package dev.schemtocreate.writer;

import dev.schemtocreate.io.nbt.NbtByteArray;
import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtIntArray;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtLongArray;
import dev.schemtocreate.io.nbt.NbtString;
import dev.schemtocreate.io.nbt.NbtTag;
import dev.schemtocreate.model.BlockEntity;
import dev.schemtocreate.model.Entity;
import dev.schemtocreate.model.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Checks a converted file against the limits Create actually enforces.
 *
 * <p>Two separate ceilings apply, and neither is the file size on disk:
 *
 * <ul>
 *   <li>{@code SchematicItem.loadSchematic} parses with {@code new NbtAccounter(0x20000000L)},
 *       a 512 MiB budget. The accounter does not count bytes in the file — it charges a
 *       fixed cost per tag, so a {@code blocks} list costs roughly 220 bytes per entry
 *       whatever the file compresses to. This is what caps very large schematics.</li>
 *   <li>Uploading to a server is bounded by {@code maxTotalSchematicSize}, 256 KiB by
 *       default, applied to the file itself. Single player and local previews are not.</li>
 * </ul>
 *
 * <p>The per-tag costs below mirror the accounting in Minecraft 1.20.1's tag readers. They
 * are an estimate: the goal is to tell a user whether they are near a limit, not to predict
 * the accounter's final value to the byte.
 */
public final class CreateCompatibility {

    /** {@code new NbtAccounter(0x20000000L)} in {@code SchematicItem.loadSchematic}. */
    public static final long NBT_ACCOUNTER_BUDGET_BYTES = 0x20000000L;

    /** Create's {@code maxTotalSchematicSize} default, in bytes. */
    public static final long DEFAULT_SERVER_UPLOAD_LIMIT_BYTES = 256L * 1024;

    private static final long COMPOUND_BITS = 384;
    private static final long COMPOUND_ENTRY_BITS = 8 + 224;
    private static final long LIST_BITS = 296;
    private static final long LIST_ELEMENT_BITS = 32;
    private static final long INT_BITS = 96;
    private static final long DOUBLE_BITS = 128;
    private static final long STRING_BITS = 288;
    private static final long BITS_PER_CHAR = 16;

    /** Cost of one {@code blocks} entry without a block entity payload. */
    private static final long BLOCK_ENTRY_BITS =
            COMPOUND_BITS
                    + (COMPOUND_ENTRY_BITS + 3 * BITS_PER_CHAR) + LIST_BITS + 3 * LIST_ELEMENT_BITS + 3 * INT_BITS
                    + (COMPOUND_ENTRY_BITS + 5 * BITS_PER_CHAR) + INT_BITS
                    + 8;

    private CreateCompatibility() {
    }

    /**
     * @return the estimated number of bytes Create's {@code NbtAccounter} will charge
     */
    public static long estimateAccountedBytes(Structure structure, WriteResult result) {
        long bits = COMPOUND_BITS;
        bits += result.blocksWritten() * BLOCK_ENTRY_BITS;
        for (BlockEntity blockEntity : structure.blockEntities().values()) {
            bits += COMPOUND_ENTRY_BITS + 3 * BITS_PER_CHAR
                    + estimateBits(blockEntity.data())
                    + STRING_BITS + blockEntity.id().length() * BITS_PER_CHAR;
        }
        if (result.entitiesWritten() > 0) {
            for (Entity entity : structure.entities()) {
                bits += COMPOUND_BITS + 2 * LIST_BITS + 3 * (DOUBLE_BITS + INT_BITS)
                        + estimateBits(entity.data())
                        + STRING_BITS + entity.id().length() * BITS_PER_CHAR;
            }
        }
        bits += (long) result.paletteSize() * (COMPOUND_BITS + STRING_BITS + 4 * BITS_PER_CHAR);
        return bits / 8;
    }

    private static long estimateBits(NbtTag tag) {
        if (tag instanceof NbtCompound compound) {
            long bits = COMPOUND_BITS + 8;
            for (Map.Entry<String, NbtTag> entry : compound.entries()) {
                bits += COMPOUND_ENTRY_BITS + entry.getKey().length() * BITS_PER_CHAR
                        + estimateBits(entry.getValue());
            }
            return bits;
        }
        if (tag instanceof NbtList list) {
            long bits = LIST_BITS + (long) list.size() * LIST_ELEMENT_BITS;
            for (NbtTag element : list) {
                bits += estimateBits(element);
            }
            return bits;
        }
        if (tag instanceof NbtString string) {
            return STRING_BITS + string.value().length() * BITS_PER_CHAR;
        }
        if (tag instanceof NbtByteArray array) {
            return 192 + array.length() * 8L;
        }
        if (tag instanceof NbtIntArray array) {
            return 192 + array.length() * 32L;
        }
        if (tag instanceof NbtLongArray array) {
            return 192 + array.length() * 64L;
        }
        // Every remaining type is a fixed-width scalar; the widest is 64 bits of payload.
        return DOUBLE_BITS;
    }

    /** Human readable problems, most severe first. Empty means the file should load fine. */
    public static List<String> warnings(Structure structure, WriteResult result) {
        List<String> warnings = new ArrayList<>();
        long accounted = estimateAccountedBytes(structure, result);
        if (accounted > NBT_ACCOUNTER_BUDGET_BYTES) {
            warnings.add(String.format(Locale.ROOT,
                    "Estimated NBT budget %.1f MiB exceeds Create's 512 MiB parse limit; "
                            + "the Schematic Table will fail to load this file. "
                            + "Split the build into smaller schematics (roughly %,d blocks each).",
                    accounted / 1048576.0, NBT_ACCOUNTER_BUDGET_BYTES * 8 / BLOCK_ENTRY_BITS));
        } else if (accounted > NBT_ACCOUNTER_BUDGET_BYTES * 8 / 10) {
            warnings.add(String.format(Locale.ROOT,
                    "Estimated NBT budget %.1f MiB is within 20%% of Create's 512 MiB parse limit.",
                    accounted / 1048576.0));
        }
        if (result.compressedBytes() > DEFAULT_SERVER_UPLOAD_LIMIT_BYTES) {
            warnings.add(String.format(Locale.ROOT,
                    "File is %.1f KiB, above Create's default maxTotalSchematicSize of %d KiB. "
                            + "Single player is unaffected; uploading to a server needs that "
                            + "config raised in create-server.toml.",
                    result.compressedBytes() / 1024.0, DEFAULT_SERVER_UPLOAD_LIMIT_BYTES / 1024));
        }
        warnings.addAll(versionWarnings(structure.dataVersion()));
        return warnings;
    }

    /**
     * Version-gap advice.
     *
     * <p>A newer source is the more damaging direction and gets the sharper wording: blocks
     * that do not exist in the target simply become air when vanilla resolves the palette,
     * so a build using them arrives full of holes with nothing logged.
     */
    private static List<String> versionWarnings(int dataVersion) {
        if (dataVersion <= 0 || dataVersion == CreateWriterOptions.DATA_VERSION_1_20_1) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        String source = MinecraftVersions.describe(dataVersion);
        if (MinecraftVersions.isNewerThanTarget(dataVersion)) {
            warnings.add("This schematic was made in " + source
                    + ", which is newer than Minecraft 1.20.1. Any block that did not exist yet "
                    + "in the version you play becomes air when Create resolves the palette — "
                    + "silently, with nothing logged. Run with --list-palette to see every block "
                    + "the build uses and spot the ones your version does not have.");
        } else {
            warnings.add("This schematic was made in " + source
                    + ", which is older than Minecraft 1.20.1. Blocks renamed since then, and "
                    + "block entities whose shape changed (pre-1.20 sign text, for instance), "
                    + "will not load correctly. Re-save it once in 1.20.1 with WorldEdit.");
        }
        warnings.add("No data fixer is applied: Minecraft's migration rules ship with the game, "
                + "so an offline tool cannot run them.");
        return warnings;
    }
}
