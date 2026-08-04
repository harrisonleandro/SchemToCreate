package dev.schemtocreate.model;

import java.io.Closeable;
import java.io.IOException;

/**
 * Forward-only iterator over palette indices in Sponge/vanilla YZX order.
 *
 * <p>Sequential access is not a limitation of the implementation but of the data: Sponge
 * encodes indices as varints, which have no fixed stride, so position {@code n} cannot be
 * located without decoding everything before it. Every consumer is therefore built around
 * a single forward pass.
 */
public interface BlockIndexCursor extends Closeable {

    /**
     * @return the next palette index
     * @throws java.io.EOFException if the source ends before {@link BlockIndexSource#count()}
     *                              indices were produced
     */
    int next() throws IOException;
}
