package dev.schemtocreate.reader.sponge;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtTag;

import java.util.Map;
import java.util.Set;

/**
 * Extracts the payload compound of a block entity or entity entry.
 *
 * <p>v3 nests it under {@code Data}; v1 and v2 splice the same keys directly into the entry
 * alongside {@code Id} and {@code Pos}. Both are reduced to "the payload, minus bookkeeping",
 * which is exactly what a vanilla structure's {@code nbt} field holds.
 */
final class SpongePayloads {

    /** Keys that describe the entry itself rather than the object it carries. */
    private static final Set<String> ENVELOPE_KEYS = Set.of(
            "Id", "id", "Pos", "pos", "Data", "x", "y", "z");

    private SpongePayloads() {
    }

    /** Reads the identifier, tolerating writers that use a lowercase key. */
    static String identifier(NbtCompound entry) {
        String id = entry.getString("Id", null);
        return id != null ? id : entry.getString("id", null);
    }

    /**
     * @return the payload as an independent compound, never {@code null}
     */
    static NbtCompound payload(NbtCompound entry, SpongeVersion version) {
        if (version == SpongeVersion.V3) {
            NbtCompound data = entry.getCompound("Data");
            return data == null ? new NbtCompound() : stripEnvelope(data.copy());
        }
        return stripEnvelope(inlinePayload(entry));
    }

    private static NbtCompound inlinePayload(NbtCompound entry) {
        NbtCompound payload = new NbtCompound();
        for (Map.Entry<String, NbtTag> field : entry.entries()) {
            if (!ENVELOPE_KEYS.contains(field.getKey())) {
                payload.put(field.getKey(), field.getValue().copy());
            }
        }
        return payload;
    }

    /**
     * Removes positional bookkeeping. Vanilla structures store block entity positions in the
     * enclosing block entry, and re-derive {@code x}/{@code y}/{@code z} on placement, so a
     * stale copy inside the payload would place the block entity at its original world
     * coordinates.
     */
    private static NbtCompound stripEnvelope(NbtCompound payload) {
        payload.remove("id");
        payload.remove("Id");
        payload.remove("x");
        payload.remove("y");
        payload.remove("z");
        return payload;
    }
}
