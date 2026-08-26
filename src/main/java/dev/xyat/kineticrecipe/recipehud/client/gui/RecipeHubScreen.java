package dev.xyat.kineticrecipe.recipehud.client.gui;

import dev.xyat.kineticcore.api.client.GuiToastUtil;
import dev.xyat.kineticrecipe.recipehud.RecipeMenu;
import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork;
import dev.xyat.kineticrecipe.recipehud.RecipeRegistry;
import dev.xyat.kineticcore.api.client.ItemCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import dev.xyat.kineticcore.api.client.gui.ResponsiveContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class RecipeHubScreen extends ResponsiveContainerScreen<RecipeMenu> {

    public RecipeHubScreen(RecipeMenu recipeMenu, Inventory inv, Component title) {
        super(recipeMenu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 130;
        this.inventoryLabelY = 1000;
        this.titleLabelY = 10;

        configureResponsiveContainer(
        );
    }

    public void showToast(Component msg) {
        GuiToastUtil.showToast(msg);
    }

    @Override
    protected void init() {
        super.init();
        RecipePreviewState.returnToPreview = false;

        int btnSize = 28;
        int padding = 8;
        int columns = 3;

        int totalWidth = (columns * btnSize) + ((columns - 1) * padding);
        int startX = this.leftPos + (this.imageWidth - totalWidth) / 2;
        int startY = this.topPos + 30;

        int i = 0;
        for (RecipeRegistry.EditorType type : RecipeRegistry.EditorType.values()) {
            int col = i % columns;
            int row = i / columns;
            int x = startX + (col * (btnSize + padding));
            int y = startY + (row * (btnSize + padding));

            this.addRenderableWidget(new IconButton(x, y, btnSize, btnSize, type, button -> {
                if (Minecraft.getInstance().player != null) {
                    RecipeNetwork.CHANNEL.sendToServer(
                            new RecipeNetwork.RequestEditPacket("", type.name())
                    );
                }
            }));
            i++;
        }

        int bottomBtnW = 82;
        int bottomSpacing = 4;
        int totalBottomWidth = bottomBtnW * 2 + bottomSpacing;
        int bottomStartX = this.leftPos + (this.imageWidth - totalBottomWidth) / 2;
        int bottomY = this.topPos + this.imageHeight - 30;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.kineticrecipe.recipehud.btn.hub"), b -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                ItemCache.prepareCache(() -> {
                    this.showToast(Component.translatable("msg.kineticrecipe.recipehud.requesting_data"));
                    RecipeNetwork.requestOpen();
                });
            }
        }).bounds(bottomStartX, bottomY, bottomBtnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.kineticrecipe.recipehud.manage.title"), b -> {
            this.showToast(Component.translatable("msg.kineticrecipe.recipehud.requesting_recipes"));
            RecipeNetwork.requestRecipeRecords();
        }).bounds(bottomStartX + bottomBtnW + bottomSpacing, bottomY, bottomBtnW, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        RecipeEditSessionState.applyPendingAndClear();
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xDD000000);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFFFFFFFF);
    }

    private static class IconButton extends Button {
        private final RecipeRegistry.EditorType type;
        public IconButton(int x, int y, int w, int h, RecipeRegistry.EditorType type, OnPress onPress) {
            super(x, y, w, h, Component.empty(), onPress, DEFAULT_NARRATION);
            this.type = type;
            this.setTooltip(Tooltip.create(type.getTitle()));
        }
        @Override
        public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.renderFakeItem(new ItemStack(type.getIcon()), this.getX() + 6, this.getY() + 6);
        }
    }
}
