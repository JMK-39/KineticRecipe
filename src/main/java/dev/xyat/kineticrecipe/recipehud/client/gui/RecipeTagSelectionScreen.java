package dev.xyat.kineticrecipe.recipehud.client.gui;

import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.gui.GridScrollController;
import dev.xyat.kineticcore.api.client.gui.SearchableListModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class RecipeTagSelectionScreen extends ScaledScreen {
    private static final int ITEM_HEIGHT = 20;

    private final Screen parent;
    private final Consumer<String> onSelected;
    private final List<String> allTags = new ArrayList<>();
    private final SearchableListModel<String> tagModel;
    private final GridScrollController listScroll = new GridScrollController();

    private EditBox searchBox;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int visibleRows;

    public RecipeTagSelectionScreen(
            Screen parent,
            Consumer<String> onSelected
    ) {
        super(Component.translatable(
                "gui.kineticrecipe.recipehud.tag_selection.title"
        ));

        this.parent = parent;
        this.onSelected = onSelected;

        configureResponsiveCanvas(
                640f,
                360f,
                6
        );

        Objects.requireNonNull(
                ForgeRegistries.ITEMS.tags()
        ).getTagNames().forEach(tagKey ->
                allTags.add("#" + tagKey.location())
        );

        allTags.sort(String::compareTo);
        tagModel = new SearchableListModel<>(
                allTags,
                tag -> tag
        );
        tagModel.refresh("");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void initScaled() {
        listW = Math.max(100, Math.min(360, vWidth - 24));
        listX = (vWidth - listW) / 2;

        searchBox = new EditBox(
                font,
                listX,
                20,
                listW,
                20,
                Component.empty()
        );
        searchBox.setResponder(this::onSearchUpdate);
        addRenderableWidget(searchBox);

        listY = 50;
        listH = Math.max(ITEM_HEIGHT, vHeight - listY - 40);
        visibleRows = Math.max(1, listH / ITEM_HEIGHT);

        listScroll.update(
                tagModel.items().size(),
                visibleRows
        );

        int btnW = 80;
        int btnH = 20;

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
                                vWidth / 2 - btnW / 2,
                                vHeight - 30,
                                btnW,
                                btnH
                        )
                        .build()
        );
    }

    private void onSearchUpdate(String query) {
        tagModel.refresh(query);
        listScroll.reset();
        listScroll.update(
                tagModel.items().size(),
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
                5,
                0xFFFFFF
        );

        GuiRenderUtil.drawDarkPanel(
                graphics,
                listX,
                listY,
                listW,
                listH
        );

        enableVirtualScissor(
                graphics,
                listX,
                listY,
                listX + listW,
                listY + listH
        );

        List<String> displayTags =
                tagModel.items();

        for (int row = 0;
             row < visibleRows;
             row++) {
            int index =
                    listScroll.offset()
                            + row;

            if (index >= displayTags.size()) {
                break;
            }

            String tag =
                    displayTags.get(index);

            int y =
                    listY
                            + row * ITEM_HEIGHT;

            boolean hovered =
                    mouseX >= listX
                            && mouseX < listX + listW
                            && mouseY >= y
                            && mouseY < y + ITEM_HEIGHT;

            int backgroundColor =
                    row % 2 == 0
                            ? 0x44FFFFFF
                            : 0x44888888;

            if (hovered) {
                backgroundColor =
                        0x88FFFFFF;
            }

            graphics.fill(
                    listX,
                    y,
                    listX + listW,
                    y + ITEM_HEIGHT,
                    backgroundColor
            );

            graphics.drawString(
                    font,
                    tag,
                    listX + 5,
                    y + 6,
                    0xFFFFFF
            );
        }

        graphics.disableScissor();

        listScroll.render(
                graphics,
                listX + listW + 2,
                listY,
                6,
                listH,
                20
        );
    }

    @Override
    protected void renderScaledForeground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (searchBox != null
                && !searchBox.isFocused()
                && searchBox.getValue().isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "gui.kineticrecipe.recipehud.search_hint"
                    ),
                    searchBox.getX() + 6,
                    searchBox.getY() + 6,
                    0xFFAAAAAA,
                    false
            );
        }
    }

    @Override
    protected boolean universalMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (searchBox != null
                && !searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setFocused(false);
        }

        if (super.universalMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

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

        if (button == 0
                && mouseX >= listX
                && mouseX < listX + listW
                && mouseY >= listY
                && mouseY < listY + listH) {
            int index = listScroll.offset()
                    + (int) ((mouseY - listY) / ITEM_HEIGHT);

            List<String> displayTags = tagModel.items();

            if (index >= 0 && index < displayTags.size()) {
                onSelected.accept(displayTags.get(index));

                if (minecraft != null) {
                    minecraft.setScreen(parent);
                }

                return true;
            }
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
        return listScroll.scroll(delta)
                || super.universalMouseScrolled(
                        mouseX,
                        mouseY,
                        delta
                );
    }
}
