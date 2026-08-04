package dev.schemtocreate.writer;

import java.util.Map;

/**
 * Knobs for {@link CreateStructureWriter}.
 *
 * @param includeEntities     write the {@code entities} list
 * @param skipAir             omit air blocks from the {@code blocks} list; costs an extra
 *                            counting pass because a list declares its length up front
 * @param structureVoid       how to treat {@code minecraft:structure_void}
 * @param compressionLevel    GZIP level, 1-9
 * @param author              value for the optional {@code author} field, or {@code null}
 * @param dataVersionOverride explicit {@code DataVersion}, or {@code null} to use the source's
 * @param blockReplacements   palette substitutions by block name, {@code from -> to}
 * @param repairSignText      rewrite 1.21.5+ sign text into the JSON components 1.20.1 needs
 */
public record CreateWriterOptions(boolean includeEntities,
                                  boolean skipAir,
                                  StructureVoidPolicy structureVoid,
                                  int compressionLevel,
                                  String author,
                                  Integer dataVersionOverride,
                                  Map<String, String> blockReplacements,
                                  boolean repairSignText) {

    /** Minecraft 1.20.1, the version Create 6.0.8 targets. */
    public static final int DATA_VERSION_1_20_1 = 3465;

    public CreateWriterOptions {
        blockReplacements = Map.copyOf(blockReplacements);
    }

    /**
     * Sign repair is on by default. It only ever touches text that 1.20.1 cannot parse, and
     * leaving it off produces a file that aborts placement of the whole structure — a much
     * worse outcome than a byte-for-byte faithful copy is worth.
     */
    public static CreateWriterOptions defaults() {
        return new CreateWriterOptions(true, false, StructureVoidPolicy.AIR, 6, null, null,
                Map.of(), true);
    }

    public CreateWriterOptions withIncludeEntities(boolean value) {
        return new CreateWriterOptions(value, skipAir, structureVoid, compressionLevel, author,
                dataVersionOverride, blockReplacements, repairSignText);
    }

    public CreateWriterOptions withSkipAir(boolean value) {
        return new CreateWriterOptions(includeEntities, value, structureVoid, compressionLevel, author,
                dataVersionOverride, blockReplacements, repairSignText);
    }

    public CreateWriterOptions withStructureVoid(StructureVoidPolicy value) {
        return new CreateWriterOptions(includeEntities, skipAir, value, compressionLevel, author,
                dataVersionOverride, blockReplacements, repairSignText);
    }

    public CreateWriterOptions withCompressionLevel(int value) {
        return new CreateWriterOptions(includeEntities, skipAir, structureVoid, value, author,
                dataVersionOverride, blockReplacements, repairSignText);
    }

    public CreateWriterOptions withAuthor(String value) {
        return new CreateWriterOptions(includeEntities, skipAir, structureVoid, compressionLevel, value,
                dataVersionOverride, blockReplacements, repairSignText);
    }

    public CreateWriterOptions withDataVersionOverride(Integer value) {
        return new CreateWriterOptions(includeEntities, skipAir, structureVoid, compressionLevel, author,
                value, blockReplacements, repairSignText);
    }

    /**
     * Substitutions applied to the palette, {@code from -> to} by block name.
     *
     * <p>The use case is a build made in a newer Minecraft than the one Create runs on:
     * blocks that do not exist there resolve to air with nothing logged, so mapping them to
     * an equivalent that does exist is the difference between a build with holes and a build
     * that prints.
     */
    public CreateWriterOptions withBlockReplacements(Map<String, String> value) {
        return new CreateWriterOptions(includeEntities, skipAir, structureVoid, compressionLevel, author,
                dataVersionOverride, value, repairSignText);
    }

    public CreateWriterOptions withRepairSignText(boolean value) {
        return new CreateWriterOptions(includeEntities, skipAir, structureVoid, compressionLevel, author,
                dataVersionOverride, blockReplacements, value);
    }

    /** The {@code DataVersion} to write: the override, else the source's, else 1.20.1. */
    public int resolveDataVersion(int sourceDataVersion) {
        if (dataVersionOverride != null) {
            return dataVersionOverride;
        }
        return sourceDataVersion > 0 ? sourceDataVersion : DATA_VERSION_1_20_1;
    }
}
