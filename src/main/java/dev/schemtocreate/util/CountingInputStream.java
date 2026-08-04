package dev.schemtocreate.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tracks how many bytes have been consumed from the underlying stream.
 *
 * <p>Placed as the <em>outermost</em> wrapper of a decompressing stream stack, the count
 * equals the logical offset inside the decompressed payload. That offset is what lets the
 * Sponge reader skip a multi-hundred-megabyte block-data array on the first pass and come
 * back to it on a second pass without ever holding it in memory.
 */
public final class CountingInputStream extends FilterInputStream {

    private long count;

    public CountingInputStream(InputStream in) {
        super(in);
    }

    /** Number of bytes consumed so far. */
    public long count() {
        return count;
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        if (b != -1) {
            count++;
        }
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = in.read(buffer, offset, length);
        if (read > 0) {
            count += read;
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = in.skip(n);
        if (skipped > 0) {
            count += skipped;
        }
        return skipped;
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}
