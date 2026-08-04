package dev.schemtocreate.io.nbt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * TAG_Compound. Insertion order is preserved so that round-tripping a file keeps its
 * original key order, which makes byte-level diffing of outputs practical.
 *
 * <p>The numeric getters are deliberately lenient (a {@code short} reads fine as an
 * {@code int}): schematic writers in the wild disagree about widths for the same field.
 */
public final class NbtCompound implements NbtTag {

    private final Map<String, NbtTag> tags;

    public NbtCompound() {
        this.tags = new LinkedHashMap<>();
    }

    private NbtCompound(Map<String, NbtTag> tags) {
        this.tags = tags;
    }

    public NbtCompound put(String key, NbtTag tag) {
        tags.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(tag, "tag"));
        return this;
    }

    public NbtCompound putByte(String key, byte value) {
        return put(key, new NbtByte(value));
    }

    public NbtCompound putShort(String key, short value) {
        return put(key, new NbtShort(value));
    }

    public NbtCompound putInt(String key, int value) {
        return put(key, new NbtInt(value));
    }

    public NbtCompound putLong(String key, long value) {
        return put(key, new NbtLong(value));
    }

    public NbtCompound putDouble(String key, double value) {
        return put(key, new NbtDouble(value));
    }

    public NbtCompound putString(String key, String value) {
        return put(key, new NbtString(value));
    }

    public NbtCompound putIntArray(String key, int... value) {
        return put(key, new NbtIntArray(value));
    }

    public NbtTag remove(String key) {
        return tags.remove(key);
    }

    public NbtTag get(String key) {
        return tags.get(key);
    }

    public boolean contains(String key) {
        return tags.containsKey(key);
    }

    public boolean contains(String key, byte type) {
        NbtTag tag = tags.get(key);
        return tag != null && tag.typeId() == type;
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public int size() {
        return tags.size();
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(tags.keySet());
    }

    public Set<Map.Entry<String, NbtTag>> entries() {
        return Collections.unmodifiableSet(tags.entrySet());
    }

    /** Any numeric tag widened to {@code long}; {@code fallback} when absent or non-numeric. */
    public long getLong(String key, long fallback) {
        NbtTag tag = tags.get(key);
        if (tag instanceof NbtByte value) {
            return value.value();
        }
        if (tag instanceof NbtShort value) {
            return value.value();
        }
        if (tag instanceof NbtInt value) {
            return value.value();
        }
        if (tag instanceof NbtLong value) {
            return value.value();
        }
        if (tag instanceof NbtFloat value) {
            return (long) value.value();
        }
        if (tag instanceof NbtDouble value) {
            return (long) value.value();
        }
        return fallback;
    }

    public int getInt(String key, int fallback) {
        return (int) getLong(key, fallback);
    }

    /**
     * Reads a field the Sponge spec defines as an <em>unsigned</em> short. Java has no
     * unsigned short, so writers store 40000 as -25536; masking restores the intent while
     * still accepting writers that (incorrectly but harmlessly) use TAG_Int.
     */
    public int getUnsignedShort(String key, int fallback) {
        NbtTag tag = tags.get(key);
        if (tag instanceof NbtShort shortTag) {
            return shortTag.value() & 0xFFFF;
        }
        return getInt(key, fallback);
    }

    public String getString(String key, String fallback) {
        return tags.get(key) instanceof NbtString tag ? tag.value() : fallback;
    }

    public NbtCompound getCompound(String key) {
        return tags.get(key) instanceof NbtCompound tag ? tag : null;
    }

    public NbtList getList(String key) {
        return tags.get(key) instanceof NbtList tag ? tag : null;
    }

    public byte[] getByteArray(String key) {
        return tags.get(key) instanceof NbtByteArray tag ? tag.value() : null;
    }

    /**
     * Three coordinates from either TAG_Int_Array or a TAG_List of numbers. Sponge mandates
     * an int array for {@code Pos}, but several exporters emit a list instead.
     */
    public int[] getIntTriple(String key) {
        NbtTag tag = tags.get(key);
        if (tag instanceof NbtIntArray array && array.length() >= 3) {
            return new int[]{array.get(0), array.get(1), array.get(2)};
        }
        if (tag instanceof NbtList list && list.size() >= 3) {
            double[] values = list.asDoubles();
            return new int[]{(int) values[0], (int) values[1], (int) values[2]};
        }
        return null;
    }

    /** Three coordinates from a TAG_List of doubles/floats, or an int array. */
    public double[] getDoubleTriple(String key) {
        NbtTag tag = tags.get(key);
        if (tag instanceof NbtList list && list.size() >= 3) {
            double[] values = list.asDoubles();
            return new double[]{values[0], values[1], values[2]};
        }
        if (tag instanceof NbtIntArray array && array.length() >= 3) {
            return new double[]{array.get(0), array.get(1), array.get(2)};
        }
        return null;
    }

    @Override
    public byte typeId() {
        return NbtType.COMPOUND;
    }

    @Override
    public NbtCompound copy() {
        Map<String, NbtTag> copies = new LinkedHashMap<>(Math.max(4, tags.size() * 2));
        for (Map.Entry<String, NbtTag> entry : tags.entrySet()) {
            copies.put(entry.getKey(), entry.getValue().copy());
        }
        return new NbtCompound(copies);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof NbtCompound compound && tags.equals(compound.tags));
    }

    @Override
    public int hashCode() {
        return tags.hashCode();
    }

    @Override
    public String toString() {
        return "NbtCompound" + tags.keySet();
    }
}
