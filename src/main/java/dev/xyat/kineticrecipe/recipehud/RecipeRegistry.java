package dev.xyat.kineticrecipe.recipehud;

import dev.xyat.kineticrecipe.KineticRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RecipeRegistry {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, KineticRecipe.MODID);

    public static final RegistryObject<MenuType<RecipeMenu>> HUB_MENU = MENUS.register("recipe_hub",
            () -> IForgeMenuType.create((windowId, inv, data) -> new RecipeMenu(windowId, inv)));

    // 【修改点】由于采用了客户端友好型构造函数，这里直接传 data 即可
    public static final RegistryObject<MenuType<UniversalRecipeMenu>> EDITOR_MENU = MENUS.register("recipehud",
            () -> IForgeMenuType.create(UniversalRecipeMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }

    public enum EditorType {
        CRAFTING("crafting", "minecraft:crafting_table", "minecraft:crafting_shaped"),
        FURNACE("furnace", "minecraft:furnace", "minecraft:smelting"),
        BLAST("blast", "minecraft:blast_furnace", "minecraft:blasting"),
        SMOKER("smoker", "minecraft:smoker", "minecraft:smoking"),
        SMITHING("smithing", "minecraft:smithing_table", "minecraft:smithing"),
        STONECUTTER("stonecutter", "minecraft:stonecutter", "minecraft:stonecutting");

        public final String id;
        public final String iconId;
        public final String recipeType;

        EditorType(String id, String iconId, String recipeType) {
            this.id = id;
            this.iconId = iconId;
            this.recipeType = recipeType;
        }

        public Item getIcon() {
            return ForgeRegistries.ITEMS.getValue(new ResourceLocation(iconId));
        }

        public Component getTitle() {
            return Component.translatable("gui.kineticrecipe.recipehud." + id);
        }
    }
}