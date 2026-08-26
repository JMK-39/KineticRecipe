package dev.xyat.kineticrecipe.recipehud.config;

import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.kineticcore.config.client.KTConfigPage;
import dev.xyat.kineticcore.config.client.KTConfigScope;
import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class RecipeConfigGui {
    public static final String PAGE_ID = "kineticrecipe:recipehud";

    private RecipeConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.kineticrecipe.recipehud.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .pageDescription(Component.translatable("cfg.kineticrecipe.recipehud.description"))
                .action(
                        "open_recipe_editor",
                        Component.translatable("cfg.kineticrecipe.recipehud.open_editor"),
                        RecipeNetwork::requestOpenHub,
                        Component.translatable("cfg.kineticrecipe.recipehud.open_editor.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "kineticrecipe");
    }
}
