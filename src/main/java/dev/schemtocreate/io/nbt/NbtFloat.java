package dev.schemtocreate.io.nbt;

public record NbtFloat(float value) implements NbtTag {

    @Override
    public byte typeId() {
        return NbtType.FLOAT;
    }

    @Override
    public NbtFloat copy() {
        return this;
    }
}
