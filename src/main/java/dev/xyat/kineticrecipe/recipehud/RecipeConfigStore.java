package dev.xyat.kineticrecipe.recipehud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.xyat.kineticrecipe.recipehud.removal.RemovalEntry;
import dev.xyat.kineticrecipe.recipehud.removal.RemovalMode;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RecipeConfigStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("kineticcore/recipes.json");

    private RecipeConfigStore() {
    }

    public record Snapshot(List<RecipeRecord> recipes, List<RemovalEntry> removals, List<JsonObject> unresolvedRecipes) {
    }

    public static synchronized Snapshot load() {
        ensureFile();
        try {
            return loadRequired();
        } catch (IOException e) {
            LOGGER.error("Failed to read recipe config {}", CONFIG_FILE, e);
            return new Snapshot(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    private static Snapshot loadRequired() throws IOException {
        ensureFile();
        try {
            JsonElement rootElement = GSON.fromJson(Files.readString(CONFIG_FILE, StandardCharsets.UTF_8), JsonElement.class);
            if (rootElement == null || !rootElement.isJsonObject()) {
                throw new IOException("Recipe config root must be a JSON object");
            }
            JsonObject root = rootElement.getAsJsonObject();
            List<JsonObject> unresolvedRecipes = new ArrayList<>();
            List<RecipeRecord> recipes = readRecipes(root, unresolvedRecipes);
            return new Snapshot(recipes, readRemovals(root), unresolvedRecipes);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse recipe config", e);
        }
    }

    public static synchronized void updateRecipes(List<RecipeRecord> recipes) throws IOException {
        Snapshot current = loadRequired();
        save(recipes, current.removals(), current.unresolvedRecipes());
    }

    public static synchronized void updateRemovals(List<RemovalEntry> removals) throws IOException {
        Snapshot current = loadRequired();
        save(current.recipes(), removals, current.unresolvedRecipes());
    }

    public static synchronized void save(List<RecipeRecord> recipes, List<RemovalEntry> removals) throws IOException {
        save(recipes, removals, List.of());
    }

    private static void save(
            List<RecipeRecord> recipes,
            List<RemovalEntry> removals,
            List<JsonObject> unresolvedRecipes
    ) throws IOException {
        Files.createDirectories(CONFIG_FILE.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);

        JsonArray recipesJson = new JsonArray();
        Set<String> writtenKeys = new HashSet<>();
        for (RecipeRecord record : recipes) {
            JsonObject recipeObject = writeRecipe(record);
            recipesJson.add(recipeObject);
            String key = recipeConfigKey(recipeObject);
            if (!key.isBlank()) {
                writtenKeys.add(key);
            }
        }
        for (JsonObject unresolved : unresolvedRecipes) {
            if (unresolved == null) {
                continue;
            }
            String key = recipeConfigKey(unresolved);
            if (!key.isBlank() && writtenKeys.contains(key)) {
                continue;
            }
            recipesJson.add(unresolved.deepCopy());
        }
        root.add("recipes", recipesJson);

        JsonArray removalsJson = new JsonArray();
        for (RemovalEntry entry : removals) {
            JsonObject object = new JsonObject();
            object.addProperty("mode", entry.mode().name());
            object.addProperty("value", entry.value());
            if (!entry.comment().isEmpty()) {
                object.addProperty("comment", entry.comment());
            }
            removalsJson.add(object);
        }
        root.add("removals", removalsJson);

        Path tempPath = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
        Files.writeString(tempPath, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(tempPath, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tempPath, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureFile() {
        if (Files.exists(CONFIG_FILE)) {
            return;
        }
        try {
            save(List.of(), List.of());
        } catch (IOException e) {
            LOGGER.error("Failed to create recipe config {}", CONFIG_FILE, e);
        }
    }

    private static List<RecipeRecord> readRecipes(JsonObject root, List<JsonObject> unresolvedRecipes) {
        List<RecipeRecord> recipes = new ArrayList<>();
        JsonArray array = root.has("recipes") && root.get("recipes").isJsonArray()
                ? root.getAsJsonArray("recipes")
                : new JsonArray();

        int configIndex = 0;
        for (JsonElement element : array) {
            int currentIndex = configIndex++;
            if (!element.isJsonObject()) {
                LOGGER.warn("Skipping recipe config entry #{} because it is not a JSON object", currentIndex);
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String label = recipeLabel(object, currentIndex);
            try {
                RecipeRecord record = new RecipeRecord();
                if (object.has("uuid")) {
                    record.uuid = object.get("uuid").getAsString();
                }
                record.editorType = getString(object, "editor_type", "CRAFTING");
                RecipeRegistry.EditorType editorType = RecipeRegistry.EditorType.valueOf(record.editorType);
                record.isShapeless = getBoolean(object, "shapeless", false);
                record.outputUseNbt = getBoolean(object, "output_use_nbt", false);
                record.comment = getString(object, "comment", "");

                if (!object.has("output") || !object.get("output").isJsonObject()) {
                    throw new IllegalArgumentException("missing output object");
                }
                record.output = readStack(object.getAsJsonObject("output"), false, false);
                if (record.output.isEmpty()) {
                    throw new IllegalArgumentException("output is empty");
                }

                JsonArray inputs = object.has("inputs") && object.get("inputs").isJsonArray()
                        ? object.getAsJsonArray("inputs")
                        : new JsonArray();
                for (JsonElement inputElement : inputs) {
                    if (!inputElement.isJsonObject()) {
                        throw new IllegalArgumentException("input entry is not a JSON object");
                    }
                    JsonObject inputObject = inputElement.getAsJsonObject();
                    record.inputs.add(readStack(inputObject, true, true));
                    record.inputModes.add(inputObject.has("mode") ? inputObject.get("mode").getAsInt() : 0);
                }

                int required = switch (editorType) {
                    case CRAFTING -> 9;
                    case SMITHING -> 3;
                    default -> 1;
                };
                while (record.inputs.size() < required) {
                    record.inputs.add(ItemStack.EMPTY);
                    record.inputModes.add(0);
                }
                while (record.inputModes.size() < record.inputs.size()) {
                    record.inputModes.add(0);
                }

                validateRequiredInputs(record, editorType);
                recipes.add(record);
            } catch (Exception e) {
                unresolvedRecipes.add(object.deepCopy());
                LOGGER.warn("Skipping unusable recipe config entry {}: {}", label, safeMessage(e));
            }
        }
        return recipes;
    }

    private static List<RemovalEntry> readRemovals(JsonObject root) {
        List<RemovalEntry> removals = new ArrayList<>();
        JsonArray array = root.has("removals") && root.get("removals").isJsonArray()
                ? root.getAsJsonArray("removals")
                : new JsonArray();

        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            try {
                JsonObject object = element.getAsJsonObject();
                RemovalMode mode = RemovalMode.valueOf(getString(object, "mode", "OUTPUT"));
                String value = getString(object, "value", "");
                String comment = getString(object, "comment", "");
                if (!value.isBlank()) {
                    removals.add(new RemovalEntry(mode, value, comment));
                }
            } catch (Exception e) {
                LOGGER.error("Skipping invalid recipe removal config entry", e);
            }
        }
        return removals;
    }

    private static JsonObject writeRecipe(RecipeRecord record) {
        JsonObject object = new JsonObject();
        object.addProperty("uuid", record.uuid);
        object.addProperty("id", memoryRecipeId(record).toString());
        object.addProperty("editor_type", record.editorType);
        object.addProperty("shapeless", record.isShapeless);
        object.addProperty("output_use_nbt", record.outputUseNbt);
        if (record.comment != null && !record.comment.isEmpty()) {
            object.addProperty("comment", record.comment);
        }
        object.add("output", writeStack(record.output, null));

        JsonArray inputs = new JsonArray();
        for (int i = 0; i < record.inputs.size(); i++) {
            int mode = i < record.inputModes.size() ? record.inputModes.get(i) : 0;
            inputs.add(writeStack(record.inputs.get(i), mode));
        }
        object.add("inputs", inputs);
        return object;
    }

    private static JsonObject writeStack(ItemStack stack, Integer mode) {
        JsonObject object = new JsonObject();
        if (mode != null) {
            object.addProperty("mode", mode);
        }
        if (stack == null || stack.isEmpty()) {
            object.addProperty("empty", true);
            return object;
        }

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("kt_tag")) {
            String tagId = tag.getString("kt_tag");
            object.addProperty("tag", tagId.startsWith("#") ? tagId.substring(1) : tagId);
            return object;
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            object.addProperty("empty", true);
            return object;
        }

        object.addProperty("item", id.toString());
        object.addProperty("count", stack.getCount());
        if (tag != null && !tag.isEmpty()) {
            object.addProperty("nbt", tag.toString());
        }
        return object;
    }

    private static ItemStack readStack(JsonObject object, boolean allowEmpty, boolean allowTag) {
        if (getBoolean(object, "empty", false)) {
            if (!allowEmpty) {
                throw new IllegalArgumentException("required stack is explicitly empty");
            }
            return ItemStack.EMPTY;
        }

        if (object.has("tag")) {
            if (!allowTag) {
                throw new IllegalArgumentException("output cannot be an item tag");
            }
            String rawTag = object.get("tag").getAsString();
            if (rawTag.startsWith("#")) {
                rawTag = rawTag.substring(1);
            }
            ResourceLocation tagId = parseResourceLocation(rawTag, "item tag");
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
            Ingredient tagIngredient = Ingredient.of(tagKey);
            if (tagIngredient.getItems().length == 0) {
                throw new IllegalArgumentException("missing or empty item tag #" + tagId);
            }
            ItemStack tagStack = new ItemStack(Items.PAPER);
            tagStack.getOrCreateTag().putString("kt_tag", "#" + tagId);
            tagStack.setHoverName(Component.literal("#" + tagId));
            return tagStack;
        }

        if (!object.has("item")) {
            throw new IllegalArgumentException("stack has neither item nor tag");
        }

        ResourceLocation id = parseResourceLocation(object.get("item").getAsString(), "item");
        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            throw new IllegalArgumentException("missing registered item " + id);
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            throw new IllegalArgumentException("missing registered item " + id);
        }

        ItemStack stack = new ItemStack(item);
        stack.setCount(Math.max(1, object.has("count") ? object.get("count").getAsInt() : 1));
        if (object.has("nbt")) {
            try {
                stack.setTag(TagParser.parseTag(object.get("nbt").getAsString()));
            } catch (Exception e) {
                throw new IllegalArgumentException("invalid NBT for item " + id, e);
            }
        }
        return stack;
    }

    private static void validateRequiredInputs(RecipeRecord record, RecipeRegistry.EditorType editorType) {
        switch (editorType) {
            case CRAFTING -> {
                boolean hasInput = false;
                for (int i = 0; i < Math.min(9, record.inputs.size()); i++) {
                    ItemStack stack = record.inputs.get(i);
                    if (stack != null && !stack.isEmpty()) {
                        hasInput = true;
                        break;
                    }
                }
                if (!hasInput) {
                    throw new IllegalArgumentException("crafting recipe has no usable ingredients");
                }
            }
            case SMITHING -> {
                for (int i = 0; i < 3; i++) {
                    if (i >= record.inputs.size() || record.inputs.get(i) == null || record.inputs.get(i).isEmpty()) {
                        throw new IllegalArgumentException("smithing recipe is missing ingredient slot " + i);
                    }
                }
            }
            default -> {
                if (record.inputs.isEmpty() || record.inputs.get(0) == null || record.inputs.get(0).isEmpty()) {
                    throw new IllegalArgumentException("recipe is missing its input ingredient");
                }
            }
        }
    }

    private static ResourceLocation parseResourceLocation(String raw, String kind) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(kind + " id is blank");
        }
        try {
            return new ResourceLocation(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid " + kind + " id " + raw, e);
        }
    }


    private static String recipeConfigKey(JsonObject object) {
        if (object.has("uuid")) {
            String uuid = object.get("uuid").getAsString();
            if (!uuid.isBlank()) {
                return "uuid:" + uuid;
            }
        }
        if (object.has("id")) {
            String id = object.get("id").getAsString();
            if (!id.isBlank()) {
                return "id:" + id;
            }
        }
        return "";
    }

    private static String recipeLabel(JsonObject object, int index) {
        if (object.has("id")) {
            return object.get("id").getAsString();
        }
        if (object.has("uuid")) {
            return object.get("uuid").getAsString();
        }
        return "#" + index;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    public static ResourceLocation memoryRecipeId(RecipeRecord record) {
        String value = record.uuid == null ? "" : record.uuid.replace("-", "").toLowerCase();
        if (value.isBlank()) {
            value = Integer.toHexString(System.identityHashCode(record));
        }
        return new ResourceLocation("kineticrecipe", "memory/" + value);
    }

    private static String getString(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }
}
