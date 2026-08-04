package dev.schemtocreate.io.nbt;

import java.util.Arrays;
import java.util.Objects;

/** Not a record: array identity semantics would break value equality in tests and maps. */
public final class NbtByteArray implements NbtTag {

    private final byte[] value;

    public NbtByteArray(byte[] value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /** Direct reference; callers must not mutate. */
    public byte[] value() {
        return value;
    }

    public int length() {
        return value.length;
    }

    @Override
    public byte typeId() {
        return NbtType.BYTE_ARRAY;
    }

    @Override
    public NbtByteArray copy() {
        return new NbtByteArray(value.clone());
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof NbtByteArray tag && Arrays.equals(value, tag.value));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "NbtByteArray[length=" + value.length + "]";
    }
}
