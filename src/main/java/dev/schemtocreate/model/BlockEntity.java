package dev.schemtocreate.model;

import dev.schemtocreate.io.nbt.NbtCompound;

import java.util.Objects;

/**
 * A block entity and its opaque payload.
 *
 * <p>{@code data} is carried through untouched. Chests, barrels, furnaces, smokers, blast
 * furnaces, shulker boxes, hoppers, droppers, dispensers, signs, lecterns, beehives,
 * jukeboxes, respawn anchors, spawners and anything a mod defines all reduce to "an id and
 * a compound", so no per-type handling is needed or wanted — a whitelist would silently
 * drop whatever it did not know about.
 *
 * @param pos  position relative to the structure origin
 * @param id   namespaced block entity type, e.g. {@code minecraft:chest}
 * @param data payload without the {@code id}/{@code x}/{@code y}/{@code z} bookkeeping keys
 */
public record BlockEntity(BlockPos pos, String id, NbtCompound data) {

    public BlockEntity {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(data, "data");
    }
}
