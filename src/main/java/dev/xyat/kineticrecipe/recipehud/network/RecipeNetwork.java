package dev.xyat.kineticrecipe.recipehud.network;

import dev.xyat.kineticrecipe.KineticRecipe;
import dev.xyat.kineticrecipe.recipehud.RecipeDatabase;
import dev.xyat.kineticrecipe.recipehud.RecipeRecord;
import dev.xyat.kineticrecipe.recipehud.RecipeMenu;
import dev.xyat.kineticrecipe.recipehud.RecipeRegistry;
import dev.xyat.kineticrecipe.recipehud.RecipeSaveManager;
import dev.xyat.kineticrecipe.recipehud.UniversalRecipeMenu;
import dev.xyat.kineticrecipe.recipehud.removal.RecipeRemovalManager;
import dev.xyat.kineticrecipe.recipehud.removal.RemovalEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class RecipeNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KineticRecipe.MODID, "recipe_network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private RecipeNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::new, ActionPacket::handle);
        CHANNEL.registerMessage(id++, RequestSyncPacket.class, RequestSyncPacket::encode, RequestSyncPacket::new, RequestSyncPacket::handle);
        CHANNEL.registerMessage(id++, SyncPacket.class, SyncPacket::encode, SyncPacket::new, SyncPacket::handle);
        CHANNEL.registerMessage(id++, RecipeChangePacket.class, RecipeChangePacket::encode, RecipeChangePacket::new, RecipeChangePacket::handle);
        CHANNEL.registerMessage(id++, RequestEditPacket.class, RequestEditPacket::encode, RequestEditPacket::new, RequestEditPacket::handle);
        CHANNEL.registerMessage(id++, RequestRecipeRecordsPacket.class, RequestRecipeRecordsPacket::encode, RequestRecipeRecordsPacket::new, RequestRecipeRecordsPacket::handle);
        CHANNEL.registerMessage(id++, RecipeRecordsSyncPacket.class, RecipeRecordsSyncPacket::encode, RecipeRecordsSyncPacket::new, RecipeRecordsSyncPacket::handle);
        CHANNEL.registerMessage(id++, ToastPacket.class, ToastPacket::encode, ToastPacket::new, ToastPacket::handle);
        CHANNEL.registerMessage(id++, ApplyPendingRecipesPacket.class, ApplyPendingRecipesPacket::encode, ApplyPendingRecipesPacket::new, ApplyPendingRecipesPacket::handle);
        CHANNEL.registerMessage(id, RequestOpenHubPacket.class, RequestOpenHubPacket::encode, RequestOpenHubPacket::new, RequestOpenHubPacket::handle);
    }

    public record RequestOpenHubPacket() {
        public RequestOpenHubPacket(FriendlyByteBuf buf) {
            this();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                RecipeDatabase.loadDatabase();
                NetworkHooks.openScreen(
                        player,
                        new SimpleMenuProvider(
                                (id, inv, p) -> new RecipeMenu(id, inv),
                                Component.translatable("gui.kineticrecipe.recipehud.title")
                        )
                );
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record ToastPacket(Component message) {
        public ToastPacket(FriendlyByteBuf buf) {
            this(buf.readComponent());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeComponent(message);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RecipeNetworkClient.handleToast(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record RequestEditPacket(String uuid, String editorType) {
        public RequestEditPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(), buf.readUtf());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(uuid);
            buf.writeUtf(editorType);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !player.hasPermissions(2)) {
                    return;
                }
                if (!uuid.isEmpty() && !isValidUuid(uuid)) {
                    sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.invalid_data"));
                    return;
                }
                RecipeDatabase.loadDatabase();

                RecipeRegistry.EditorType type;
                try {
                    type = RecipeRegistry.EditorType.valueOf(editorType);
                } catch (IllegalArgumentException exception) {
                    sendToast(
                            player,
                            Component.translatable(
                                    "msg.kineticrecipe.recipehud.invalid_type",
                                    editorType
                            )
                    );
                    return;
                }

                RecipeRecord record = uuid.isEmpty()
                        ? null
                        : RecipeDatabase.records.stream()
                        .filter(value -> value.uuid != null && value.uuid.equals(uuid))
                        .findFirst()
                        .orElse(null);

                NetworkHooks.openScreen(
                        player,
                        new SimpleMenuProvider(
                                (id, inv, p) -> new UniversalRecipeMenu(id, inv, type, record),
                                type.getTitle()
                        ),
                        buf -> {
                            buf.writeUtf(type.name());
                            buf.writeBoolean(record != null);
                            if (record != null) {
                                buf.writeNbt(record.saveToNBT());
                                buf.writeUtf(record.uuid == null ? "" : record.uuid);
                            }
                        }
                );
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record RecipeChangePacket(
            String uuid,
            String editorType,
            boolean isShapeless,
            List<Integer> inputNbtModes,
            boolean outputUseNbt,
            int action,
            List<ItemStack> inputs,
            ItemStack output
    ) {
        public RecipeChangePacket(FriendlyByteBuf buf) {
            this(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    readIntList(buf),
                    buf.readBoolean(),
                    buf.readInt(),
                    readItemList(buf),
                    buf.readItem()
            );
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(uuid);
            buf.writeUtf(editorType);
            buf.writeBoolean(isShapeless);
            buf.writeInt(inputNbtModes.size());
            for (int mode : inputNbtModes) {
                buf.writeInt(mode);
            }
            buf.writeBoolean(outputUseNbt);
            buf.writeInt(action);
            buf.writeInt(inputs.size());
            for (ItemStack stack : inputs) {
                buf.writeItem(stack);
            }
            buf.writeItem(output);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !player.hasPermissions(2)) {
                    return;
                }
                if (action != 0 && action != 1) {
                    sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.invalid_data"));
                    return;
                }
                if (action == 1) {
                    if (!isValidUuid(uuid)) {
                        sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.invalid_data"));
                        return;
                    }
                    RecipeSaveManager.deleteOnly(player, uuid);
                    return;
                }
                if (!isValidRecipeChange(this)) {
                    sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.invalid_data"));
                    return;
                }
                if (inputs.stream().allMatch(ItemStack::isEmpty)) {
                    sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.input_empty"));
                    return;
                }
                if (output.isEmpty()) {
                    sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.output_empty"));
                    return;
                }
                RecipeSaveManager.saveOnly(player, this);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record ActionPacket(int action, RemovalEntry entry) {
        public ActionPacket(FriendlyByteBuf buf) {
            this(buf.readInt(), buf.readBoolean() ? RemovalEntry.fromNetwork(buf) : null);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(action);
            buf.writeBoolean(entry != null);
            if (entry != null) {
                entry.toNetwork(buf);
            }
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                if (action == 0 && isValidRemovalEntry(entry)) {
                    RecipeRemovalManager.addEntry(entry);
                } else if (action == 1 && isValidRemovalEntry(entry)) {
                    RecipeRemovalManager.removeEntry(entry);
                } else if (action == 2 && entry == null) {
                    RecipeRemovalManager.saveAndApply(player);
                } else {
                    sendToast(player, Component.translatable("gui.kineticrecipe.recipehud.err.invalid_data"));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record ApplyPendingRecipesPacket() {
        public ApplyPendingRecipesPacket(FriendlyByteBuf buf) {
            this();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.hasPermissions(2)) {
                    RecipeSaveManager.applyPending(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record RequestSyncPacket() {
        public RequestSyncPacket(FriendlyByteBuf buf) {
            this();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.hasPermissions(2)) {
                    RecipeRemovalManager.syncToPlayer(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SyncPacket(List<RemovalEntry> entries) {
        public SyncPacket(FriendlyByteBuf buf) {
            this(readEntries(buf));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(entries.size());
            for (RemovalEntry entry : entries) {
                entry.toNetwork(buf);
            }
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RecipeNetworkClient.handleSync(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record RequestRecipeRecordsPacket() {
        public RequestRecipeRecordsPacket(FriendlyByteBuf buf) {
            this();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.hasPermissions(2)) {
                    syncRecipeRecords(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record RecipeRecordsSyncPacket(List<RecipeRecord> records) {
        public RecipeRecordsSyncPacket(FriendlyByteBuf buf) {
            this(readRecords(buf));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(records.size());
            for (RecipeRecord record : records) {
                buf.writeNbt(record.saveToNBT());
            }
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RecipeNetworkClient.handleRecipeRecords(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    private static List<Integer> readIntList(FriendlyByteBuf buf) {
        int size = readBoundedSize(buf, 64);
        List<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readInt());
        }
        return list;
    }

    private static List<ItemStack> readItemList(FriendlyByteBuf buf) {
        int size = readBoundedSize(buf, 64);
        List<ItemStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readItem());
        }
        return list;
    }

    private static List<RemovalEntry> readEntries(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<RemovalEntry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(RemovalEntry.fromNetwork(buf));
        }
        return list;
    }

    private static List<RecipeRecord> readRecords(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<RecipeRecord> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                try {
                    RecipeRecord record = RecipeRecord.loadFromNBT(tag);
                    if (record.output != null && !record.output.isEmpty()) {
                        list.add(record);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }


    private static int readBoundedSize(FriendlyByteBuf buf, int maximum) {
        int size = buf.readInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("invalid list size");
        }
        return size;
    }

    private static boolean isValidUuid(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isValidRecipeChange(RecipeChangePacket packet) {
        if (packet == null || packet.editorType() == null || packet.inputs() == null
                || packet.inputNbtModes() == null || packet.output() == null) {
            return false;
        }
        if (packet.uuid() != null && !packet.uuid().isEmpty() && !isValidUuid(packet.uuid())) {
            return false;
        }

        RecipeRegistry.EditorType type;
        try {
            type = RecipeRegistry.EditorType.valueOf(packet.editorType());
        } catch (IllegalArgumentException exception) {
            return false;
        }

        int required = switch (type) {
            case CRAFTING -> 9;
            case SMITHING -> 3;
            default -> 1;
        };
        if (packet.inputs().size() != required || packet.inputNbtModes().size() != required) {
            return false;
        }
        if (type != RecipeRegistry.EditorType.CRAFTING && packet.isShapeless()) {
            return false;
        }
        if (type == RecipeRegistry.EditorType.SMITHING && packet.outputUseNbt()) {
            return false;
        }
        for (Integer mode : packet.inputNbtModes()) {
            if (mode == null || mode < 0 || mode > 2) return false;
        }
        for (ItemStack stack : packet.inputs()) {
            if (stack == null) return false;
            if (!stack.isEmpty() && ForgeRegistries.ITEMS.getKey(stack.getItem()) == null) return false;
        }
        if (packet.output().isEmpty() || ForgeRegistries.ITEMS.getKey(packet.output().getItem()) == null
                || packet.output().getCount() < 1 || packet.output().getCount() > 64) {
            return false;
        }
        if (type == RecipeRegistry.EditorType.SMITHING) {
            return packet.inputs().stream().noneMatch(ItemStack::isEmpty);
        }
        if (type != RecipeRegistry.EditorType.CRAFTING) {
            return !packet.inputs().get(0).isEmpty();
        }
        return packet.inputs().stream().anyMatch(stack -> !stack.isEmpty());
    }

    private static boolean isValidRemovalEntry(RemovalEntry entry) {
        if (entry == null || entry.mode() == null || entry.value() == null || entry.value().isBlank()) {
            return false;
        }
        String value = entry.value().trim();
        return switch (entry.mode()) {
            case MOD -> {
                ResourceLocation probe = ResourceLocation.tryParse(value + ":entry");
                yield probe != null && probe.getNamespace().equals(value);
            }
            case OUTPUT -> {
                ResourceLocation id = ResourceLocation.tryParse(value);
                yield id != null && ForgeRegistries.ITEMS.containsKey(id);
            }
            case TYPE -> {
                ResourceLocation id = ResourceLocation.tryParse(value);
                yield id != null && ForgeRegistries.RECIPE_TYPES.containsKey(id);
            }
            case RECIPE_ID, TAG -> ResourceLocation.tryParse(value) != null;
        };
    }

    public static void sendToast(ServerPlayer player, Component message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ToastPacket(message));
    }

    public static void sendAdd(RemovalEntry entry) {
        CHANNEL.sendToServer(new ActionPacket(0, entry));
    }

    public static void sendRemove(RemovalEntry entry) {
        CHANNEL.sendToServer(new ActionPacket(1, entry));
    }

    public static void sendSaveRemovalRequest() {
        CHANNEL.sendToServer(new ActionPacket(2, null));
    }

    public static void requestOpenHub() {
        CHANNEL.sendToServer(new RequestOpenHubPacket());
    }

    public static void requestOpen() {
        CHANNEL.sendToServer(new RequestSyncPacket());
    }

    public static void requestRecipeRecords() {
        CHANNEL.sendToServer(new RequestRecipeRecordsPacket());
    }

    public static void sendRecipeChange(RecipeChangePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendApplyPendingRecipesRequest() {
        CHANNEL.sendToServer(new ApplyPendingRecipesPacket());
    }

    public static void syncRecipeRecords(ServerPlayer player) {
        RecipeDatabase.reloadDatabase();
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RecipeRecordsSyncPacket(RecipeDatabase.snapshot())
        );
    }
}
