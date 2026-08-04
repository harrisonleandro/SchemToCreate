package dev.schemtocreate.io.nbt;

public record NbtShort(short value) implements NbtTag {

    @Override
    public byte typeId() {
        return NbtType.SHORT;
    }

    @Override
    public NbtShort copy() {
        return this;
    }
}
