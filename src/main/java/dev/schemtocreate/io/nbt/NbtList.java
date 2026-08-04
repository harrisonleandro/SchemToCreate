package dev.schemtocreate.io.nbt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * TAG_List. A list is homogeneous: every element shares {@link #elementType()}.
 * An empty list carries element type {@link NbtType#END}, matching vanilla's writer.
 */
public final class NbtList implements NbtTag, Iterable<NbtTag> {

    private final List<NbtTag> elements;
    private byte elementType;

    public NbtList() {
        this.elements = new ArrayList<>();
        this.elementType = NbtType.END;
    }

    public NbtList(byte elementType, List<NbtTag> elements) {
        this.elements = new ArrayList<>(elements);
        this.elementType = this.elements.isEmpty() ? NbtType.END : elementType;
    }

    public static NbtList ofDoubles(double... values) {
        NbtList list = new NbtList();
        for (double value : values) {
            list.add(new NbtDouble(value));
        }
        return list;
    }

    public static NbtList ofInts(int... values) {
        NbtList list = new NbtList();
        for (int value : values) {
            list.add(new NbtInt(value));
        }
        return list;
    }

    public static NbtList ofFloats(float... values) {
        NbtList list = new NbtList();
        for (float value : values) {
            list.add(new NbtFloat(value));
        }
        return list;
    }

    public NbtList add(NbtTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (elements.isEmpty()) {
            elementType = tag.typeId();
        } else if (tag.typeId() != elementType) {
            throw new IllegalArgumentException("Cannot add " + NbtType.name(tag.typeId())
                    + " to a list of " + NbtType.name(elementType));
        }
        elements.add(tag);
        return this;
    }

    public NbtTag get(int index) {
        return elements.get(index);
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public byte elementType() {
        return elementType;
    }

    public List<NbtTag> elements() {
        return Collections.unmodifiableList(elements);
    }

    /** Elements as compounds; entries of another type are skipped. */
    public List<NbtCompound> compounds() {
        List<NbtCompound> result = new ArrayList<>(elements.size());
        for (NbtTag tag : elements) {
            if (tag instanceof NbtCompound compound) {
                result.add(compound);
            }
        }
        return result;
    }

    /**
     * Numeric elements widened to {@code double}. Used for positions, which different
     * writers store as float, double or int lists.
     */
    public double[] asDoubles() {
        double[] result = new double[elements.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toDouble(elements.get(i));
        }
        return result;
    }

    private static double toDouble(NbtTag tag) {
        if (tag instanceof NbtDouble value) {
            return value.value();
        }
        if (tag instanceof NbtFloat value) {
            return value.value();
        }
        if (tag instanceof NbtInt value) {
            return value.value();
        }
        if (tag instanceof NbtLong value) {
            return value.value();
        }
        if (tag instanceof NbtShort value) {
            return value.value();
        }
        if (tag instanceof NbtByte value) {
            return value.value();
        }
        return 0.0;
    }

    @Override
    public Iterator<NbtTag> iterator() {
        return elements.iterator();
    }

    @Override
    public byte typeId() {
        return NbtType.LIST;
    }

    @Override
    public NbtList copy() {
        List<NbtTag> copies = new ArrayList<>(elements.size());
        for (NbtTag tag : elements) {
            copies.add(tag.copy());
        }
        return new NbtList(elementType, copies);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof NbtList list
                    && elementType == list.elementType
                    && elements.equals(list.elements));
    }

    @Override
    public int hashCode() {
        return 31 * elements.hashCode() + elementType;
    }

    @Override
    public String toString() {
        return "NbtList[" + NbtType.name(elementType) + " x" + elements.size() + "]";
    }
}
