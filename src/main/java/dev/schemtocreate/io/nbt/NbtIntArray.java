package dev.schemtocreate.io.nbt;

import java.util.Arrays;
import java.util.Objects;

public final class NbtIntArray implements NbtTag {

    private final int[] value;

    public NbtIntArray(int... value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** Direct reference; callers must not mutate. */
    public int[] value() {
        return value;
    }

    public int length() {
        return value.length;
    }

    public int get(int index) {
        return value[index];
    }

    @Override
    public byte typeId() {
        return NbtType.INT_ARRAY;
    }

    @Override
    public NbtIntArray copy() {
        return new NbtIntArray(value.clone());
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof NbtIntArray tag && Arrays.equals(value, tag.value));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "NbtIntArray" + Arrays.toString(value);
    }
}
