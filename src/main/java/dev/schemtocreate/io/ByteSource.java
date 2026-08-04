package dev.schemtocreate.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A re-openable run of bytes.
 *
 * <p>Lets a caller consume the same payload more than once without deciding where it lives.
 * The two implementations — an in-memory array and a region of a compressed file — are what
 * allow small and very large schematics to share one code path.
 */
public interface ByteSource {

    /** Length in bytes. */
    long length();

    /** Opens a stream over the whole run, from its first byte. */
    InputStream open() throws IOException;

    static ByteSource ofArray(byte[] bytes) {
        return new ByteSource() {
            @Override
            public long length() {
                return bytes.length;
            }

            @Override
            public InputStream open() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    static ByteSource empty() {
        return ofArray(new byte[0]);
    }
}
