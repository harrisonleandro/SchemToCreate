package dev.schemtocreate.writer;

import java.util.List;

/**
 * What a write actually produced.
 *
 * @param blocksWritten        entries in the {@code blocks} list
 * @param airBlocks            how many of those are air
 * @param blockEntitiesWritten block entity payloads attached to blocks
 * @param entitiesWritten      entries in the {@code entities} list
 * @param paletteSize          entries in the {@code palette} list
 * @param blockUsage           distinct block names and how often each is placed, commonest
 *                             first; the quickest way to spot blocks a target version lacks
 * @param repairedSignLines    sign lines rewritten into 1.20.1 JSON components
 * @param uncompressedBytes    NBT size before GZIP
 * @param compressedBytes      size of the file on disk
 */
public record WriteResult(long blocksWritten,
                          long airBlocks,
                          int blockEntitiesWritten,
                          int entitiesWritten,
                          int paletteSize,
                          List<BlockUsage> blockUsage,
                          int repairedSignLines,
                          long uncompressedBytes,
                          long compressedBytes) {

    /** One block type and how many times the structure places it. */
    public record BlockUsage(String name, long count) {
    }

    public long solidBlocks() {
        return blocksWritten - airBlocks;
    }

    public double compressionRatio() {
        return uncompressedBytes == 0 ? 1.0 : (double) compressedBytes / uncompressedBytes;
    }
}
