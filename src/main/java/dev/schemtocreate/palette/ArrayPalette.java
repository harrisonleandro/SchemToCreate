package dev.schemtocreate.palette;

import dev.schemtocreate.blockstate.BlockState;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Dense array backed {@link Palette}. */
public final class ArrayPalette implements Palette {

    private final BlockState[] states;

    public ArrayPalette(BlockState[] states) {
        this.states = Objects.requireNonNull(states, "states").clone();
        for (int i = 0; i < this.states.length; i++) {
            Objects.requireNonNull(this.states[i], "palette entry " + i);
        }
    }

    public static ArrayPalette of(BlockState... states) {
        return new ArrayPalette(states);
    }

    @Override
    public int size() {
        return states.length;
    }

    @Override
    public BlockState state(int index) {
        if (index < 0 || index >= states.length) {
            throw new IndexOutOfBoundsException(
                    "Palette index " + index + " outside 0.." + (states.length - 1));
        }
        return states[index];
    }

    @Override
    public List<BlockState> states() {
        return List.of(states);
    }

    @Override
    public String toString() {
        return "ArrayPalette[" + states.length + " entries]";
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof ArrayPalette palette && Arrays.equals(states, palette.states));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(states);
    }
}
