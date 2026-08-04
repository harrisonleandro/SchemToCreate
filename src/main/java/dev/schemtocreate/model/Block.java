package dev.schemtocreate.model;

import dev.schemtocreate.blockstate.BlockState;

/**
 * One positioned block, as produced while iterating a {@link Structure}.
 *
 * <p>Instances are transient by design: a structure with a hundred million blocks yields a
 * hundred million of these, one at a time, and none of them is retained.
 *
 * <p>Both the palette index and the resolved state are carried. The index is what a writer
 * needs — searching the palette for a matching state would turn every block into a linear
 * scan — while the state lets a consumer make decisions without a palette lookup of its own.
 *
 * @param pos         position relative to the structure origin
 * @param stateIndex  index into the structure's palette
 * @param state       resolved block state
 * @param blockEntity attached block entity, or {@code null}
 */
public record Block(BlockPos pos, int stateIndex, BlockState state, BlockEntity blockEntity) {

    public boolean hasBlockEntity() {
        return blockEntity != null;
    }
}
