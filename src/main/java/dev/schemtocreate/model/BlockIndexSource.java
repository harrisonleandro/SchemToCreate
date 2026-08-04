package dev.schemtocreate.model;

import java.io.IOException;

/**
 * Re-openable supply of a structure's palette indices.
 *
 * <p>Modelled as a factory rather than a collection so the bulk block data never has to be
 * resident. An implementation may hold a byte array for a small schematic or re-read and
 * re-inflate a region of the source file for a large one; callers cannot tell the
 * difference and neither strategy changes their code.
 */
public interface BlockIndexSource {

    /** How many indices {@link #open} will yield. */
    long count();

    /** Opens a fresh cursor positioned at the first index. */
    BlockIndexCursor open() throws IOException;

    /** An empty source, for zero-volume structures. */
    static BlockIndexSource empty() {
        return new BlockIndexSource() {
            @Override
            public long count() {
                return 0;
            }

            @Override
            public BlockIndexCursor open() {
                return new BlockIndexCursor() {
                    @Override
                    public int next() throws IOException {
                        throw new java.io.EOFException("Empty block index source");
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };
    }
}
