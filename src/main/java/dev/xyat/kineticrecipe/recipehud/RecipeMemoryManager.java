package dev.xyat.kineticrecipe.recipehud;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticrecipe.KineticRecipe;
import dev.xyat.kineticrecipe.recipehud.removal.RecipeRemovalManager;
import dev.xyat.kineticrecipe.recipehud.removal.RemovalEntry;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraftforge.common.crafting.PartialNBTIngredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = KineticRecipe.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeMemoryManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Recipe<?>> BASELINE = new ArrayList<>();
    private static boolean baselineReady;

    private RecipeMemoryManager() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        captureBaseline(event.getServer());
        RecipeDatabase.reloadDatabase();
        RecipeRemovalManager.reloadData();
        apply(event.getServer(), false);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BASELINE.clear();
        baselineReady = false;
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            return;
        }
        MinecraftServer server = event.getPlayerList().getServer();
        captureBaseline(server);
        RecipeDatabase.reloadDatabase();
        RecipeRemovalManager.reloadData();
        apply(server, false);
        RecipeSaveManager.markApplied();
    }

    public static synchronized void applyAndSync(MinecraftServer server) {
        if (!baselineReady) {
            captureBaseline(server);
        }
        RecipeDatabase.reloadDatabase();
        apply(server, true);
        RecipeSaveManager.markApplied();
    }

    private static synchronized void captureBaseline(MinecraftServer server) {
        BASELINE.clear();
        BASELINE.addAll(server.getRecipeManager().getRecipes());
        baselineReady = true;
        LOGGER.info("Captured {} baseline recipes", BASELINE.size());
    }

    private static void apply(MinecraftServer server, boolean syncPlayers) {
        RecipeConfigStore.Snapshot snapshot = RecipeConfigStore.load();
        Map<ResourceLocation, Recipe<?>> recipes = new LinkedHashMap<>();
        int skippedBaseline = 0;
        for (Recipe<?> recipe : BASELINE) {
            try {
                if (recipe == null || recipe.getId() == null) {
                    skippedBaseline++;
                    continue;
                }
                recipes.put(recipe.getId(), recipe);
            } catch (Exception e) {
                skippedBaseline++;
                LOGGER.warn("Skipping unreadable baseline recipe: {}", safeMessage(e));
            }
        }

        RegistryAccess registryAccess = server.registryAccess();
        recipes.values().removeIf(recipe -> matchesRemoval(recipe, snapshot.removals(), registryAccess));

        int added = 0;
        int skippedConfigured = 0;
        for (RecipeRecord record : snapshot.recipes()) {
            try {
                Recipe<?> recipe = buildRecipe(record);
                if (recipe != null) {
                    recipes.put(recipe.getId(), recipe);
                    added++;
                } else {
                    skippedConfigured++;
                }
            } catch (Exception e) {
                skippedConfigured++;
                LOGGER.warn("Skipping unusable memory recipe {}: {}", recipeLabel(record), safeMessage(e));
            }
        }

        List<Recipe<?>> finalRecipes = new ArrayList<>(recipes.values());
        server.getRecipeManager().replaceRecipes(finalRecipes);

        if (syncPlayers) {
            ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(finalRecipes);
            server.getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
        }
        LOGGER.info(
                "Applied memory recipes: baseline={}, configured={}, active={}, skippedBaseline={}, skippedConfigured={}",
                BASELINE.size(),
                added,
                finalRecipes.size(),
                skippedBaseline,
                skippedConfigured
        );
    }

    private static Recipe<?> buildRecipe(RecipeRecord record) {
        RecipeRegistry.EditorType type = RecipeRegistry.EditorType.valueOf(record.editorType);
        ResourceLocation id = RecipeConfigStore.memoryRecipeId(record);
        ItemStack output = record.output == null ? ItemStack.EMPTY : record.output.copy();
        if (output.isEmpty()) {
            throw new IllegalArgumentException("recipe output is empty or missing");
        }
        validateRegisteredStack(output, "output");
        if (!record.outputUseNbt || type == RecipeRegistry.EditorType.SMITHING) {
            output.setTag(null);
        }

        return switch (type) {
            case CRAFTING -> record.isShapeless
                    ? buildShapeless(id, record, output)
                    : buildShaped(id, record, output);
            case SMITHING -> new SmithingTransformRecipe(
                    id,
                    ingredient(record, 0),
                    ingredient(record, 1),
                    ingredient(record, 2),
                    output
            );
            case FURNACE -> new SmeltingRecipe(id, "", CookingBookCategory.MISC, ingredient(record, 0), output, 0.0F, 200);
            case BLAST -> new BlastingRecipe(id, "", CookingBookCategory.MISC, ingredient(record, 0), output, 0.0F, 100);
            case SMOKER -> new SmokingRecipe(id, "", CookingBookCategory.MISC, ingredient(record, 0), output, 0.0F, 100);
            case STONECUTTER -> new StonecutterRecipe(id, "", ingredient(record, 0), output);
        };
    }

    private static ShapelessRecipe buildShapeless(ResourceLocation id, RecipeRecord record, ItemStack output) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (int i = 0; i < Math.min(9, record.inputs.size()); i++) {
            ItemStack stack = record.inputs.get(i);
            if (stack != null && !stack.isEmpty()) {
                ingredients.add(ingredient(record, i));
            }
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Shapeless recipe has no ingredients");
        }
        return new ShapelessRecipe(id, "", CraftingBookCategory.MISC, output, ingredients);
    }

    private static ShapedRecipe buildShaped(ResourceLocation id, RecipeRecord record, ItemStack output) {
        int minRow = 3;
        int maxRow = -1;
        int minColumn = 3;
        int maxColumn = -1;
        for (int i = 0; i < Math.min(9, record.inputs.size()); i++) {
            ItemStack stack = record.inputs.get(i);
            if (stack != null && !stack.isEmpty()) {
                int row = i / 3;
                int column = i % 3;
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
            }
        }
        if (maxRow < minRow || maxColumn < minColumn) {
            throw new IllegalArgumentException("Shaped recipe has no ingredients");
        }

        int width = maxColumn - minColumn + 1;
        int height = maxRow - minRow + 1;
        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int row = minRow; row <= maxRow; row++) {
            for (int column = minColumn; column <= maxColumn; column++) {
                int sourceIndex = row * 3 + column;
                ItemStack stack = record.inputs.get(sourceIndex);
                if (stack != null && !stack.isEmpty()) {
                    int targetIndex = (row - minRow) * width + (column - minColumn);
                    ingredients.set(targetIndex, ingredient(record, sourceIndex));
                }
            }
        }
        return new ShapedRecipe(id, "", CraftingBookCategory.MISC, width, height, ingredients, output);
    }

    private static Ingredient ingredient(RecipeRecord record, int index) {
        if (index < 0 || index >= record.inputs.size()) {
            return Ingredient.EMPTY;
        }
        ItemStack stack = record.inputs.get(index);
        if (stack == null || stack.isEmpty()) {
            return Ingredient.EMPTY;
        }

        if (stack.getTag() != null && stack.getTag().contains("kt_tag")) {
            String raw = stack.getTag().getString("kt_tag");
            if (raw.startsWith("#")) {
                raw = raw.substring(1);
            }
            TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(raw));
            Ingredient tagIngredient = Ingredient.of(tag);
            if (tagIngredient.getItems().length == 0) {
                throw new IllegalArgumentException("missing or empty item tag #" + raw);
            }
            return tagIngredient;
        }

        validateRegisteredStack(stack, "ingredient " + index);

        int mode = index < record.inputModes.size() ? record.inputModes.get(index) : 0;
        if (mode == 1) {
            return stack.getTag() == null ? Ingredient.of(stack.getItem()) : PartialNBTIngredient.of(stack.getItem(), stack.getTag());
        }
        if (mode == 2) {
            return StrictNBTIngredient.of(stack);
        }
        return Ingredient.of(stack.getItem());
    }

    private static boolean matchesRemoval(Recipe<?> recipe, List<RemovalEntry> removals, RegistryAccess registryAccess) {
        for (RemovalEntry entry : removals) {
            try {
                switch (entry.mode()) {
                    case MOD -> {
                        if (recipe.getId().getNamespace().equals(entry.value())) {
                            return true;
                        }
                    }
                    case RECIPE_ID -> {
                        if (recipe.getId().toString().equals(entry.value())) {
                            return true;
                        }
                    }
                    case OUTPUT -> {
                        ItemStack result = recipe.getResultItem(registryAccess);
                        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(result.getItem());
                        if (itemId != null && itemId.toString().equals(entry.value())) {
                            return true;
                        }
                    }
                    case TAG -> {
                        ItemStack result = recipe.getResultItem(registryAccess);
                        String raw = entry.value().startsWith("#") ? entry.value().substring(1) : entry.value();
                        if (result.is(TagKey.create(Registries.ITEM, new ResourceLocation(raw)))) {
                            return true;
                        }
                    }
                    case TYPE -> {
                        ResourceLocation typeId = ForgeRegistries.RECIPE_TYPES.getKey(recipe.getType());
                        if (typeId != null && typeId.toString().equals(entry.value())) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to evaluate recipe removal {} against {}", entry, recipe.getId(), e);
            }
        }
        return false;
    }
    private static void validateRegisteredStack(ItemStack stack, String role) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException(role + " is empty");
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId)) {
            throw new IllegalArgumentException(role + " references an unregistered item");
        }
        Item registered = ForgeRegistries.ITEMS.getValue(itemId);
        if (registered == null || registered != stack.getItem()) {
            throw new IllegalArgumentException(role + " references missing item " + itemId);
        }
    }

    private static String recipeLabel(RecipeRecord record) {
        if (record == null) {
            return "<null>";
        }
        if (record.uuid != null && !record.uuid.isBlank()) {
            return record.uuid;
        }
        if (record.output != null && !record.output.isEmpty()) {
            ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(record.output.getItem());
            if (outputId != null) {
                return outputId.toString();
            }
        }
        return "<unknown>";
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

}
