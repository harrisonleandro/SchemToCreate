package dev.schemtocreate.model;

import dev.schemtocreate.io.nbt.NbtCompound;

/**
 * Descriptive fields that travel with a schematic.
 *
 * <p>Vanilla structures keep only {@code author}, so the rest is informational and is
 * surfaced in logs rather than written to the output.
 *
 * @param name    display name, or {@code null}
 * @param author  creator, or {@code null}
 * @param date    creation time in epoch milliseconds, or {@code null}
 * @param raw     the untouched source metadata compound, never {@code null}
 */
public record SchematicMetadata(String name, String author, Long date, NbtCompound raw) {

    public static SchematicMetadata empty() {
        return new SchematicMetadata(null, null, null, new NbtCompound());
    }
}
