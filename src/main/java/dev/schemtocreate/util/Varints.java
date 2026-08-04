package dev.schemtocreate.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * LEB128-style variable length integers, as used by the Sponge Schematic block-data array.
 *
 * <p>The Sponge specification stores palette indices as unsigned varints inside a plain
 * {@code TAG_Byte_Array}; the array therefore has no fixed stride and can only be decoded
 * sequentially.
 */
public final class Varints {

    private static final int CONTINUATION_BIT = 0x80;
    private static final int VALUE_MASK = 0x7F;
    private static final int MAX_BYTES = 5;

    private Varints() {
    }

    /**
     * Reads one unsigned varint.
     *
     * @throws EOFException if the stream ends mid-value
     * @throws IOException  if the encoding exceeds five bytes (malformed input)
     */
    public static int read(InputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < MAX_BYTES; i++) {
            int b = in.read();
            if (b == -1) {
                throw new EOFException("Truncated varint after " + i + " byte(s)");
            }
            value |= (b & VALUE_MASK) << (i * 7);
            if ((b & CONTINUATION_BIT) == 0) {
                return value;
            }
        }
        throw new IOException("Varint longer than " + MAX_BYTES + " bytes");
    }

    /** Writes one unsigned varint. */
    public static void write(OutputStream out, int value) throws IOException {
        int remaining = value;
        while (true) {
            if ((remaining & ~VALUE_MASK) == 0) {
                out.write(remaining);
                return;
            }
            out.write((remaining & VALUE_MASK) | CONTINUATION_BIT);
            remaining >>>= 7;
        }
    }

    /** Number of bytes {@link #write} would emit for {@code value}. */
    public static int sizeOf(int value) {
        int size = 1;
        int remaining = value;
        while ((remaining & ~VALUE_MASK) != 0) {
            size++;
            remaining >>>= 7;
        }
        return size;
    }
}
