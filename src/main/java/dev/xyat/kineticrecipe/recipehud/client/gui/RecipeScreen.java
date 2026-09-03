package dev.xyat.kineticrecipe.recipehud.client.gui;

import net.minecraft.ChatFormatting;
import dev.xyat.kineticcore.api.client.AdaptiveItemGridRenderer;
import dev.xyat.kineticcore.api.client.GuiToastUtil;
import dev.xyat.kineticcore.api.client.ItemCache;
import dev.xyat.kineticcore.api.client.ItemSelectorScreen;
import dev.xyat.kineticcore.api.client.gui.HighZButton;
import dev.xyat.kineticcore.api.client.gui.NbtEditorScreen;
import dev.xyat.kineticcore.api.client.gui.NumericEditBox;
import dev.xyat.kineticrecipe.recipehud.RecipeRecord;
import dev.xyat.kineticrecipe.recipehud.RecipeRegistry;
import dev.xyat.kineticrecipe.recipehud.UniversalRecipeMenu;
import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork;
import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork.RecipeChangePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import dev.xyat.kineticcore.api.client.gui.ResponsiveContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

public class RecipeScreen extends ResponsiveContainerScreen<UniversalRecipeMenu> {
    private static final ResourceLocation CRAFTING_BG = new ResourceLocation("textures/gui/container/crafting_table.png");
    private static final ResourceLocation FURNACE_BG = new ResourceLocation("textures/gui/container/furnace.png");
    private static final ResourceLocation SMITHING_BG = new ResourceLocation("textures/gui/container/smithing.png");
    private static final ResourceLocation STONECUTTER_BG = new ResourceLocation("textures/gui/container/stonecutter.png");

    private boolean isShapeless = false;
    private final int[] inputNbtModes = new int[9];
    private boolean outputUseNbt = true;

    private NumericEditBox countInput;
    private int boxX;
    private int boxY;

    public RecipeScreen(UniversalRecipeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;

        configureResponsiveContainer(
        );
    }

    public void showToast(Component msg) {
        GuiToastUtil.showToast(msg);
    }

    private void showToast(String key, Object... args) {
        showToast(Component.translatable(key, args));
    }

    @Override
    protected void init() {
        super.init();

        if (menu.clientRecordData != null) {
            RecipeRecord record = RecipeRecord.loadFromNBT(menu.clientRecordData);
            this.isShapeless = record.isShapeless;
            this.outputUseNbt = record.outputUseNbt;
            for (int i = 0; i < record.inputModes.size() && i < inputNbtModes.length; i++) {
                this.inputNbtModes[i] = record.inputModes.get(i);
            }
        }

        if (menu.type == RecipeRegistry.EditorType.SMITHING) {
            this.outputUseNbt = false;
            this.titleLabelX = this.imageWidth - this.font.width(this.title) - 10;
            this.titleLabelY = 10;
        }

        int bW = 85;
        int bH = 20;
        int sp = 4;
        int x = this.leftPos - bW - 6;
        int y = this.topPos + 5;

        this.addRenderableWidget(new HighZButton(x, y, bW, bH, Component.translatable("gui.kineticrecipe.recipehud.back"),
                b -> {
                    if (RecipePreviewState.returnToPreview && Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().setScreen(new RecipePreviewScreen(null));
                        RecipeNetwork.requestRecipeRecords();
                    } else if (Minecraft.getInstance().player != null) {
                        RecipeNetwork.requestOpenHub();
                    }
                }, null));
        y += bH + sp;

        if (menu.type == RecipeRegistry.EditorType.CRAFTING) {
            this.addRenderableWidget(new HighZButton(x, y, bW, bH, getModeText(), b -> {
                isShapeless = !isShapeless;
                b.setMessage(getModeText());
            }, null));
            y += bH + sp;
        }

        this.addRenderableWidget(new HighZButton(x, y, bW, bH, Component.translatable("gui.kineticrecipe.recipehud.save_deferred"), b -> handleSave(), Tooltip.create(Component.translatable("gui.kineticrecipe.recipehud.tooltip.save_deferred"))));

        int outputSlotX;
        int outputSlotY;
        switch (menu.type) {
            case CRAFTING -> { outputSlotX = 124; outputSlotY = 35; }
            case SMITHING -> { outputSlotX = 98; outputSlotY = 48; }
            case STONECUTTER -> { outputSlotX = 143; outputSlotY = 33; }
            default -> { outputSlotX = 116; outputSlotY = 35; }
        }

        boxX = this.leftPos + outputSlotX;
        boxY = this.topPos + outputSlotY - 18;

        // 保存前一次的值，防止屏幕切换重绘时输入框内容丢失
        String prevCountValue = (countInput != null) ? countInput.getValue() : "1";

        countInput = NumericEditBox.integer(
                this.font,
                boxX + 4,
                boxY + 2,
                14,
                10,
                Component.empty(),
                false,
                1,
                64
        );
        countInput.setBordered(false);
        countInput.setMaxLength(2);

        if (menu.clientRecordData != null) {
            RecipeRecord record = RecipeRecord.loadFromNBT(menu.clientRecordData);
            countInput.setValue(String.valueOf(record.output.getCount()));
            menu.clientRecordData = null;
        } else {
            countInput.setValue(prevCountValue);
        }

        countInput.setTooltip(Tooltip.create(Component.translatable("gui.kineticrecipe.recipehud.tooltip.count_input")));
        this.addRenderableWidget(countInput);
    }

    private Component getModeText() {
        return isShapeless
                ? Component.translatable("gui.kineticrecipe.recipehud.mode.shapeless")
                : Component.translatable("gui.kineticrecipe.recipehud.mode.shaped");
    }

    private void handleItemSelectorResult(ItemSelectorScreen.Selection selection, int slotIdx, Container container, boolean allowTag) {
        if (selection.isTag()) {
            if (!allowTag) return;
            String tagId = "#" + selection.value();
            ItemStack dummy = new ItemStack(Items.PAPER);
            dummy.getOrCreateTag().putString("kt_tag", tagId);
            dummy.setHoverName(Component.translatable("gui.kineticrecipe.recipehud.tooltip.tag_item.colored", Component.literal(tagId).withStyle(ChatFormatting.GREEN)));
            container.setItem(slotIdx, dummy);
            return;
        }
        if (!selection.isItem()) return;
        ItemStack stack = selection.stack().copy();
        container.setItem(slotIdx, stack.isEmpty() || stack.is(Items.AIR) ? ItemStack.EMPTY : stack);
    }

    private boolean isInvalidPlaceholder(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getTag() != null
                && stack.getTag().getBoolean("kineticrecipe_invalid_placeholder");
    }

    private boolean containsInvalidPlaceholder() {
        if (isInvalidPlaceholder(menu.outputContainer.getItem(0))) {
            return true;
        }
        int inputCount = menu.type == RecipeRegistry.EditorType.CRAFTING
                ? 9
                : (menu.type == RecipeRegistry.EditorType.SMITHING ? 3 : 1);
        for (int i = 0; i < inputCount; i++) {
            if (isInvalidPlaceholder(menu.inputContainer.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private void openItemSelectorForSlot(int slotIdx, Container container, boolean allowTag) {
        ItemCache.prepareCache(() -> Minecraft.getInstance().setScreen(
                new ItemSelectorScreen(
                        this,
                        selection -> handleItemSelectorResult(selection, slotIdx, container, allowTag)
                )
        ));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double virtualMouseX =
                toVirtualX(mouseX);

        double virtualMouseY =
                toVirtualY(mouseY);

        if (countInput != null) {
            if (countInput.isMouseOver(virtualMouseX, virtualMouseY)) {
                countInput.setFocused(true);
                this.setFocused(countInput);
            } else {
                countInput.setFocused(false);
                if (this.getFocused() == countInput) this.setFocused(null);
            }
        }

        if (button == 0
                && this.hoveredSlot != null
                && isInvalidPlaceholder(this.hoveredSlot.getItem())
                && this.menu.getCarried().isEmpty()) {
            if (this.hoveredSlot.container == menu.inputContainer
                    || this.hoveredSlot.container == menu.outputContainer) {
                int slotIdx = this.hoveredSlot.getContainerSlot();
                Container container = this.hoveredSlot.container;
                boolean isInput = container == menu.inputContainer;
                openItemSelectorForSlot(slotIdx, container, isInput);
                return true;
            }
        }

        // Shift+左键有物品：直接召唤全屏统一 NBT 编辑器
        if (button == 0 && Screen.hasShiftDown() && this.hoveredSlot != null && this.hoveredSlot.hasItem() && this.menu.getCarried().isEmpty()) {
            if (this.hoveredSlot.container == menu.inputContainer || this.hoveredSlot.container == menu.outputContainer) {
                int slotIdx = this.hoveredSlot.getContainerSlot();
                Container container = this.hoveredSlot.container;
                ItemStack stack = this.hoveredSlot.getItem();
                String initNbt = (stack.hasTag() && stack.getTag() != null) ? stack.getTag().toString() : "";

                if (this.minecraft != null) {
                    this.minecraft.setScreen(new NbtEditorScreen(initNbt, (savedNbt) -> {
                        try {
                            if (savedNbt == null || savedNbt.trim().isEmpty() || savedNbt.trim().equals("{}")) {
                                stack.setTag(null);
                            } else {
                                stack.setTag(net.minecraft.nbt.TagParser.parseTag(savedNbt));
                            }
                            container.setItem(slotIdx, stack);
                            showToast("msg.kineticrecipe.saved");
                        } catch (Exception ignored) {
                        }
                    }, this));
                }
                return true;
            }
        }

        // 左键空槽：打开物品搜索（输入槽支持返回#tag，输出槽只返回物品）
        if (button == 0 && this.hoveredSlot != null && !this.hoveredSlot.hasItem() && this.menu.getCarried().isEmpty()) {
            if (this.hoveredSlot.container == menu.inputContainer || this.hoveredSlot.container == menu.outputContainer) {
                int slotIdx = this.hoveredSlot.getContainerSlot();
                Container container = this.hoveredSlot.container;
                boolean isInput = this.hoveredSlot.container == menu.inputContainer;

                openItemSelectorForSlot(slotIdx, container, isInput);
                return true;
            }
        }

        // 右键输入槽有物品：切换NBT匹配模式（tag物品不可切换）
        if (button == 1 && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            if (this.hoveredSlot.container == menu.inputContainer) {
                int slotIdx = this.hoveredSlot.getContainerSlot();
                ItemStack stack = this.hoveredSlot.getItem();
                if (stack.getTag() != null && stack.hasTag() && stack.getTag().contains("kt_tag")) return true;
                inputNbtModes[slotIdx] = (inputNbtModes[slotIdx] + 1) % 3;
                return true;
            } else if (this.hoveredSlot.container == menu.outputContainer) {
                if (menu.type != RecipeRegistry.EditorType.SMITHING) outputUseNbt = !outputUseNbt;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected @NotNull List<Component> getTooltipFromContainerItem(@NotNull ItemStack stack) {
        List<Component> original = super.getTooltipFromContainerItem(stack);
        if (isInvalidPlaceholder(stack)) {
            List<Component> invalidTooltip = new ArrayList<>();
            invalidTooltip.add(stack.getHoverName());
            invalidTooltip.add(Component.empty());
            invalidTooltip.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.invalid_placeholder_replace.colored"));
            return invalidTooltip;
        }
        if (this.hoveredSlot != null && this.hoveredSlot.getItem() == stack
                && (this.hoveredSlot.container == menu.inputContainer || this.hoveredSlot.container == menu.outputContainer)) {
            List<Component> cleaned = new ArrayList<>();
            if (!original.isEmpty()) cleaned.add(original.get(0));
            cleaned.add(Component.empty());
            if (this.hoveredSlot.container == menu.inputContainer) {
                if (stack.getTag() != null && stack.hasTag() && stack.getTag().contains("kt_tag")) {
                    cleaned.add(stack.getHoverName());
                    cleaned.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.lclick_remove.tag.colored"));
                    return cleaned;
                }
                int mode = inputNbtModes[this.hoveredSlot.getContainerSlot()];
                Component statusPrefix = Component.translatable("gui.kineticrecipe.recipehud.status.nbt.colored");
                if (mode == 1) {
                    cleaned.add(statusPrefix.copy().append(Component.translatable("gui.kineticrecipe.recipehud.mode.weak.colored")));
                    cleaned.add(Component.translatable("gui.kineticrecipe.recipehud.mode.weak.desc.colored"));
                } else if (mode == 2) {
                    cleaned.add(statusPrefix.copy().append(Component.translatable("gui.kineticrecipe.recipehud.mode.strong.colored")));
                    cleaned.add(Component.translatable("gui.kineticrecipe.recipehud.mode.strong.desc.colored"));
                } else {
                    cleaned.add(statusPrefix.copy().append(Component.translatable("gui.kineticrecipe.recipehud.mode.none.colored")));
                    cleaned.add(Component.translatable("gui.kineticrecipe.recipehud.mode.none.desc.colored"));
                }
                cleaned.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.rclick_toggle.colored"));
            } else {
                Component statusPrefix = Component.translatable("gui.kineticrecipe.recipehud.status.nbt_output.colored");
                if (menu.type == RecipeRegistry.EditorType.SMITHING) {
                    cleaned.add(statusPrefix.copy().append(Component.translatable("gui.kineticrecipe.recipehud.mode.off.colored")));
                    cleaned.add(Component.translatable("gui.kineticrecipe.recipehud.mode.none.desc.colored"));
                } else {
                    cleaned.add(statusPrefix.copy().append(Component.translatable(outputUseNbt ? "gui.kineticrecipe.recipehud.mode.on.colored" : "gui.kineticrecipe.recipehud.mode.off.colored")));
                    cleaned.add(Component.translatable(outputUseNbt ? "gui.kineticrecipe.recipehud.mode.on.desc.colored" : "gui.kineticrecipe.recipehud.mode.off.desc.colored"));
                    cleaned.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.rclick_toggle.colored"));
                }
            }
            cleaned.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.shift_edit_nbt.colored"));
            cleaned.add(Component.translatable("gui.kineticrecipe.recipehud.tooltip.lclick_remove.slot.colored"));
            return cleaned;
        }
        return original;
    }

    private void handleSave() {
        if (containsInvalidPlaceholder()) {
            showToast("gui.kineticrecipe.recipehud.err.invalid_placeholder");
            return;
        }
        ItemStack originalOut = menu.outputContainer.getItem(0);
        if (originalOut.isEmpty()) {
            showToast("gui.kineticrecipe.recipehud.err.output_empty");
            return;
        }
        Integer countValue =
                countInput.getIntValue();

        if (countValue == null) {
            showToast(
                    "msg.kineticrecipe.invalid_number"
            );
            return;
        }

        int count = countValue;
        ItemStack outStack = originalOut.copy();
        outStack.setCount(count);
        List<ItemStack> inputs = new ArrayList<>();
        List<Integer> modes = new ArrayList<>();
        int inputCount = (menu.type == RecipeRegistry.EditorType.CRAFTING) ?
                9 : ((menu.type == RecipeRegistry.EditorType.SMITHING) ? 3 : 1);
        for (int i = 0; i < inputCount; i++) {
            inputs.add(menu.inputContainer.getItem(i));
            modes.add(inputNbtModes[i]);
        }
        if (menu.type != RecipeRegistry.EditorType.CRAFTING || isShapeless) {
            if (inputs.stream().allMatch(ItemStack::isEmpty)) {
                showToast("gui.kineticrecipe.recipehud.err.input_empty");
                return;
            }
        }
        String uuidToSend = menu.editUuid == null ? "" : menu.editUuid;
        RecipeNetwork.sendRecipeChange(new RecipeChangePacket(uuidToSend, menu.editConfigIndex, menu.type.name(), isShapeless, modes, menu.type != RecipeRegistry.EditorType.SMITHING && outputUseNbt, 0, inputs, outStack));
        RecipeEditSessionState.markPendingRecipeApply();
        showToast("gui.kineticrecipe.recipehud.msg.saving_only");
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
    @ParametersAreNonnullByDefault
    protected void renderBg(GuiGraphics g, float p, int x, int y) {
        ResourceLocation t = switch (menu.type) {
            case CRAFTING -> CRAFTING_BG;
            case SMITHING -> SMITHING_BG;
            case STONECUTTER -> STONECUTTER_BG;
            default -> FURNACE_BG;
        };
        g.blit(t, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                AdaptiveItemGridRenderer.drawSlot(g, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
            }
        }
        g.fill(boxX, boxY, boxX + 18, boxY + 12, 0xFF000000);
        g.renderOutline(boxX, boxY, 18, 12, 0xFF555555);
    }

    @Override
    protected void renderResponsiveForeground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (hoveredSlot == null
                || hoveredSlot.hasItem()
                || menu.getCarried().isEmpty() == false) {
            return;
        }

        if (hoveredSlot.container != menu.inputContainer
                && hoveredSlot.container != menu.outputContainer) {
            return;
        }

        List<Component> tooltip =
                new ArrayList<>();

        tooltip.add(
                Component.translatable(
                        "gui.kineticrecipe.recipehud.tooltip.empty_slot_title.colored"
                )
        );

        tooltip.add(
                Component.translatable(
                        "gui.kineticrecipe.recipehud.tooltip.lclick_search.colored"
                )
        );

        graphics.renderComponentTooltip(
                font,
                tooltip,
                mouseX,
                mouseY
        );
    }
}
