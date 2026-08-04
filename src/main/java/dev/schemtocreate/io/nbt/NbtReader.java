package dev.schemtocreate.io.nbt;

import dev.schemtocreate.util.CountingInputStream;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Pull-style NBT reader.
 *
 * <p>Two modes coexist:
 * <ul>
 *   <li>{@link #readPayload} materialises a value as a {@link NbtTag} tree — used for the
 *       small parts of a schematic;</li>
 *   <li>{@link #skipPayload} consumes a value without allocating, and {@link #bytesConsumed}
 *       reports the exact offset reached — together they let a caller step over a bulk
 *       array and revisit it later from a second pass.</li>
 * </ul>
 *
 * <p>The instance is not thread safe; one reader belongs to one stream.
 */
public final class NbtReader implements Closeable {

    private final CountingInputStream counter;
    private final DataInputStream in;
    private final NbtLimits limits;

    public NbtReader(InputStream source) {
        this(source, NbtLimits.DEFAULT);
    }

    public NbtReader(InputStream source, NbtLimits limits) {
        // The counter sits outside the buffer so its count tracks bytes the parser has
        // actually consumed rather than bytes pre-fetched into the buffer.
        this.counter = new CountingInputStream(new BufferedInputStream(source, 1 << 16));
        this.in = new DataInputStream(counter);
        this.limits = limits;
    }

    /** Offset, in decompressed bytes, of the parser's current position. */
    public long bytesConsumed() {
        return counter.count();
    }

    /** Reads the file's root tag together with its name. */
    public NamedTag readRoot() throws IOException {
        byte type = readTypeId();
        if (type == NbtType.END) {
            throw new NbtFormatException("Empty NBT stream: root tag is TAG_End");
        }
        String name = readName();
        return new NamedTag(name, readPayload(type));
    }

    public byte readTypeId() throws IOException {
        byte type = in.readByte();
        if (!NbtType.isValid(type)) {
            throw new NbtFormatException("Unknown NBT tag type " + type
                    + " at offset " + (bytesConsumed() - 1));
        }
        return type;
    }

    public String readName() throws IOException {
        return in.readUTF();
    }

    public byte readByteValue() throws IOException {
        return in.readByte();
    }

    public short readShortValue() throws IOException {
        return in.readShort();
    }

    public int readIntValue() throws IOException {
        return in.readInt();
    }

    public long readLongValue() throws IOException {
        return in.readLong();
    }

    public String readStringValue() throws IOException {
        return in.readUTF();
    }

    /** Skips {@code count} raw bytes, keeping {@link #bytesConsumed} accurate. */
    public void skipBytes(long count) throws IOException {
        in.skipNBytes(count);
    }

    /** Fills {@code target} completely, or fails. */
    public void readFully(byte[] target) throws IOException {
        in.readFully(target);
    }

    /** Materialises one payload of the given type. */
    public NbtTag readPayload(byte type) throws IOException {
        return readPayload(type, 0);
    }

    private NbtTag readPayload(byte type, int depth) throws IOException {
        if (depth > limits.maxDepth()) {
            throw new NbtFormatException("NBT nesting deeper than " + limits.maxDepth());
        }
        return switch (type) {
            case NbtType.BYTE -> new NbtByte(in.readByte());
            case NbtType.SHORT -> new NbtShort(in.readShort());
            case NbtType.INT -> new NbtInt(in.readInt());
            case NbtType.LONG -> new NbtLong(in.readLong());
            case NbtType.FLOAT -> new NbtFloat(in.readFloat());
            case NbtType.DOUBLE -> new NbtDouble(in.readDouble());
            case NbtType.STRING -> new NbtString(in.readUTF());
            case NbtType.BYTE_ARRAY -> readByteArray();
            case NbtType.INT_ARRAY -> readIntArray();
            case NbtType.LONG_ARRAY -> readLongArray();
            case NbtType.LIST -> readList(depth);
            case NbtType.COMPOUND -> readCompound(depth);
            default -> throw new NbtFormatException("Cannot read payload of " + NbtType.name(type));
        };
    }

    private NbtByteArray readByteArray() throws IOException {
        byte[] data = new byte[checkedLength(in.readInt(), 1)];
        in.readFully(data);
        return new NbtByteArray(data);
    }

    private NbtIntArray readIntArray() throws IOException {
        int[] data = new int[checkedLength(in.readInt(), 4)];
        for (int i = 0; i < data.length; i++) {
            data[i] = in.readInt();
        }
        return new NbtIntArray(data);
    }

    private NbtLongArray readLongArray() throws IOException {
        long[] data = new long[checkedLength(in.readInt(), 8)];
        for (int i = 0; i < data.length; i++) {
            data[i] = in.readLong();
        }
        return new NbtLongArray(data);
    }

    private NbtList readList(int depth) throws IOException {
        byte elementType = in.readByte();
        int size = in.readInt();
        if (size <= 0) {
            return new NbtList();
        }
        if (!NbtType.isValid(elementType) || elementType == NbtType.END) {
            throw new NbtFormatException("TAG_List of " + size
                    + " elements declares element type " + NbtType.name(elementType));
        }
        checkedLength(size, 1);
        List<NbtTag> elements = new ArrayList<>(Math.min(size, 1024));
        for (int i = 0; i < size; i++) {
            elements.add(readPayload(elementType, depth + 1));
        }
        return new NbtList(elementType, elements);
    }

    private NbtCompound readCompound(int depth) throws IOException {
        NbtCompound compound = new NbtCompound();
        byte type;
        while ((type = readTypeId()) != NbtType.END) {
            compound.put(in.readUTF(), readPayload(type, depth + 1));
        }
        return compound;
    }

    /** Consumes one payload without allocating it. */
    public void skipPayload(byte type) throws IOException {
        skipPayload(type, 0);
    }

    private void skipPayload(byte type, int depth) throws IOException {
        if (depth > limits.maxDepth()) {
            throw new NbtFormatException("NBT nesting deeper than " + limits.maxDepth());
        }
        switch (type) {
            case NbtType.BYTE -> in.skipNBytes(1);
            case NbtType.SHORT -> in.skipNBytes(2);
            case NbtType.INT, NbtType.FLOAT -> in.skipNBytes(4);
            case NbtType.LONG, NbtType.DOUBLE -> in.skipNBytes(8);
            case NbtType.STRING -> in.skipNBytes(in.readUnsignedShort());
            case NbtType.BYTE_ARRAY -> in.skipNBytes(unsignedLength(in.readInt()));
            case NbtType.INT_ARRAY -> in.skipNBytes(unsignedLength(in.readInt()) * 4L);
            case NbtType.LONG_ARRAY -> in.skipNBytes(unsignedLength(in.readInt()) * 8L);
            case NbtType.LIST -> skipList(depth);
            case NbtType.COMPOUND -> skipCompound(depth);
            default -> throw new NbtFormatException("Cannot skip payload of " + NbtType.name(type));
        }
    }

    private void skipList(int depth) throws IOException {
        byte elementType = in.readByte();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            skipPayload(elementType, depth + 1);
        }
    }

    private void skipCompound(int depth) throws IOException {
        byte type;
        while ((type = readTypeId()) != NbtType.END) {
            in.skipNBytes(in.readUnsignedShort());
            skipPayload(type, depth + 1);
        }
    }

    private int checkedLength(int length, int bytesPerElement) throws NbtFormatException {
        if (length < 0) {
            throw new NbtFormatException("Negative array length " + length);
        }
        if (length > limits.maxArrayLength()) {
            throw new NbtFormatException("Array of " + length + " elements ("
                    + (long) length * bytesPerElement + " bytes) exceeds the limit of "
                    + limits.maxArrayLength());
        }
        return length;
    }

    private static long unsignedLength(int length) throws NbtFormatException {
        if (length < 0) {
            throw new NbtFormatException("Negative array length " + length);
        }
        return length;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
