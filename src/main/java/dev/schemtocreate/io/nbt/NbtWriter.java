package dev.schemtocreate.io.nbt;

import dev.schemtocreate.util.CountingOutputStream;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Push-style NBT writer.
 *
 * <p>The API is split into <em>named</em> writes (legal inside a compound) and <em>payload</em>
 * writes (legal inside a list, where elements carry neither type nor name). That split is what
 * makes it possible to emit a list of a hundred million compounds: the caller declares the
 * element count up front with {@link #beginList} and then streams elements one at a time, so
 * no part of the list is ever resident in memory.
 *
 * <p>The writer performs no structural validation beyond the list contract; callers are
 * expected to pair every {@link #beginCompound} with an {@link #endCompound}.
 */
public final class NbtWriter implements Closeable, Flushable {

    private final DataOutputStream out;
    private final CountingOutputStream counter;

    public NbtWriter(OutputStream sink) {
        // DataOutputStream.size() is an int and saturates at 2 GiB, which a large structure
        // passes easily, so byte accounting uses a long counter placed against the sink.
        this.counter = new CountingOutputStream(sink);
        this.out = new DataOutputStream(new BufferedOutputStream(counter, 1 << 16));
    }

    // --- structure -------------------------------------------------------------------

    /** Writes the file header: root tag type plus its (usually empty) name. */
    public void beginRootCompound(String name) throws IOException {
        out.writeByte(NbtType.COMPOUND);
        out.writeUTF(name);
    }

    /** Opens a named child compound. */
    public void beginCompound(String name) throws IOException {
        writeHeader(name, NbtType.COMPOUND);
    }

    /** Terminates the compound currently being written. */
    public void endCompound() throws IOException {
        out.writeByte(NbtType.END);
    }

    /**
     * Opens a named list. Exactly {@code size} payloads of {@code elementType} must follow.
     * An empty list is written with element type {@code TAG_End}, matching vanilla.
     */
    public void beginList(String name, byte elementType, int size) throws IOException {
        writeHeader(name, NbtType.LIST);
        beginListPayload(elementType, size);
    }

    /** List header without a name, for a list nested directly inside another list. */
    public void beginListPayload(byte elementType, int size) throws IOException {
        out.writeByte(size <= 0 ? NbtType.END : elementType);
        out.writeInt(Math.max(size, 0));
    }

    private void writeHeader(String name, byte type) throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    // --- named scalars ---------------------------------------------------------------

    public void putByte(String name, byte value) throws IOException {
        writeHeader(name, NbtType.BYTE);
        out.writeByte(value);
    }

    public void putShort(String name, short value) throws IOException {
        writeHeader(name, NbtType.SHORT);
        out.writeShort(value);
    }

    public void putInt(String name, int value) throws IOException {
        writeHeader(name, NbtType.INT);
        out.writeInt(value);
    }

    public void putLong(String name, long value) throws IOException {
        writeHeader(name, NbtType.LONG);
        out.writeLong(value);
    }

    public void putString(String name, String value) throws IOException {
        writeHeader(name, NbtType.STRING);
        out.writeUTF(value);
    }

    public void putIntArray(String name, int... values) throws IOException {
        writeHeader(name, NbtType.INT_ARRAY);
        out.writeInt(values.length);
        for (int value : values) {
            out.writeInt(value);
        }
    }

    public void putByteArray(String name, byte[] values) throws IOException {
        writeHeader(name, NbtType.BYTE_ARRAY);
        out.writeInt(values.length);
        out.write(values);
    }

    /** Writes an arbitrary in-memory tag under {@code name}. */
    public void put(String name, NbtTag tag) throws IOException {
        writeHeader(name, tag.typeId());
        writePayload(tag);
    }

    /** Writes every entry of {@code compound} into the compound currently open. */
    public void putAll(NbtCompound compound) throws IOException {
        for (Map.Entry<String, NbtTag> entry : compound.entries()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    // --- bare payloads (list elements) ------------------------------------------------

    public void payloadInt(int value) throws IOException {
        out.writeInt(value);
    }

    public void payloadDouble(double value) throws IOException {
        out.writeDouble(value);
    }

    /** Writes a payload for any in-memory tag, without type byte or name. */
    public void writePayload(NbtTag tag) throws IOException {
        if (tag instanceof NbtByte value) {
            out.writeByte(value.value());
        } else if (tag instanceof NbtShort value) {
            out.writeShort(value.value());
        } else if (tag instanceof NbtInt value) {
            out.writeInt(value.value());
        } else if (tag instanceof NbtLong value) {
            out.writeLong(value.value());
        } else if (tag instanceof NbtFloat value) {
            out.writeFloat(value.value());
        } else if (tag instanceof NbtDouble value) {
            out.writeDouble(value.value());
        } else if (tag instanceof NbtString value) {
            out.writeUTF(value.value());
        } else if (tag instanceof NbtByteArray value) {
            writeByteArrayPayload(value);
        } else if (tag instanceof NbtIntArray value) {
            writeIntArrayPayload(value);
        } else if (tag instanceof NbtLongArray value) {
            writeLongArrayPayload(value);
        } else if (tag instanceof NbtList value) {
            writeListPayload(value);
        } else if (tag instanceof NbtCompound value) {
            writeCompoundPayload(value);
        } else {
            throw new IOException("Unsupported tag implementation: " + tag.getClass().getName());
        }
    }

    private void writeByteArrayPayload(NbtByteArray tag) throws IOException {
        out.writeInt(tag.length());
        out.write(tag.value());
    }

    private void writeIntArrayPayload(NbtIntArray tag) throws IOException {
        out.writeInt(tag.length());
        for (int value : tag.value()) {
            out.writeInt(value);
        }
    }

    private void writeLongArrayPayload(NbtLongArray tag) throws IOException {
        out.writeInt(tag.length());
        for (long value : tag.value()) {
            out.writeLong(value);
        }
    }

    private void writeListPayload(NbtList tag) throws IOException {
        beginListPayload(tag.elementType(), tag.size());
        for (NbtTag element : tag) {
            writePayload(element);
        }
    }

    private void writeCompoundPayload(NbtCompound tag) throws IOException {
        putAll(tag);
        endCompound();
    }

    /** Total NBT bytes produced so far, before any compression the sink applies. */
    public long bytesWritten() throws IOException {
        out.flush();
        return counter.count();
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
