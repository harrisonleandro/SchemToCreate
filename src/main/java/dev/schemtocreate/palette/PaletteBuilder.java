package dev.schemtocreate.palette;

import dev.schemtocreate.blockstate.BlockState;
import dev.schemtocreate.blockstate.BlockStateParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a sparse {@code stateString -> index} map (how Sponge stores its palette) into a
 * dense indexed {@link Palette} (how vanilla structures store theirs).
 *
 * <p>Sponge does not require indices to be contiguous. Any hole is filled with air, which
 * is the only safe choice: an index that appears in the block data but not in the palette
 * would otherwise have no meaning, and vanilla itself substitutes air for unresolvable
 * palette entries.
 */
public final class PaletteBuilder {

    private final List<BlockState> states = new ArrayList<>();
    private int holes;

    /**
     * @param mapping     palette entries, block state string to index
     * @param declaredMax {@code PaletteMax} from the file, or -1 when absent (Sponge v3)
     */
    public static PaletteBuilder fromSpongeMapping(Map<String, Integer> mapping, int declaredMax) {
        PaletteBuilder builder = new PaletteBuilder();
        int size = Math.max(declaredMax, highestIndex(mapping) + 1);
        builder.ensureCapacity(size);
        for (Map.Entry<String, Integer> entry : mapping.entrySet()) {
            int index = entry.getValue();
            if (index < 0) {
                throw new IllegalArgumentException(
                        "Negative palette index " + index + " for '" + entry.getKey() + "'");
            }
            builder.ensureCapacity(index + 1);
            builder.states.set(index, BlockStateParser.parse(entry.getKey()));
        }
        builder.countHoles();
        return builder;
    }

    private static int highestIndex(Map<String, Integer> mapping) {
        int max = -1;
        for (Integer index : mapping.values()) {
            max = Math.max(max, index);
        }
        return max;
    }

    private void ensureCapacity(int size) {
        while (states.size() < size) {
            states.add(null);
        }
    }

    private void countHoles() {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i) == null) {
                states.set(i, BlockState.AIR);
                holes++;
            }
        }
    }

    /** Number of indices that were absent from the source palette and became air. */
    public int holes() {
        return holes;
    }

    public Palette build() {
        return new ArrayPalette(states.toArray(new BlockState[0]));
    }
}
