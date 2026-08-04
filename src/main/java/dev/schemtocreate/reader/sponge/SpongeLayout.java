package dev.schemtocreate.reader.sponge;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtType;
import dev.schemtocreate.reader.SchematicFormatException;

/**
 * Resolves where each piece of a schematic lives, hiding the layout differences between
 * specification versions from the rest of the reader.
 *
 * <p>Version is taken from the {@code Version} field when present, but the actual shape of
 * the document wins: files in the wild carry stale or missing version numbers, while the
 * presence of a {@code Blocks} container is unambiguous.
 */
final class SpongeLayout {

    private final NbtCompound schematic;
    private final NbtCompound blocksContainer;
    private final String blockDataPath;
    private final SpongeVersion version;

    private SpongeLayout(NbtCompound schematic, NbtCompound blocksContainer,
                         String blockDataPath, SpongeVersion version) {
        this.schematic = schematic;
        this.blocksContainer = blocksContainer;
        this.blockDataPath = blockDataPath;
        this.version = version;
    }

    /**
     * @param root the file's root compound; v3 nests the schematic one level down
     */
    static SpongeLayout resolve(NbtCompound root) throws SchematicFormatException {
        NbtCompound nested = root.getCompound("Schematic");
        boolean isNested = nested != null && !nested.isEmpty();
        NbtCompound schematic = isNested ? nested : root;
        String prefix = isNested ? "Schematic/" : "";

        NbtCompound blocks = schematic.getCompound("Blocks");
        if (blocks != null) {
            return new SpongeLayout(schematic, blocks, prefix + "Blocks/Data", SpongeVersion.V3);
        }
        if (!schematic.contains("BlockData", NbtType.BYTE_ARRAY)) {
            throw new SchematicFormatException(
                    "No block data found: expected a 'Blocks' container (v3) or a 'BlockData' array (v1/v2)");
        }
        SpongeVersion declared = SpongeVersion.fromNumber(schematic.getInt("Version", 0));
        SpongeVersion legacy = declared != null && declared.isLegacyLayout()
                ? declared
                : (schematic.getList("TileEntities") != null ? SpongeVersion.V1 : SpongeVersion.V2);
        return new SpongeLayout(schematic, schematic, prefix + "BlockData", legacy);
    }

    SpongeVersion version() {
        return version;
    }

    NbtCompound schematic() {
        return schematic;
    }

    /** Path into the document for the varint block index array. */
    String blockDataPath() {
        return blockDataPath;
    }

    /**
     * The block state palette, {@code stateString -> index}.
     *
     * <p>v1 marks the palette optional: without one, the varints are pre-1.13 global block
     * IDs. Resolving those needs Minecraft's legacy ID table, which an offline tool cannot
     * have, so the case is reported rather than guessed at.
     */
    NbtCompound palette() throws SchematicFormatException {
        NbtCompound palette = blocksContainer.getCompound("Palette");
        if (palette == null) {
            throw new SchematicFormatException("Schematic has block data but no 'Palette'"
                    + (version == SpongeVersion.V1
                        ? "; a v1 schematic without a palette stores pre-1.13 numeric block IDs,"
                          + " which cannot be resolved without Minecraft's legacy ID table."
                          + " Re-save it with a modern WorldEdit first."
                        : ""));
        }
        return palette;
    }

    /** {@code PaletteMax} where the version defines it, otherwise -1. */
    int declaredPaletteMax() {
        return version == SpongeVersion.V3 ? -1 : schematic.getInt("PaletteMax", -1);
    }

    /** Never null; an absent list reads as empty. */
    NbtList blockEntities() {
        NbtList list = blocksContainer.getList("BlockEntities");
        if (list == null) {
            list = schematic.getList("BlockEntities");
        }
        if (list == null) {
            list = schematic.getList("TileEntities");
        }
        return list == null ? new NbtList() : list;
    }

    /** Never null; an absent list reads as empty. Entities stay at schematic level in v3. */
    NbtList entities() {
        NbtList list = schematic.getList("Entities");
        return list == null ? new NbtList() : list;
    }

    /** True when biome data is present; it has no counterpart in a vanilla structure. */
    boolean hasBiomes() {
        return schematic.getCompound("Biomes") != null || schematic.contains("BiomeData");
    }
}
