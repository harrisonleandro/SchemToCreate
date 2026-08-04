package dev.schemtocreate.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Exposes at most {@code limit} bytes of the underlying stream, then reports end of stream. */
public final class BoundedInputStream extends FilterInputStream {

    private long remaining;

    public BoundedInputStream(InputStream in, long limit) {
        super(in);
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int value = in.read();
        if (value != -1) {
            remaining--;
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int read = in.read(buffer, offset, (int) Math.min(length, remaining));
        if (read > 0) {
            remaining -= read;
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = in.skip(Math.min(n, remaining));
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(in.available(), remaining);
    }
}
