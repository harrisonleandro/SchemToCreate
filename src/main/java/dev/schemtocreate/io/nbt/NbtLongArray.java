package dev.schemtocreate.io.nbt;

import java.util.Arrays;
import java.util.Objects;

public final class NbtLongArray implements NbtTag {

    private final long[] value;

    public NbtLongArray(long... value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** Direct reference; callers must not mutate. */
    public long[] value() {
        return value;
    }

    public int length() {
        return value.length;
    }

    @Override
    public byte typeId() {
        return NbtType.LONG_ARRAY;
    }

    @Override
    public NbtLongArray copy() {
        return new NbtLongArray(value.clone());
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof NbtLongArray tag && Arrays.equals(value, tag.value));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "NbtLongArray[length=" + value.length + "]";
    }
}
