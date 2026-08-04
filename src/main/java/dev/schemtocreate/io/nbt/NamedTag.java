package dev.schemtocreate.io.nbt;

import java.io.IOException;

/** An NBT file's single root tag together with its name. */
public record NamedTag(String name, NbtTag tag) {

    /** @throws NbtFormatException if the root is not a compound */
    public NbtCompound requireCompound() throws IOException {
        if (tag instanceof NbtCompound compound) {
            return compound;
        }
        throw new NbtFormatException("Root tag is " + NbtType.name(tag.typeId()) + ", expected TAG_Compound");
    }
}
