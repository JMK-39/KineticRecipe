package dev.xyat.kineticrecipe.recipehud.removal;

import net.minecraft.network.chat.Component;

public enum RemovalMode {
    MOD("mod", "gui.kineticrecipe.recipehud.mode.mod"),
    RECIPE_ID("id", "gui.kineticrecipe.recipehud.mode.id"),
    OUTPUT("output", "gui.kineticrecipe.recipehud.mode.output"),
    TAG("tag", "gui.kineticrecipe.recipehud.mode.tag"),
    TYPE("type", "gui.kineticrecipe.recipehud.mode.type");

    public final String key;
    public final String translationKey;

    RemovalMode(String key, String translationKey) {
        this.key = key;
        this.translationKey = translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }
}