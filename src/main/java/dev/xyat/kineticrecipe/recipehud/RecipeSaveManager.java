package dev.xyat.kineticrecipe.recipehud;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;

public final class RecipeSaveManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean pendingRecipeReload;

    private RecipeSaveManager() {
    }

    public static synchronized void saveOnly(ServerPlayer player, RecipeNetwork.RecipeChangePacket packet) {
        try {
            RecipeDatabase.loadDatabase();
            ArrayList<RecipeRecord> candidateRecords = copyRecords(RecipeDatabase.snapshot());
            RecipeRecord record = null;
            if (packet.uuid() != null && !packet.uuid().isEmpty()) {
                record = candidateRecords.stream()
                        .filter(value -> value.uuid.equals(packet.uuid()))
                        .findFirst()
                        .orElse(null);
            }
            if (record == null) {
                record = new RecipeRecord();
                candidateRecords.add(record);
            }

            record.editorType = packet.editorType();
            record.isShapeless = packet.isShapeless();
            record.outputUseNbt = packet.outputUseNbt();
            record.output = packet.output().copy();
            record.inputs = new ArrayList<>(packet.inputs());
            record.inputModes = new ArrayList<>(packet.inputNbtModes());

            RecipeConfigStore.updateRecipes(candidateRecords);
            RecipeDatabase.records.clear();
            RecipeDatabase.records.addAll(candidateRecords);
            pendingRecipeReload = true;
            RecipeNetwork.sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.msg.saved_pending"));
        } catch (Exception e) {
            LOGGER.error("Failed to save memory recipe", e);
            RecipeNetwork.sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.save_failed_plain"));
        }
    }

    public static synchronized void deleteOnly(ServerPlayer player, String uuid) {
        try {
            RecipeDatabase.loadDatabase();
            ArrayList<RecipeRecord> candidateRecords = copyRecords(RecipeDatabase.snapshot());
            candidateRecords.removeIf(record -> record.uuid.equals(uuid));
            RecipeConfigStore.updateRecipes(candidateRecords);
            RecipeDatabase.records.clear();
            RecipeDatabase.records.addAll(candidateRecords);
            pendingRecipeReload = true;
            RecipeNetwork.syncRecipeRecords(player);
            RecipeNetwork.sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.msg.deleted_pending"));
        } catch (Exception e) {
            LOGGER.error("Failed to delete memory recipe {}", uuid, e);
            RecipeNetwork.sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.save_failed_plain"));
        }
    }


    private static ArrayList<RecipeRecord> copyRecords(java.util.List<RecipeRecord> source) {
        ArrayList<RecipeRecord> copies = new ArrayList<>();
        for (RecipeRecord record : source) {
            if (record != null) {
                copies.add(RecipeRecord.loadFromNBT(record.saveToNBT()));
            }
        }
        return copies;
    }

    public static synchronized void applyPending(ServerPlayer player) {
        if (player == null || player.getServer() == null || !pendingRecipeReload) {
            return;
        }
        try {
            RecipeMemoryManager.applyAndSync(player.getServer());
            pendingRecipeReload = false;
        } catch (Exception e) {
            LOGGER.error("Failed to apply pending memory recipes", e);
        }
    }

    public static synchronized void markApplied() {
        pendingRecipeReload = false;
    }
}
