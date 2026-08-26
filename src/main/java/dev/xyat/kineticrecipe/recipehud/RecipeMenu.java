package dev.xyat.kineticrecipe.recipehud;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class RecipeMenu extends AbstractContainerMenu {
    public RecipeMenu(int id, Inventory inv) {
        super(RecipeRegistry.HUB_MENU.get(), id);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player p, int i) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        return true;
    }

    @Override
    public void removed(@NotNull Player p) {
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
}