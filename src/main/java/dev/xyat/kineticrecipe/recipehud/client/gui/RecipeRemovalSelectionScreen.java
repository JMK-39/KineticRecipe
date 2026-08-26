package dev.xyat.kineticrecipe.recipehud.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.AdaptiveItemGridRenderer;
import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.gui.GridScrollController;
import dev.xyat.kineticrecipe.recipehud.removal.RemovalMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RecipeRemovalSelectionScreen extends ScaledScreen {
    private final RecipeRemovalScreen parent;
    private final RemovalMode mode;
    private final ItemStack stack;
    public final List<RecipeRemovalScreen.SelectionEntry> options;

    private static final int ITEM_HEIGHT = 20;

    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int visibleRows;

    private final GridScrollController listScroll =
            new GridScrollController();

    public RecipeRemovalSelectionScreen(RecipeRemovalScreen parent, RemovalMode mode, ItemStack stack, List<RecipeRemovalScreen.SelectionEntry> options) {
        super(Component.translatable("gui.kineticrecipe.recipehud.selection.title", mode.getDisplayName().copy().withStyle(ChatFormatting.GOLD)));
        this.parent = parent;
        this.mode = mode;
        this.stack = stack;
        this.options = options;

        configureResponsiveCanvas(
                640f,
                360f,
                6
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void initScaled() {
        listW =
                Math.max(
                        120,
                        Math.min(
                                400,
                                vWidth - 20
                        )
                );

        listX =
                (vWidth - listW) / 2;

        int buttonGap = 8;

        int buttonWidth =
                Math.max(
                        60,
                        Math.min(
                                90,
                                (
                                        vWidth
                                                - 24
                                                - buttonGap
                                ) / 2
                        )
                );

        int buttonY =
                vHeight - 24;

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.kineticrecipe.recipehud.back"
                                ),
                                button -> {
                                    if (minecraft != null) {
                                        minecraft.setScreen(parent);
                                    }
                                }
                        )
                        .bounds(
                                vWidth / 2
                                        - buttonWidth
                                        - buttonGap / 2,
                                buttonY,
                                buttonWidth,
                                20
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.kineticrecipe.recipehud.confirm"
                                ),
                                button -> {
                                    int added = 0;
                                    int removed = 0;

                                    for (RecipeRemovalScreen.SelectionEntry entry
                                            : options) {
                                        if (entry.isSelected
                                                && !entry.alreadyExists) {
                                            parent.addEntryFromSelection(
                                                    mode,
                                                    entry.value
                                            );
                                            added++;
                                        } else if (!entry.isSelected
                                                && entry.alreadyExists) {
                                            parent.removeEntryDirectly(
                                                    mode,
                                                    entry.value
                                            );
                                            removed++;
                                        }
                                    }

                                    if (added > 0
                                            || removed > 0) {
                                        parent.showToast(
                                                Component.translatable(
                                                        "gui.kineticrecipe.recipehud.toast.updated",
                                                        Component.literal(String.valueOf(added)).withStyle(ChatFormatting.GREEN),
                                                        Component.literal(String.valueOf(removed)).withStyle(ChatFormatting.RED)
                                                )
                                        );
                                    }

                                    if (minecraft != null) {
                                        minecraft.setScreen(parent);
                                    }
                                }
                        )
                        .bounds(
                                vWidth / 2
                                        + buttonGap / 2,
                                buttonY,
                                buttonWidth,
                                20
                        )
                        .build()
        );

        listY = 75;

        listH =
                Math.max(
                        ITEM_HEIGHT,
                        buttonY
                                - listY
                                - 6
                );

        visibleRows =
                Math.max(
                        1,
                        listH / ITEM_HEIGHT
                );

        listH =
                visibleRows
                        * ITEM_HEIGHT;

        listScroll.update(
                options.size(),
                visibleRows
        );
    }

    @Override
    protected void renderScaledBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.drawCenteredString(
                font,
                title,
                vWidth / 2,
                10,
                0xFFFFFF
        );

        int itemX =
                vWidth / 2 - 8;

        int itemY = 25;

        AdaptiveItemGridRenderer.drawSlot(graphics, stack, itemX - 1, itemY - 1);
        graphics.renderItem(
                stack,
                itemX,
                itemY
        );

        graphics.drawCenteredString(
                font,
                stack.getHoverName(),
                vWidth / 2,
                itemY + 20,
                0xAAAAAA
        );

        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "gui.kineticrecipe.recipehud.selection.hint"
                ),
                vWidth / 2,
                itemY + 35,
                0x888888
        );

        GuiRenderUtil.drawDarkPanel(
                graphics,
                listX,
                listY,
                listW,
                listH
        );

        listScroll.update(
                options.size(),
                visibleRows
        );

        for (int row = 0;
             row < visibleRows;
             row++) {
            int index =
                    listScroll.offset()
                            + row;

            if (index >= options.size()) {
                break;
            }

            RecipeRemovalScreen.SelectionEntry entry =
                    options.get(index);

            int y =
                    listY
                            + row * ITEM_HEIGHT;

            boolean hovered =
                    mouseX >= listX
                            && mouseX < listX + listW
                            && mouseY >= y
                            && mouseY < y + ITEM_HEIGHT;

            boolean even =
                    row % 2 == 0;

            int backgroundColor;

            if (entry.isSelected) {
                backgroundColor =
                        even
                                ? 0x66AA0000
                                : 0x66FF3333;
            } else {
                backgroundColor =
                        even
                                ? 0x44FFFFFF
                                : 0x44888888;
            }

            if (hovered) {
                backgroundColor =
                        entry.isSelected
                                ? 0x88FF0000
                                : 0x88FFFFFF;
            }

            graphics.fill(
                    listX,
                    y,
                    listX + listW,
                    y + ITEM_HEIGHT,
                    backgroundColor
            );

            MutableComponent text =
                    getMutableComponent(entry);

            float textScale = 0.75f;

            enableVirtualScissor(
                    graphics,
                    listX,
                    y,
                    listX + listW,
                    y + ITEM_HEIGHT
            );

            graphics.pose().pushPose();

            graphics.pose().scale(
                    textScale,
                    textScale,
                    1.0f
            );

            int scaledX =
                    (int) (
                            (listX + 5)
                                    / textScale
                    );

            int scaledY =
                    (int) (
                            (y + 6.5f)
                                    / textScale
                    );

            graphics.drawString(
                    font,
                    text,
                    scaledX,
                    scaledY,
                    0xFFFFFF
            );

            graphics.pose().popPose();
            graphics.disableScissor();
        }

        listScroll.render(
                graphics,
                mouseX,
                mouseY,
                listX + listW + 2,
                listY,
                6,
                listH,
                20,
                0xFF222222,
                0xFF555555,
                0xFF777777
        );
    }

    @Override
    protected void renderScaledForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int itemX =
                vWidth / 2 - 8;

        int itemY = 25;

        if (mouseX >= itemX
                && mouseX < itemX + 16
                && mouseY >= itemY
                && mouseY < itemY + 16) {
            graphics.renderTooltip(
                    font,
                    stack,
                    mouseX,
                    mouseY
            );
        }
    }

    private static @NotNull MutableComponent getMutableComponent(RecipeRemovalScreen.SelectionEntry entry) {
        MutableComponent text = Component.literal(entry.value)
                .withStyle(entry.isSelected ? ChatFormatting.RED : ChatFormatting.GOLD);
        if (entry.isSelected) {
            text.withStyle(ChatFormatting.STRIKETHROUGH);
        }

        if (entry.modName != null || entry.typeName != null) {
            String extraText = " - " + (entry.modName != null ? "[" + entry.modName + "] " : "")
                    + (entry.typeName != null ? entry.typeName : "");
            MutableComponent extra = Component.literal(extraText)
                    .withStyle(entry.isSelected ? ChatFormatting.RED : ChatFormatting.GRAY);
            if (entry.isSelected) {
                extra.withStyle(ChatFormatting.STRIKETHROUGH);
            }
            text.append(extra);
        }
        return text;
    }

    @Override
    protected boolean universalMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (super.universalMouseClicked(
                mouseX,
                mouseY,
                button
        )) {
            return true;
        }

        listScroll.update(
                options.size(),
                visibleRows
        );

        if (button == 0
                && listScroll.beginDrag(
                        mouseX,
                        mouseY,
                        listX + listW + 2,
                        listY,
                        6,
                        listH,
                        20,
                        0
                )) {
            return true;
        }

        if (mouseX < listX
                || mouseX >= listX + listW
                || mouseY < listY
                || mouseY >= listY + listH) {
            return false;
        }

        int index =
                listScroll.offset()
                        + (int) (
                        (mouseY - listY)
                                / ITEM_HEIGHT
                );

        if (index < 0
                || index >= options.size()) {
            return false;
        }

        RecipeRemovalScreen.SelectionEntry entry =
                options.get(index);

        if (button == 0) {
            entry.isSelected = true;
            return true;
        }

        if (button == 1) {
            entry.isSelected = false;
            return true;
        }

        return false;
    }

    @Override
    protected boolean universalMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (listScroll.drag(
                mouseY,
                listY,
                listH,
                20
        )) {
            return true;
        }

        return super.universalMouseDragged(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY
        );
    }

    @Override
    protected boolean universalMouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (listScroll.release(button)) {
            return true;
        }

        return super.universalMouseReleased(
                mouseX,
                mouseY,
                button
        );
    }

    @Override
    protected boolean universalMouseScrolled(
            double mouseX,
            double mouseY,
            double delta
    ) {
        listScroll.update(
                options.size(),
                visibleRows
        );

        return listScroll.scroll(delta)
                || super.universalMouseScrolled(
                        mouseX,
                        mouseY,
                        delta
                );
    }
}
