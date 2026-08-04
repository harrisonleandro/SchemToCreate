package dev.schemtocreate.palette;

import dev.schemtocreate.blockstate.BlockState;

import java.util.List;

/**
 * Index to block state mapping.
 *
 * <p>Both formats store one palette per structure and refer to entries by integer index,
 * so the palette can be carried across the conversion untouched. The interface exists so a
 * future reader (Litematica, MCEdit, .nbt input) can supply its own implementation.
 */
public interface Palette {

    /** Number of entries; valid indices are {@code 0 .. size()-1}. */
    int size();

    /**
     * @throws IndexOutOfBoundsException if {@code index} is outside the palette
     */
    BlockState state(int index);

    /** All entries in index order. */
    List<BlockState> states();

    default boolean isEmpty() {
        return size() == 0;
    }
}
