package dev.xyat.kineticrecipe.recipehud;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;

public final class RecipeSaveManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean pendingRecipeReload;

    private RecipeSaveManager() {
    }

    public static synchronized void saveOnly(ServerPlayer player, RecipeNetwork.RecipeChangePacket packet) {
        try {
            RecipeRecord record = new RecipeRecord();
            if (packet.uuid() != null && !packet.uuid().isBlank()) {
                record.uuid = packet.uuid();
            }
            record.editorType = packet.editorType();
            record.isShapeless = packet.isShapeless();
            record.outputUseNbt = packet.outputUseNbt();
            record.output = packet.output().copy();
            record.inputs = copyStacks(packet.inputs());
            record.inputModes = new ArrayList<>(packet.inputNbtModes());
            record.configIndex = packet.configIndex();
            record.invalidConfig = false;
            record.invalidReason = "";

            if (packet.configIndex() >= 0) {
                RecipeConfigStore.replaceRecipeAtConfigIndex(packet.configIndex(), record);
            } else {
                RecipeDatabase.loadDatabase();
                ArrayList<RecipeRecord> candidateRecords = copyRecords(RecipeDatabase.snapshot());
                candidateRecords.add(record);
                RecipeConfigStore.updateRecipes(candidateRecords);
            }

            RecipeDatabase.reloadDatabase();
            pendingRecipeReload = true;
            RecipeNetwork.syncRecipeRecords(player);
            RecipeNetwork.sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.msg.saved_pending"));
        } catch (Exception e) {
            LOGGER.error("Failed to save memory recipe", e);
            RecipeNetwork.sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.save_failed_plain"));
        }
    }

    public static synchronized void deleteOnly(ServerPlayer player, String uuid, int configIndex) {
        try {
            if (configIndex >= 0) {
                RecipeConfigStore.deleteRecipeAtConfigIndex(configIndex);
            } else {
                RecipeDatabase.loadDatabase();
                ArrayList<RecipeRecord> candidateRecords = copyRecords(RecipeDatabase.snapshot());
                candidateRecords.removeIf(record -> record.uuid != null && record.uuid.equals(uuid));
                RecipeConfigStore.updateRecipes(candidateRecords);
            }

            RecipeDatabase.reloadDatabase();
            pendingRecipeReload = true;
            RecipeNetwork.syncRecipeRecords(player);
            RecipeNetwork.sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.msg.deleted_pending"));
        } catch (Exception e) {
            LOGGER.error("Failed to delete memory recipe {} at config index {}", uuid, configIndex, e);
            RecipeNetwork.sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.save_failed_plain"));
        }
    }

    private static ArrayList<ItemStack> copyStacks(java.util.List<ItemStack> source) {
        ArrayList<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : source) {
            copies.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return copies;
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
