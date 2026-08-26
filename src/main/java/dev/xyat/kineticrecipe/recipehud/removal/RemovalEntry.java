package dev.xyat.kineticrecipe.recipehud.removal;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

public record RemovalEntry(RemovalMode mode, String value, String comment) {
    public RemovalEntry(RemovalMode mode, String value, String comment) {
        this.mode = mode;
        this.value = value;
        this.comment = comment != null ? comment : "";
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeUtf(value);
        buf.writeUtf(comment);
    }

    public static RemovalEntry fromNetwork(FriendlyByteBuf buf) {
        return new RemovalEntry(buf.readEnum(RemovalMode.class), buf.readUtf(), buf.readUtf());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemovalEntry that = (RemovalEntry) o;
        return mode == that.mode && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, value);
    }
}