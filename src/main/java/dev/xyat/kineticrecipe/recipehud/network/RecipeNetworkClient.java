package dev.xyat.kineticrecipe.recipehud.network;

import dev.xyat.kineticrecipe.recipehud.RecipeDatabase;
import dev.xyat.kineticrecipe.recipehud.client.gui.RecipeHubScreen;
import dev.xyat.kineticrecipe.recipehud.client.gui.RecipePreviewScreen;
import dev.xyat.kineticrecipe.recipehud.client.gui.RecipeRemovalScreen;
import dev.xyat.kineticrecipe.recipehud.client.gui.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RecipeNetworkClient {
    private RecipeNetworkClient() {
    }

    public static void handleSync(RecipeNetwork.SyncPacket packet) {
        Screen current = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(new RecipeRemovalScreen(current, packet.entries()));
    }

    public static void handleRecipeRecords(RecipeNetwork.RecipeRecordsSyncPacket packet) {
        RecipeDatabase.setClientRecords(packet.records());
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof RecipePreviewScreen preview) {
            preview.refreshFromServer();
        } else {
            Minecraft.getInstance().setScreen(new RecipePreviewScreen(current));
        }
    }

    public static void handleToast(RecipeNetwork.ToastPacket packet) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RecipeHubScreen value) {
            value.showToast(packet.message());
        } else if (screen instanceof RecipeScreen value) {
            value.showToast(packet.message());
        } else if (screen instanceof RecipeRemovalScreen value) {
            value.showToast(packet.message());
        } else if (screen instanceof RecipePreviewScreen value) {
            value.showToast(packet.message());
        }
    }
}
