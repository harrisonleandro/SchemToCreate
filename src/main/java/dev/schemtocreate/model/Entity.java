package dev.schemtocreate.model;

import dev.schemtocreate.io.nbt.NbtCompound;

import java.util.Objects;

/**
 * An entity and its opaque payload.
 *
 * @param pos  position relative to the structure origin
 * @param id   namespaced entity type, e.g. {@code minecraft:armor_stand}
 * @param data payload without the {@code id} key
 */
public record Entity(Vec3d pos, String id, NbtCompound data) {

    public Entity {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(data, "data");
    }
}
