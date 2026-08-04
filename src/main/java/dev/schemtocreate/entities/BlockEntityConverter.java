package dev.schemtocreate.entities;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.model.BlockEntity;

/**
 * Turns a model block entity into the {@code nbt} compound a vanilla structure attaches to
 * a block entry.
 *
 * <p>The one required change is the identifier key: Sponge writes {@code Id}, vanilla writes
 * {@code id} (this is what {@code BlockEntity.saveWithId} emits, and what
 * {@code StructureTemplate} feeds back to {@code BlockEntity.load} on placement). Everything
 * else — {@code Items}, {@code front_text}, {@code Bees}, {@code RecordItem},
 * {@code SpawnData}, {@code Lock}, {@code LootTable}, mod-defined keys — is copied verbatim,
 * so no block entity type needs to be known in advance.
 */
public final class BlockEntityConverter {

    private BlockEntityConverter() {
    }

    public static NbtCompound toStructureNbt(BlockEntity blockEntity) {
        return toStructureNbt(blockEntity, false).nbt();
    }

    /**
     * @param repairSignText apply {@link SignTextCompatibility}; see that class for why a
     *                       single unrepaired sign makes an entire schematic fail to place
     */
    public static Converted toStructureNbt(BlockEntity blockEntity, boolean repairSignText) {
        NbtCompound nbt = new NbtCompound();
        // Written first so the key order matches vanilla's own output.
        nbt.putString("id", blockEntity.id());
        for (var entry : blockEntity.data().entries()) {
            if (!"id".equals(entry.getKey())) {
                nbt.put(entry.getKey(), entry.getValue());
            }
        }
        int repairedLines = repairSignText && SignTextCompatibility.isSign(blockEntity.id())
                ? SignTextCompatibility.repair(nbt)
                : 0;
        return new Converted(nbt, repairedLines);
    }

    /**
     * @param nbt           the structure's {@code nbt} payload for this block
     * @param repairedLines sign lines rewritten for compatibility, 0 when nothing was touched
     */
    public record Converted(NbtCompound nbt, int repairedLines) {
    }
}
