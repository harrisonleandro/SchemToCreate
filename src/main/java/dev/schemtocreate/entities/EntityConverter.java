package dev.schemtocreate.entities;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.model.BlockPos;
import dev.schemtocreate.model.Entity;

/**
 * Turns a model entity into a vanilla structure {@code entities} entry.
 *
 * <p>A vanilla entry carries the position three times over: {@code pos} as doubles,
 * {@code blockPos} as the containing block, and {@code nbt.Pos} as the entity's own copy.
 * All three are written, because {@code StructureTemplate} reads {@code pos} for placement
 * while {@code EntityType.create} reads the payload, and a payload missing {@code Pos}
 * spawns some entity types at the world origin.
 *
 * <p>{@code UUID} is dropped deliberately: reusing one would make the first pasted copy of a
 * schematic collide with every later copy.
 */
public final class EntityConverter {

    private EntityConverter() {
    }

    /** The {@code nbt} payload, with {@code id} and a refreshed {@code Pos}. */
    public static NbtCompound toStructureNbt(Entity entity) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", entity.id());
        for (var field : entity.data().entries()) {
            String key = field.getKey();
            if (!"id".equals(key) && !"UUID".equals(key) && !"Pos".equals(key)) {
                nbt.put(key, field.getValue());
            }
        }
        nbt.put("Pos", NbtList.ofDoubles(entity.pos().x(), entity.pos().y(), entity.pos().z()));
        return nbt;
    }

    /** The block an entity is considered to occupy, as vanilla computes it. */
    public static BlockPos blockPos(Entity entity) {
        return entity.pos().containingBlock();
    }
}
