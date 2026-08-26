package dev.xyat.kineticrecipe.recipehud.removal;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticrecipe.recipehud.RecipeConfigStore;
import dev.xyat.kineticrecipe.recipehud.RecipeMemoryManager;
import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class RecipeRemovalManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<RemovalEntry> REMOVAL_LIST = new ArrayList<>();
    private static boolean loaded;

    private RecipeRemovalManager() {
    }

    public static synchronized void loadData() {
        if (loaded) {
            return;
        }
        reloadData();
    }

    public static synchronized void reloadData() {
        REMOVAL_LIST.clear();
        REMOVAL_LIST.addAll(RecipeConfigStore.load().removals());
        loaded = true;
    }

    public static synchronized void addEntry(RemovalEntry entry) {
        loadData();
        if (!REMOVAL_LIST.contains(entry)) {
            REMOVAL_LIST.add(entry);
        }
    }

    public static synchronized void removeEntry(RemovalEntry entry) {
        loadData();
        REMOVAL_LIST.remove(entry);
    }

    public static synchronized List<RemovalEntry> snapshot() {
        loadData();
        return new ArrayList<>(REMOVAL_LIST);
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<RemovalEntry> entries = snapshot();
        RecipeNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RecipeNetwork.SyncPacket(entries)
        );
    }

    public static void saveAndApply(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        try {
            RecipeConfigStore.updateRemovals(snapshot());
            RecipeMemoryManager.applyAndSync(player.getServer());
            RecipeNetwork.sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.msg.removals_saved_applied"));
        } catch (Exception e) {
            LOGGER.error("Failed to save recipe removals", e);
            RecipeNetwork.sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.save_failed_plain"));
        }
    }
}
