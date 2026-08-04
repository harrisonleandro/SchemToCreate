package dev.schemtocreate.io.nbt;

import java.util.Objects;

public record NbtString(String value) implements NbtTag {

    public NbtString {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public byte typeId() {
        return NbtType.STRING;
    }

    @Override
    public NbtString copy() {
        return this;
    }
}
