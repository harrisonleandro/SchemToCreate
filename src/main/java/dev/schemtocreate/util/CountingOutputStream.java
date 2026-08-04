package dev.schemtocreate.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Counts bytes written, so compressed output size is known without re-stat-ing the file. */
public final class CountingOutputStream extends FilterOutputStream {

    private long count;

    public CountingOutputStream(OutputStream out) {
        super(out);
    }

    public long count() {
        return count;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        count++;
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        // FilterOutputStream's default forwards byte by byte; delegating in bulk matters
        // for a stream that carries hundreds of megabytes.
        out.write(buffer, offset, length);
        count += length;
    }
}
