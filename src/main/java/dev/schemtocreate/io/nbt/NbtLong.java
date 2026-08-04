package dev.schemtocreate.io.nbt;

public record NbtLong(long value) implements NbtTag {

    @Override
    public byte typeId() {
        return NbtType.LONG;
    }

    @Override
    public NbtLong copy() {
        return this;
    }
}
