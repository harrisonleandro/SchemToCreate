package dev.schemtocreate.writer;

/** What to do with {@code minecraft:structure_void} entries in the palette. */
public enum StructureVoidPolicy {

    /**
     * Rewrite to {@code minecraft:air}. This is what Create itself does when it saves a
     * schematic ({@code SchematicAndQuillItem.replaceStructureVoidWithAir}), so it is the
     * default: it produces the file Create would have produced for the same region.
     */
    AIR,

    /** Leave the entries untouched, for callers who use structure void deliberately. */
    KEEP
}
