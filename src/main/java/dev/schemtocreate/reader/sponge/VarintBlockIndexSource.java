package dev.schemtocreate.reader.sponge;

import dev.schemtocreate.io.ByteSource;
import dev.schemtocreate.model.BlockIndexCursor;
import dev.schemtocreate.model.BlockIndexSource;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes the Sponge block-data array: unsigned varint palette indices packed back to back
 * in YZX order.
 */
public final class VarintBlockIndexSource implements BlockIndexSource {

    private final ByteSource bytes;
    private final long count;

    public VarintBlockIndexSource(ByteSource bytes, long count) {
        this.bytes = bytes;
        this.count = count;
    }

    @Override
    public long count() {
        return count;
    }

    /** Encoded size in bytes, useful for progress reporting. */
    public long encodedLength() {
        return bytes.length();
    }

    @Override
    public BlockIndexCursor open() throws IOException {
        return new Cursor(bytes.open());
    }

    /**
     * Buffers internally rather than wrapping a {@code BufferedInputStream}: at a hundred
     * million indices the per-byte virtual call through a stream chain is the dominant
     * cost, and the single-byte fast path below covers every palette under 128 entries.
     */
    private static final class Cursor implements BlockIndexCursor {

        private static final int BUFFER_SIZE = 1 << 16;

        private final InputStream in;
        private final byte[] buffer = new byte[BUFFER_SIZE];
        private int position;
        private int limit;

        Cursor(InputStream in) {
            this.in = in;
        }

        @Override
        public int next() throws IOException {
            int first = nextByte();
            if (first < 0x80) {
                return first;
            }
            int value = first & 0x7F;
            for (int shift = 7; shift <= 28; shift += 7) {
                int b = nextByte();
                value |= (b & 0x7F) << shift;
                if (b < 0x80) {
                    return value;
                }
            }
            throw new IOException("Varint longer than 5 bytes in block data");
        }

        private int nextByte() throws IOException {
            if (position >= limit) {
                fill();
            }
            return buffer[position++] & 0xFF;
        }

        private void fill() throws IOException {
            position = 0;
            limit = 0;
            while (limit == 0) {
                int read = in.read(buffer, 0, BUFFER_SIZE);
                if (read < 0) {
                    throw new EOFException("Block data ended before all indices were decoded");
                }
                limit = read;
            }
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
