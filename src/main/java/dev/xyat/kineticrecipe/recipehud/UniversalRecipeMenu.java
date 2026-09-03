package dev.xyat.kineticrecipe.recipehud;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

public class UniversalRecipeMenu extends AbstractContainerMenu {
    public final Container inputContainer = new SimpleContainer(9);
    public final Container outputContainer = new SimpleContainer(1);
    public final RecipeRegistry.EditorType type;

    private int editorSlotCount = 0;

    public String editUuid = "";
    public int editConfigIndex = -1;
    public CompoundTag clientRecordData = null;

    public UniversalRecipeMenu(int id, Inventory playerInv, RecipeRegistry.EditorType type, RecipeRecord record) {
        super(RecipeRegistry.EDITOR_MENU.get(), id);
        this.type = type;
        addSlots(playerInv);

        if (record != null) {
            this.editUuid = record.uuid;
            this.editConfigIndex = record.configIndex;
            this.outputContainer.setItem(0, record.output.copy());
            for (int i = 0; i < record.inputs.size() && i < editorSlotCount - (1); i++) {
                this.inputContainer.setItem(i, record.inputs.get(i).copy());
            }
        }
    }

    public UniversalRecipeMenu(int id, Inventory playerInv, FriendlyByteBuf data) {
        super(RecipeRegistry.EDITOR_MENU.get(), id);
        this.type = RecipeRegistry.EditorType.valueOf(data.readUtf());
        addSlots(playerInv);

        if (data.readBoolean()) {
            this.clientRecordData = data.readNbt();
            this.editUuid = data.readUtf();
            this.editConfigIndex = data.readInt();
        }
    }

    private void addSlots(Inventory playerInv) {
        if (type == RecipeRegistry.EditorType.CRAFTING) {
            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 3; ++j) {
                    this.addSlot(new GhostSlot(inputContainer, j + i * 3, 30 + j * 18, 17 + i * 18));
                }
            }
            this.addSlot(new GhostSlot(outputContainer, 0, 124, 35));
            editorSlotCount = 10;
        } else if (type == RecipeRegistry.EditorType.SMITHING) {
            this.addSlot(new GhostSlot(inputContainer, 0, 8, 48));
            this.addSlot(new GhostSlot(inputContainer, 1, 26, 48));
            this.addSlot(new GhostSlot(inputContainer, 2, 44, 48));
            this.addSlot(new GhostSlot(outputContainer, 0, 98, 48));
            editorSlotCount = 4;
        } else if (type == RecipeRegistry.EditorType.STONECUTTER) {
            this.addSlot(new GhostSlot(inputContainer, 0, 20, 33));
            this.addSlot(new GhostSlot(outputContainer, 0, 143, 33));
            editorSlotCount = 2;
        } else {
            this.addSlot(new GhostSlot(inputContainer, 0, 56, 17));
            this.addSlot(new GhostSlot(outputContainer, 0, 116, 35));
            editorSlotCount = 2;
        }

        for (int k = 0; k < 3; ++k) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInv, j + k * 9 + 9, 8 + j * 18, 84 + k * 18));
            }
        }
        for (int j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerInv, j, 8 + j * 18, 142));
        }
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        if (slotId >= 0 && slotId < editorSlotCount) {
            Slot slot = this.slots.get(slotId);
            ItemStack carried = this.getCarried();
            if (!carried.isEmpty()) {
                ItemStack copy = carried.copy();
                copy.setCount(1);
                slot.set(copy);
            } else {
                slot.set(ItemStack.EMPTY);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    @Nonnull
    public ItemStack quickMoveStack(@Nonnull Player p, int index) {
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            if (index < editorSlotCount) {
                slot.set(ItemStack.EMPTY);
            } else {
                ItemStack stackToCopy = slot.getItem().copy();
                stackToCopy.setCount(1);
                int inputSize = (type == RecipeRegistry.EditorType.CRAFTING) ? 9 :
                        ((type == RecipeRegistry.EditorType.SMITHING) ? 3 : 1);
                for (int i = 0; i < inputSize; i++) {
                    Slot targetSlot = this.slots.get(i);
                    if (!targetSlot.hasItem()) { targetSlot.set(stackToCopy); break; }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override public boolean stillValid(@Nonnull Player p) { return true; }

    @Override public void removed(@Nonnull Player p) {
        super.removed(p);
        if (p instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            var server = serverPlayer.getServer();
            if (server != null) {
                server.tell(new net.minecraft.server.TickTask(
                        server.getTickCount() + 1,
                        () -> {
                            boolean anyOpen = false;
                            for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                                if (sp.containerMenu instanceof RecipeMenu || sp.containerMenu instanceof UniversalRecipeMenu) {
                                    anyOpen = true;
                                    break;
                                }
                            }
                            if (!anyOpen) {
                                RecipeDatabase.clear();
                            }
                        }
                ));
            }
        }
    }

    private static class GhostSlot extends Slot {
        public GhostSlot(Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public boolean mayPickup(@NotNull Player p) { return false; }
        @Override public boolean mayPlace(@NotNull ItemStack s) { return false; }
    }
}