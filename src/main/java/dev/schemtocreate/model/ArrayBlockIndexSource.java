package dev.schemtocreate.model;

import java.io.EOFException;

/** In-memory {@link BlockIndexSource}, used by tests and by programmatic callers. */
public final class ArrayBlockIndexSource implements BlockIndexSource {

    private final int[] indices;

    public ArrayBlockIndexSource(int... indices) {
        this.indices = indices.clone();
    }

    @Override
    public long count() {
        return indices.length;
    }

    @Override
    public BlockIndexCursor open() {
        return new BlockIndexCursor() {
            private int position;

            @Override
            public int next() throws EOFException {
                if (position >= indices.length) {
                    throw new EOFException("Exhausted after " + indices.length + " indices");
                }
                return indices[position++];
            }

            @Override
            public void close() {
            }
        };
    }
}
