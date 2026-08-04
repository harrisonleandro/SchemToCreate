package dev.schemtocreate.io.nbt;

public record NbtDouble(double value) implements NbtTag {

    @Override
    public byte typeId() {
        return NbtType.DOUBLE;
    }

    @Override
    public NbtDouble copy() {
        return this;
    }
}
