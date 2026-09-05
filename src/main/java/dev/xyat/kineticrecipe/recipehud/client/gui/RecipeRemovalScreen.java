package dev.xyat.kineticrecipe.recipehud.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.AdaptiveItemGridRenderer;
import dev.xyat.kineticcore.api.client.AdvancedSearchUtil;
import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.GuiToastUtil;
import dev.xyat.kineticcore.api.client.PinyinUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.gui.GridScrollController;
import dev.xyat.kineticcore.api.client.ItemCache;
import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork;
import dev.xyat.kineticrecipe.recipehud.removal.RemovalEntry;
import dev.xyat.kineticrecipe.recipehud.removal.RemovalMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RecipeRemovalScreen extends ScaledScreen {
    private final Screen parent;

    private boolean isSearchMode = false;
    private RemovalMode currentMode = RemovalMode.OUTPUT;
    private EditBox searchBox;

    private final List<RemovalEntry> allRemovals = new ArrayList<>();
    private final List<RemovalEntry> displayRemovals = new ArrayList<>();
    private final List<ItemCache.CachedItem> displayItems = new ArrayList<>();

    private boolean recipeCacheBuilt = false;
    private final Map<Item, Set<String>> itemToRecipeIds = new HashMap<>();
    private final Map<Item, Set<String>> itemToRecipeTypes = new HashMap<>();
    private final Map<Item, Set<String>> itemToRecipeMods = new HashMap<>();
    private final Map<Item, List<RemovalEntry>> activeRemovalsCache = new HashMap<>();

    private static final int PREFERRED_COLUMNS = 28;
    private static final int SLOT_SIZE = 18;

    private int columns = 1;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int visibleRows = 1;

    private int invX;
    private int invY;

    private boolean compactControls;

    private final GridScrollController gridScroll =
            new GridScrollController();

    public RecipeRemovalScreen(Screen parent, List<RemovalEntry> serverData) {
        super(Component.translatable("gui.kineticrecipe.recipehud.title"));
        this.parent = parent;
        this.allRemovals.addAll(serverData);

        configureResponsiveCanvas(
                640f,
                360f,
                6
        );
    }

    public static class SelectionEntry {
        public final String value; public final String modName; public final String typeName;
        public boolean isSelected; public final boolean alreadyExists;
        public SelectionEntry(String value, String modName, String typeName, boolean alreadyExists) {
            this.value = value; this.modName = modName; this.typeName = typeName;
            this.alreadyExists = alreadyExists; this.isSelected = alreadyExists;
        }
    }

    public void showToast(Component msg) { GuiToastUtil.showToast(msg); }

    private void buildRecipeCacheIfNeeded() {
        if (recipeCacheBuilt || minecraft == null || minecraft.level == null) return;
        RecipeManager rm = minecraft.level.getRecipeManager();
        for (Recipe<?> recipe : rm.getRecipes()) {
            try {
                ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
                if (!result.isEmpty()) {
                    Item item = result.getItem();
                    itemToRecipeIds.computeIfAbsent(item, k -> new HashSet<>()).add(recipe.getId().toString());
                    ResourceLocation typeId = ForgeRegistries.RECIPE_TYPES.getKey(recipe.getType());
                    if (typeId != null) itemToRecipeTypes.computeIfAbsent(item, k -> new HashSet<>()).add(typeId.toString());
                    itemToRecipeMods.computeIfAbsent(item, k -> new HashSet<>()).add(recipe.getId().getNamespace());
                }
            } catch (Exception ignored) {}
        }
        recipeCacheBuilt = true;
    }

    private void refreshActiveRemovalsCache() {
        activeRemovalsCache.clear();
        for (ItemCache.CachedItem ci : ItemCache.getItems()) {
            Item item = ci.stack.getItem();
            List<RemovalEntry> active = new ArrayList<>();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId == null) continue;

            String idStr = itemId.toString();
            String modStr = itemId.getNamespace();

            for (RemovalEntry entry : allRemovals) {
                boolean matches = false;
                switch (entry.mode()) {
                    case OUTPUT: matches = idStr.equals(entry.value()); break;
                    case MOD: matches = modStr.equals(entry.value()) || (itemToRecipeMods.containsKey(item) && itemToRecipeMods.get(item).contains(entry.value())); break;
                    case TAG:
                        for (var tag : ci.stack.getTags().toList()) {
                            if (tag.location().toString().equals(entry.value())) { matches = true; break; }
                        }
                        break;
                    case TYPE: matches = itemToRecipeTypes.containsKey(item) && itemToRecipeTypes.get(item).contains(entry.value()); break;
                    case RECIPE_ID: matches = itemToRecipeIds.containsKey(item) && itemToRecipeIds.get(item).contains(entry.value()); break;
                }
                if (matches) active.add(entry);
            }
            if (!active.isEmpty()) activeRemovalsCache.put(item, active);
        }
    }

    @Override
    protected void initScaled() {
        buildRecipeCacheIfNeeded();
        refreshActiveRemovalsCache();

        int sidePadding = 12;

        int preferredColumns = PREFERRED_COLUMNS;

        int maxColumns =
                Math.max(
                        1,
                        (
                                vWidth
                                        - sidePadding * 2
                                        - 10
                        ) / SLOT_SIZE
                );

        columns =
                Math.max(
                        1,
                        Math.min(
                                preferredColumns,
                                maxColumns
                        )
                );

        gridW =
                columns
                        * SLOT_SIZE;

        gridX =
                Math.max(
                        sidePadding,
                        (
                                vWidth
                                        - gridW
                                        - 10
                        ) / 2
                );

        int topMargin = 8;
        int searchHeight = 20;

        searchBox =
                new EditBox(
                        font,
                        gridX,
                        topMargin,
                        gridW,
                        searchHeight,
                        Component.empty()
                );

        searchBox.setResponder(
                this::onSearchUpdate
        );

        addRenderableWidget(searchBox);

        gridY =
                topMargin
                        + searchHeight
                        + 8;

        int inventoryHeight =
                4 * SLOT_SIZE
                        + 4;

        invX =
                (vWidth
                        - 9 * SLOT_SIZE)
                        / 2;

        invY =
                vHeight
                        - inventoryHeight
                        - 8;

        compactControls = false;

        int buttonWidth =
                Math.max(
                        58,
                        Math.min(
                                80,
                                (vWidth - 32) / 2
                        )
                );

        int buttonHeight = 20;
        int gap = 4;

        int controlsTop;

        if (compactControls) {
            controlsTop =
                    invY
                            - buttonHeight * 2
                            - gap
                            - 8;

            int leftButtonX =
                    vWidth / 2
                            - buttonWidth
                            - gap / 2;

            int rightButtonX =
                    vWidth / 2
                            + gap / 2;

            addControlButtons(
                    leftButtonX,
                    rightButtonX,
                    controlsTop,
                    buttonWidth,
                    buttonHeight,
                    gap
            );
        } else {
            controlsTop =
                    invY;

            int leftButtonX =
                    Math.max(
                            sidePadding,
                            invX
                                    - buttonWidth
                                    - 8
                    );

            int rightButtonX =
                    Math.min(
                            vWidth
                                    - sidePadding
                                    - buttonWidth,
                            invX
                                    + 9 * SLOT_SIZE
                                    + 8
                    );

            addControlButtons(
                    leftButtonX,
                    rightButtonX,
                    invY,
                    buttonWidth,
                    buttonHeight,
                    gap
            );
        }

        int gridBottom =
                compactControls
                        ? controlsTop - 4
                        : invY - 4;

        visibleRows =
                Math.max(
                        1,
                        (
                                gridBottom
                                        - gridY
                        ) / SLOT_SIZE
                );

        gridH =
                visibleRows
                        * SLOT_SIZE;

        updateScrollBox();
    }

    private void addControlButtons(
            int leftX,
            int rightX,
            int firstY,
            int buttonWidth,
            int buttonHeight,
            int gap
    ) {
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
                                leftX,
                                firstY,
                                buttonWidth,
                                buttonHeight
                        )
                        .tooltip(
                                Tooltip.create(
                                        Component.translatable(
                                                "gui.kineticrecipe.recipehud.tooltip.back"
                                        )
                                )
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                getModeComponent(),
                                button -> {
                                    int next =
                                            (
                                                    currentMode.ordinal()
                                                            + 1
                                            )
                                                    % RemovalMode.values().length;

                                    currentMode =
                                            RemovalMode.values()[next];

                                    button.setMessage(
                                            getModeComponent()
                                    );

                                    gridScroll.reset();
                                    updateScrollBox();
                                }
                        )
                        .bounds(
                                leftX,
                                firstY
                                        + buttonHeight
                                        + gap,
                                buttonWidth,
                                buttonHeight
                        )
                        .tooltip(
                                Tooltip.create(
                                        Component.translatable(
                                                "gui.kineticrecipe.recipehud.tooltip.mode"
                                        )
                                )
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                getViewComponent(),
                                button -> {
                                    isSearchMode =
                                            !isSearchMode;

                                    gridScroll.reset();

                                    button.setMessage(
                                            getViewComponent()
                                    );

                                    updateScrollBox();
                                }
                        )
                        .bounds(
                                rightX,
                                firstY,
                                buttonWidth,
                                buttonHeight
                        )
                        .tooltip(
                                Tooltip.create(
                                        Component.translatable(
                                                "gui.kineticrecipe.recipehud.tooltip.view"
                                        )
                                )
                        )
                        .build()
        );

        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.kineticrecipe.recipehud.save_removals_apply"
                                ),
                                button -> {
                                    RecipeNetwork.sendSaveRemovalRequest();

                                    showToast(
                                            Component.translatable(
                                                    "gui.kineticrecipe.recipehud.msg.saving_apply"
                                            )
                                    );
                                }
                        )
                        .bounds(
                                rightX,
                                firstY
                                        + buttonHeight
                                        + gap,
                                buttonWidth,
                                buttonHeight
                        )
                        .tooltip(
                                Tooltip.create(
                                        Component.translatable(
                                                "gui.kineticrecipe.recipehud.tooltip.save_removals"
                                        )
                                )
                        )
                        .build()
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component getModeComponent() {
        return Component.translatable("gui.kineticrecipe.recipehud.mode_prefix").append(currentMode.getDisplayName());
    }

    private Component getViewComponent() {
        return Component.translatable("gui.kineticrecipe.recipehud.view_prefix").append(
                Component.translatable(isSearchMode ? "gui.kineticrecipe.recipehud.view.search" : "gui.kineticrecipe.recipehud.view.preview")
        );
    }

    private void onSearchUpdate(String query) {
        gridScroll.reset();
        updateScrollBox();
    }

    private void updateScrollBox() {
        String query = searchBox.getValue().toLowerCase(Locale.ROOT).trim();

        if (isSearchMode) {
            displayItems.clear();
            List<ItemCache.CachedItem> src = ItemCache.getItems();
            if (query.isEmpty()) displayItems.addAll(src);
            else for (ItemCache.CachedItem ci : src) if (AdvancedSearchUtil.match(ci.searchData, query)) displayItems.add(ci);
        } else {
            displayRemovals.clear();
            for (RemovalEntry entry : allRemovals) {
                if (entry.mode() == currentMode) {
                    if (query.isEmpty()) {
                        displayRemovals.add(entry);
                        continue;
                    }
                    if (entry.mode() == RemovalMode.OUTPUT || entry.mode() == RemovalMode.RECIPE_ID) {
                        Item item = null;
                        try {
                            ResourceLocation rl = new ResourceLocation(entry.value());
                            item = ForgeRegistries.ITEMS.getValue(rl);
                        } catch (Exception ignored) {
                        }
                        String name = item != null ? net.minecraft.client.resources.language.I18n.get(item.getDescriptionId()).toLowerCase(Locale.ROOT) : "";
                        String searchStr = entry.value() + " " + name + " " + PinyinUtil.getSearchData(name);
                        if (AdvancedSearchUtil.match(searchStr, query)) displayRemovals.add(entry);
                    } else {
                        if (AdvancedSearchUtil.match(entry.value(), query)) displayRemovals.add(entry);
                    }
                }
            }
        }

        int totalItems =
                isSearchMode
                        ? displayItems.size()
                        : displayRemovals.size();

        int activeColumns = safeColumns();
        int totalRows =
                (
                        totalItems
                                + activeColumns
                                - 1
                ) / activeColumns;

        gridScroll.update(
                totalRows,
                safeVisibleRows()
        );
    }

    @Override
    protected void renderScaledBackground(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (searchBox == null || gridW <= 0 || gridH <= 0) {
            return;
        }

        int panelX =
                gridX - 12;

        int panelY =
                searchBox.getY() - 6;

        int panelW =
                gridW + 24 + 10;

        int panelBottom =
                compactControls
                        ? invY - 8
                        : invY
                        + 4 * SLOT_SIZE
                        + 12;

        int panelH =
                Math.max(
                        gridH + 20,
                        panelBottom - panelY
                );

        GuiRenderUtil.drawPanel(
                graphics,
                panelX,
                panelY,
                panelW,
                panelH,
                0xFA1E1E1E,
                0xFF444444
        );

        GuiRenderUtil.drawPanel(
                graphics,
                gridX - 2,
                gridY - 2,
                gridW + 4,
                gridH + 4,
                0x55000000,
                0xFF333333
        );

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        int startIndex =
                gridScroll.offset()
                        * safeColumns();

        int totalItems =
                isSearchMode
                        ? displayItems.size()
                        : displayRemovals.size();

        int endIndex =
                Math.min(
                        startIndex
                                + safeVisibleRows() * safeColumns(),
                        totalItems
                );

        enableVirtualScissor(
                graphics,
                gridX,
                gridY,
                gridX + gridW,
                gridY + gridH
        );

        for (int i = startIndex;
             i < endIndex;
             i++) {
            int localIndex =
                    i - startIndex;

            int column =
                    localIndex % safeColumns();

            int row =
                    localIndex / safeColumns();

            int x =
                    gridX
                            + column * SLOT_SIZE;

            int y =
                    gridY
                            + row * SLOT_SIZE;

            boolean hovered = mouseX >= x
                    && mouseX < x + SLOT_SIZE
                    && mouseY >= y
                    && mouseY < y + SLOT_SIZE;
            AdaptiveItemGridRenderer.drawSlot(graphics, x, y, SLOT_SIZE, 4, hovered);

            if (isSearchMode) {
                ItemStack stack =
                        displayItems.get(i).stack;

                graphics.renderItem(
                        stack,
                        x + 1,
                        y + 1
                );

                if (activeRemovalsCache.containsKey(
                        stack.getItem()
                )) {
                    graphics.fill(
                            x,
                            y,
                            x + SLOT_SIZE,
                            y + SLOT_SIZE,
                            0x66FF3333
                    );
                }
            } else {
                renderRemovalEntry(
                        graphics,
                        displayRemovals.get(i),
                        x + 1,
                        y + 1
                );
            }

        }

        graphics.disableScissor();

        gridScroll.render(
                graphics,
                mouseX,
                mouseY,
                gridX + gridW + 4,
                gridY,
                6,
                gridH,
                20,
                0xFF222222,
                0xFF555555,
                0xFF777777
        );

        renderPlayerInventory(
                graphics,
                mouseX,
                mouseY
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

    private int totalRows() {
        int totalItems =
                isSearchMode
                        ? displayItems.size()
                        : displayRemovals.size();
        int activeColumns = safeColumns();

        return (
                totalItems
                        + activeColumns
                        - 1
        ) / activeColumns;
    }

    private int safeColumns() {
        return Math.max(1, columns);
    }

    private int safeVisibleRows() {
        return Math.max(1, visibleRows);
    }

    private void renderPlayerInventory(GuiGraphics g, int mx, int my) {
        if (minecraft == null || minecraft.player == null) return;
        Inventory inv = minecraft.player.getInventory();
        g.drawString(font, Component.translatable("container.inventory"), invX, invY - 12, 0xAAAAAA);

        for (int i = 0; i < 36; i++) {
            int col = i % 9; int row = i / 9;
            int slotIndex = (row == 3) ? col : (col + (row + 1) * 9);
            int x = invX + col * SLOT_SIZE;
            int y = invY + row * SLOT_SIZE + (row == 3 ? 4 : 0);

            ItemStack stack = inv.getItem(slotIndex);
            boolean hovered = mx >= x && mx < x + SLOT_SIZE && my >= y && my < y + SLOT_SIZE;
            AdaptiveItemGridRenderer.drawSlot(g, stack, x, y, SLOT_SIZE, 4, hovered);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 1, y + 1);
                g.renderItemDecorations(font, stack, x + 1, y + 1);
                if (activeRemovalsCache.containsKey(stack.getItem())) g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x66FF3333);
            }

        }
    }

    @Override
    protected void renderTooltips(
            GuiGraphics graphics,
            int scaledMouseX,
            int scaledMouseY,
            int mouseX,
            int mouseY
    ) {
        List<Component> tooltip = buildTooltipAt(
                scaledMouseX,
                scaledMouseY,
                gridScroll.offset()
                        * safeColumns()
        );

        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(
                mouseX,
                mouseY,
                500
        );
        graphics.pose().scale(
                guiScale,
                guiScale,
                1.0F
        );
        graphics.pose().translate(
                -mouseX,
                -mouseY,
                0
        );
        graphics.renderComponentTooltip(
                font,
                tooltip,
                mouseX,
                mouseY
        );
        graphics.pose().popPose();
    }

    private List<Component> buildTooltipAt(int mx, int my, int startIdx) {
        ItemStack hoveredStack = ItemStack.EMPTY;

        if (mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH) {
            int col = (mx - gridX) / SLOT_SIZE;
            int row = (my - gridY) / SLOT_SIZE;
            int idx = startIdx + row * safeColumns() + col;

            if (isSearchMode) {
                if (idx < displayItems.size()) {
                    hoveredStack = displayItems.get(idx).stack;
                }
            } else {
                if (idx < displayRemovals.size()) {
                    RemovalEntry entry = displayRemovals.get(idx);
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.entry", entry.mode().getDisplayName().copy().withStyle(ChatFormatting.AQUA), Component.literal(entry.value()).withStyle(ChatFormatting.GOLD)));
                    tooltip.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.remove"));
                    return tooltip;
                }
                return null;
            }
        } else if (minecraft != null && minecraft.player != null && mx >= invX && mx < invX + 9 * SLOT_SIZE && my >= invY && my < invY + 4 * SLOT_SIZE + 4) {
            int col = (mx - invX) / SLOT_SIZE;
            int row = (my - invY) / SLOT_SIZE;
            if (row == 3 && my >= invY + 3 * SLOT_SIZE + 4) {
                return null;
            }
            if (row > 3) {
                row = 3;
            }

            int slotIndex = (row == 3) ? col : (col + (row + 1) * 9);
            hoveredStack = minecraft.player.getInventory().getItem(slotIndex);
        }

        if (hoveredStack.isEmpty() || minecraft == null) {
            return null;
        }

        List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hoveredStack));
        List<RemovalEntry> active = activeRemovalsCache.get(hoveredStack.getItem());

        if (active != null && !active.isEmpty()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.active_removals.colored"));
            for (RemovalEntry entry : active) {
                tooltip.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.active_removal_item.colored", entry.mode().getDisplayName().copy().withStyle(ChatFormatting.AQUA), Component.literal(entry.value()).withStyle(ChatFormatting.GOLD)));
            }
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.left_add.colored"));
        tooltip.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.right_remove.colored"));
        return tooltip;
    }

    private void renderRemovalEntry(GuiGraphics g, RemovalEntry entry, int x, int y) {
        if (entry == null || entry.value() == null) {
            return;
        }
        if (entry.mode() == RemovalMode.OUTPUT || entry.mode() == RemovalMode.RECIPE_ID) {
            try {
                ResourceLocation rl = new ResourceLocation(entry.value());
                if (ForgeRegistries.ITEMS.containsKey(rl)) {
                    Item item = ForgeRegistries.ITEMS.getValue(rl);
                    if (item != null) {
                        g.renderItem(new ItemStack(item), x, y);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        String value = entry.value();
        if (!value.isEmpty()) {
            g.drawCenteredString(font, value.substring(0, Math.min(value.length(), 2)).toUpperCase(), x + 8, y + 4, 0xFFFFAA);
        }
    }

    @Override
    protected boolean universalMouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (searchBox != null
                && !searchBox.isMouseOver(
                        mouseX,
                        mouseY
                )) {
            searchBox.setFocused(false);
        }

        if (super.universalMouseClicked(
                mouseX,
                mouseY,
                button
        )) {
            return true;
        }

        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        if (button == 0
                && gridScroll.beginDrag(
                        mouseX,
                        mouseY,
                        gridX + gridW + 4,
                        gridY,
                        6,
                        gridH,
                        20,
                        0
                )) {
            return true;
        }

        if (mouseX >= gridX
                && mouseX < gridX + gridW
                && mouseY >= gridY
                && mouseY < gridY + gridH) {
            int column =
                    (int) (
                            (mouseX - gridX)
                                    / SLOT_SIZE
                    );

            int row =
                    (int) (
                            (mouseY - gridY)
                                    / SLOT_SIZE
                    );

            int index =
                    (
                            gridScroll.offset()
                                    + row
                    )
                            * safeColumns()
                            + column;

            if (isSearchMode) {
                if (index < displayItems.size()) {
                    handleAddFromItem(
                            displayItems.get(index).stack,
                            button
                    );
                }
            } else if (button == 0
                    && index < displayRemovals.size()) {
                RemovalEntry toRemove =
                        displayRemovals.get(index);

                removeEntryDirectly(
                        toRemove.mode(),
                        toRemove.value()
                );
            }

            return true;
        }

        if (minecraft == null
                || minecraft.player == null
                || mouseX < invX
                || mouseX >= invX + 9 * SLOT_SIZE
                || mouseY < invY
                || mouseY >= invY + 4 * SLOT_SIZE + 4) {
            return false;
        }

        int column =
                (int) (
                        (mouseX - invX)
                                / SLOT_SIZE
                );

        int row =
                (int) (
                        (mouseY - invY)
                                / SLOT_SIZE
                );

        if (row == 3
                && mouseY
                < invY + 3 * SLOT_SIZE + 4) {
            return false;
        }

        if (row > 3) {
            row = 3;
        }

        int slotIndex =
                row == 3
                        ? column
                        : column
                        + (row + 1) * 9;

        ItemStack stack =
                minecraft.player
                        .getInventory()
                        .getItem(slotIndex);

        if (!stack.isEmpty()) {
            handleAddFromItem(
                    stack,
                    button
            );
        }

        return true;
    }

    @Override
    protected boolean universalMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (gridScroll.drag(
                mouseY,
                gridY,
                gridH,
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
        if (gridScroll.release(button)) {
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
        gridScroll.update(
                totalRows(),
                safeVisibleRows()
        );

        return gridScroll.scroll(delta)
                || super.universalMouseScrolled(
                        mouseX,
                        mouseY,
                        delta
                );
    }

    private boolean isAlreadyAdded(String value) { return allRemovals.contains(new RemovalEntry(currentMode, value, "")); }

    @OnlyIn(Dist.CLIENT)
    private String getTranslatedModName(String modid) {
        String key = "gui.kineticrecipe.recipehud.modname." + modid;
        if (net.minecraft.client.resources.language.I18n.exists(key)) return net.minecraft.client.resources.language.I18n.get(key);
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    private String getTranslatedRecipeType(ResourceLocation typeId) {
        if (typeId == null) return null;
        String namespace = typeId.getNamespace();
        String path = typeId.getPath();

        Map<String, String> vanillaMap = Map.of(
                "crafting", "block.minecraft.crafting_table",
                "smelting", "block.minecraft.furnace",
                "blasting", "block.minecraft.blast_furnace",
                "smoking", "block.minecraft.smoker",
                "campfire_cooking", "block.minecraft.campfire",
                "stonecutting", "block.minecraft.stonecutter",
                "smithing", "block.minecraft.smithing_table"
        );
        if (namespace.equals("minecraft") && vanillaMap.containsKey(path)) return net.minecraft.client.resources.language.I18n.get(vanillaMap.get(path));
        if (ForgeRegistries.BLOCKS.containsKey(typeId)) { var block = ForgeRegistries.BLOCKS.getValue(typeId); if (block != null) return net.minecraft.client.resources.language.I18n.get(block.getDescriptionId()); }
        if (ForgeRegistries.ITEMS.containsKey(typeId)) { var item = ForgeRegistries.ITEMS.getValue(typeId); if (item != null) return net.minecraft.client.resources.language.I18n.get(item.getDescriptionId()); }
        String customKey = "recipe.type." + namespace + "." + path;
        if (net.minecraft.client.resources.language.I18n.exists(customKey)) return net.minecraft.client.resources.language.I18n.get(customKey);
        return null;
    }

    private void handleAddFromItem(ItemStack stack, int btn) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;

        Map<String, SelectionEntry> optionMap = new LinkedHashMap<>();

        if (currentMode == RemovalMode.OUTPUT) {
            optionMap.put(id.toString(), new SelectionEntry(id.toString(), null, null, isAlreadyAdded(id.toString())));
        } else if (currentMode == RemovalMode.TAG) {
            stack.getTags().forEach(tag -> {
                String val = tag.location().toString();
                String modid = tag.location().getNamespace();
                optionMap.put(val, new SelectionEntry(val, getTranslatedModName(modid), null, isAlreadyAdded(val)));
            });
            if (optionMap.isEmpty()) {
                String modid = id.getNamespace();
                optionMap.put(id.toString(), new SelectionEntry(id.toString(), getTranslatedModName(modid), null, isAlreadyAdded(id.toString())));
            }
        } else {
            if (itemToRecipeMods.containsKey(stack.getItem())) {
                if (minecraft != null && minecraft.level != null) {
                    RecipeManager rm = minecraft.level.getRecipeManager();
                    for (Recipe<?> recipe : rm.getRecipes()) {
                        try {
                            ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
                            if (!result.isEmpty() && result.is(stack.getItem())) {
                                String modid = recipe.getId().getNamespace();
                                String modName = getTranslatedModName(modid);
                                ResourceLocation typeId = ForgeRegistries.RECIPE_TYPES.getKey(recipe.getType());

                                String val = null;
                                String typeName = null;

                                if (currentMode == RemovalMode.RECIPE_ID) val = recipe.getId().toString();
                                else if (currentMode == RemovalMode.TYPE && typeId != null) { val = typeId.toString(); typeName = getTranslatedRecipeType(typeId); }
                                else if (currentMode == RemovalMode.MOD) val = modid;
                                if (val != null && !optionMap.containsKey(val)) optionMap.put(val, new SelectionEntry(val, modName, typeName, isAlreadyAdded(val)));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (currentMode == RemovalMode.MOD && !optionMap.containsKey(id.getNamespace())) {
                String modid = id.getNamespace();
                optionMap.put(modid, new SelectionEntry(modid, getTranslatedModName(modid), null, isAlreadyAdded(modid)));
            }
        }

        List<SelectionEntry> options = new ArrayList<>(optionMap.values());
        if (options.isEmpty()) { showToast(Component.translatable("gui.kineticrecipe.recipehud.toast.not_found")); return; }

        if (btn == 0) {
            List<SelectionEntry> toAdd = options.stream().filter(e -> !e.alreadyExists).toList();
            if (toAdd.isEmpty()) showToast(Component.translatable("gui.kineticrecipe.recipehud.msg.already_exists"));
            else if (toAdd.size() == 1) { addEntryFromSelection(currentMode, toAdd.get(0).value); showToast(Component.translatable("gui.kineticrecipe.recipehud.toast.added", Component.literal(toAdd.get(0).value).withStyle(ChatFormatting.GOLD))); }
            else if (minecraft != null) minecraft.setScreen(new RecipeRemovalSelectionScreen(this, currentMode, stack, options));
        } else if (btn == 1) {
            List<SelectionEntry> toRemove = options.stream().filter(e -> e.alreadyExists).toList();
            if (toRemove.isEmpty()) showToast(Component.translatable("gui.kineticrecipe.recipehud.toast.not_found"));
            else if (toRemove.size() == 1) { removeEntryDirectly(currentMode, toRemove.get(0).value); showToast(Component.translatable("gui.kineticrecipe.recipehud.toast.removed", Component.literal(toRemove.get(0).value).withStyle(ChatFormatting.GOLD))); }
            else if (minecraft != null) minecraft.setScreen(new RecipeRemovalSelectionScreen(this, currentMode, stack, options));
        }
    }

    public void addEntryFromSelection(RemovalMode mode, String value) {
        RemovalEntry newEntry = new RemovalEntry(mode, value, "");
        if (!allRemovals.contains(newEntry)) {
            allRemovals.add(0, newEntry);
            RecipeNetwork.sendAdd(newEntry);
            refreshActiveRemovalsCache();
            updateScrollBox();
        }
    }

    public void removeEntryDirectly(RemovalMode mode, String value) {
        RemovalEntry target = new RemovalEntry(mode, value, "");
        if (allRemovals.contains(target)) {
            allRemovals.remove(target);
            displayRemovals.remove(target);
            RecipeNetwork.sendRemove(target);
            refreshActiveRemovalsCache();
            updateScrollBox();
        }
    }

}
