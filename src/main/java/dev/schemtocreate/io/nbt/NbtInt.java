package dev.schemtocreate.io.nbt;

public record NbtInt(int value) implements NbtTag {

    @Override
    public byte typeId() {
        return NbtType.INT;
    }

    @Override
    public NbtInt copy() {
        return this;
    }
}
