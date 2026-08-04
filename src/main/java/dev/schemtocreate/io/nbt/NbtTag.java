package dev.schemtocreate.io.nbt;

/**
 * In-memory NBT value.
 *
 * <p>The tree representation is used only for the <em>small</em> parts of a schematic:
 * metadata, palettes, block-entity payloads and entity payloads. Bulk block data never
 * becomes a tag tree — see {@link NbtReader} and {@link NbtWriter} for the streaming path.
 */
public sealed interface NbtTag
        permits NbtByte, NbtShort, NbtInt, NbtLong, NbtFloat, NbtDouble,
                NbtByteArray, NbtString, NbtList, NbtCompound, NbtIntArray, NbtLongArray {

    /** The {@link NbtType} identifier of this tag. */
    byte typeId();

    /** Deep copy; mutable containers return an independent instance. */
    NbtTag copy();
}
