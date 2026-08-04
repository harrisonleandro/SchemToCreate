package dev.schemtocreate.writer;

import dev.schemtocreate.blockstate.BlockState;
import dev.schemtocreate.entities.BlockEntityConverter;
import dev.schemtocreate.entities.EntityConverter;
import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtIo;
import dev.schemtocreate.io.nbt.NbtType;
import dev.schemtocreate.io.nbt.NbtWriter;
import dev.schemtocreate.model.Block;
import dev.schemtocreate.model.BlockPos;
import dev.schemtocreate.model.Entity;
import dev.schemtocreate.model.Region;
import dev.schemtocreate.model.Structure;
import dev.schemtocreate.palette.ArrayPalette;
import dev.schemtocreate.palette.Palette;
import dev.schemtocreate.util.CountingOutputStream;
import dev.schemtocreate.util.ProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the format Create 6.0.8 reads.
 *
 * <p>Create does not define a schematic format of its own. {@code SchematicItem.loadSchematic}
 * opens the file with a {@code GZIPInputStream}, parses it with {@code NbtIo.read}, and hands
 * the result to vanilla {@code StructureTemplate.load} — the same code path a structure block
 * uses. The target is therefore a GZIP compressed vanilla structure file:
 *
 * <pre>
 * root (TAG_Compound, unnamed)
 *   size        TAG_List of 3 TAG_Int          width, height, length
 *   palette     TAG_List of TAG_Compound       { Name, Properties? }
 *   blocks      TAG_List of TAG_Compound       { pos: 3 TAG_Int, state: TAG_Int, nbt? }
 *   entities    TAG_List of TAG_Compound       { pos: 3 TAG_Double, blockPos: 3 TAG_Int, nbt }
 *   DataVersion TAG_Int
 *   author      TAG_String                     optional
 * </pre>
 *
 * <p>The {@code blocks} list is emitted incrementally: its length is known from the region
 * before any block is read, so entries stream straight from the reader to the GZIP sink and
 * memory stays flat regardless of block count.
 */
public final class CreateStructureWriter implements StructureWriter {

    private static final Logger LOG = LoggerFactory.getLogger(CreateStructureWriter.class);

    private final CreateWriterOptions options;

    public CreateStructureWriter() {
        this(CreateWriterOptions.defaults());
    }

    public CreateStructureWriter(CreateWriterOptions options) {
        this.options = options;
    }

    @Override
    public String formatName() {
        return "Create / vanilla structure";
    }

    @Override
    public String fileExtension() {
        return "nbt";
    }

    @Override
    public WriteResult write(Structure structure, Path target, ProgressListener progress) throws IOException {
        Palette palette = applyPaletteRewrites(structure.palette());
        long blockCount = options.skipAir()
                ? countWrittenBlocks(structure, palette)
                : structure.region().volume();
        requireListSize(blockCount, structure.region());

        Counters counters = new Counters(palette.size());
        CountingOutputStream compressed = new CountingOutputStream(Files.newOutputStream(target));
        long uncompressed;
        // `owned` closes the file handle even if opening the GZIP sink fails; `compressed`
        // stays in scope afterwards so its byte count can be read.
        try (CountingOutputStream owned = compressed;
             OutputStream gzip = NbtIo.gzipSink(owned, options.compressionLevel());
             NbtWriter writer = new NbtWriter(gzip)) {
            writer.beginRootCompound("");
            writeSize(writer, structure.region());
            writePalette(writer, palette);
            writeBlocks(writer, structure, palette, (int) blockCount, counters, progress);
            writeEntities(writer, structure, counters);
            writer.putInt("DataVersion", options.resolveDataVersion(structure.dataVersion()));
            if (options.author() != null) {
                writer.putString("author", options.author());
            }
            writer.endCompound();
            writer.flush();
            uncompressed = writer.bytesWritten();
        }

        if (counters.repairedSignLines > 0) {
            LOG.info("  Rewrote {} sign line(s) into 1.20.1 JSON components; without this the "
                    + "whole structure fails to place", counters.repairedSignLines);
        }
        return new WriteResult(blockCount, counters.air, counters.blockEntities, counters.entities,
                palette.size(), summariseUsage(palette, counters.usagePerPaletteIndex),
                counters.repairedSignLines, uncompressed, compressed.count());
    }

    /**
     * Collapses per-index tallies into per-block-name totals, commonest first.
     *
     * <p>Counting by index costs one array increment per block; folding to names happens once
     * at the end, over the palette rather than over the blocks.
     */
    private static List<WriteResult.BlockUsage> summariseUsage(Palette palette, long[] usage) {
        Map<String, Long> totals = new HashMap<>();
        for (int index = 0; index < usage.length && index < palette.size(); index++) {
            if (usage[index] > 0) {
                totals.merge(palette.state(index).name(), usage[index], Long::sum);
            }
        }
        return totals.entrySet().stream()
                .map(entry -> new WriteResult.BlockUsage(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(WriteResult.BlockUsage::count).reversed()
                        .thenComparing(WriteResult.BlockUsage::name))
                .toList();
    }

    /** A TAG_List declares its length as a signed int, which caps a structure at ~2.1G blocks. */
    private static void requireListSize(long blockCount, Region region) throws IOException {
        if (blockCount > Integer.MAX_VALUE) {
            throw new IOException("Structure has " + blockCount + " blocks (" + region
                    + "); an NBT list cannot hold more than " + Integer.MAX_VALUE + " entries");
        }
    }

    /**
     * Applies every palette-level rewrite in one pass.
     *
     * <p>Rewriting the palette rather than each block means substitutions cost nothing at
     * write time and leave block indices identical to the source's, so nothing downstream
     * has to know a rewrite happened.
     */
    private Palette applyPaletteRewrites(Palette source) {
        BlockState[] states = source.states().toArray(new BlockState[0]);
        int voids = 0;
        int substituted = 0;
        for (int i = 0; i < states.length; i++) {
            if (options.structureVoid() == StructureVoidPolicy.AIR && states[i].isStructureVoid()) {
                states[i] = BlockState.AIR;
                voids++;
                continue;
            }
            String replacement = options.blockReplacements().get(states[i].name());
            if (replacement != null) {
                // Properties are carried over: a stripped log swapped for another stripped log
                // keeps its axis, a slab keeps its type. Properties the new block does not
                // have are dropped by Minecraft when it resolves the state.
                states[i] = states[i].withName(replacement);
                substituted++;
            }
        }
        if (voids > 0) {
            LOG.debug("Replaced {} structure_void palette entr(ies) with air, matching Create's own saver", voids);
        }
        if (substituted > 0) {
            LOG.info("  Substituted {} palette entr(ies) via --replace", substituted);
        }
        return voids + substituted == 0 ? source : new ArrayPalette(states);
    }

    private static void writeSize(NbtWriter writer, Region region) throws IOException {
        writer.beginList("size", NbtType.INT, 3);
        writer.payloadInt(region.width());
        writer.payloadInt(region.height());
        writer.payloadInt(region.length());
    }

    private static void writePalette(NbtWriter writer, Palette palette) throws IOException {
        writer.beginList("palette", NbtType.COMPOUND, palette.size());
        for (BlockState state : palette.states()) {
            writer.putString("Name", state.name());
            if (state.hasProperties()) {
                writer.beginCompound("Properties");
                for (Map.Entry<String, String> property : state.properties().entrySet()) {
                    writer.putString(property.getKey(), property.getValue());
                }
                writer.endCompound();
            }
            writer.endCompound();
        }
    }

    private void writeBlocks(NbtWriter writer, Structure structure, Palette palette,
                             int blockCount, Counters counters, ProgressListener progress)
            throws IOException {
        writer.beginList("blocks", NbtType.COMPOUND, blockCount);
        long total = structure.region().volume();
        long[] visited = {0};
        long[] lastReported = {-1};
        structure.forEachBlock(block -> {
            // Structure-void rewriting preserves indices, so the block's own index still
            // addresses the right entry in the possibly-rewritten palette.
            boolean air = palette.state(block.stateIndex()).isAir();
            counters.usagePerPaletteIndex[block.stateIndex()]++;
            if (!options.skipAir() || !air) {
                writeBlockEntry(writer, block, counters);
            }
            if (air) {
                counters.air++;
            }
            if ((++visited[0] & 0xFFFF) == 0) {
                progress.onProgress(visited[0], total);
                lastReported[0] = visited[0];
            }
        });
        // Only if the loop did not already land exactly on the total, so listeners see a
        // strictly increasing sequence ending at `total` exactly once.
        if (lastReported[0] != total) {
            progress.onProgress(total, total);
        }
    }

    private void writeBlockEntry(NbtWriter writer, Block block, Counters counters)
            throws IOException {
        BlockPos pos = block.pos();
        writer.beginList("pos", NbtType.INT, 3);
        writer.payloadInt(pos.x());
        writer.payloadInt(pos.y());
        writer.payloadInt(pos.z());
        writer.putInt("state", block.stateIndex());
        if (block.hasBlockEntity()) {
            var converted = BlockEntityConverter.toStructureNbt(
                    block.blockEntity(), options.repairSignText());
            writer.put("nbt", converted.nbt());
            counters.blockEntities++;
            counters.repairedSignLines += converted.repairedLines();
        }
        writer.endCompound();
    }

    private void writeEntities(NbtWriter writer, Structure structure, Counters counters) throws IOException {
        if (!options.includeEntities() || structure.entities().isEmpty()) {
            writer.beginList("entities", NbtType.COMPOUND, 0);
            return;
        }
        writer.beginList("entities", NbtType.COMPOUND, structure.entities().size());
        for (Entity entity : structure.entities()) {
            BlockPos blockPos = EntityConverter.blockPos(entity);
            writer.beginList("pos", NbtType.DOUBLE, 3);
            writer.payloadDouble(entity.pos().x());
            writer.payloadDouble(entity.pos().y());
            writer.payloadDouble(entity.pos().z());
            writer.beginList("blockPos", NbtType.INT, 3);
            writer.payloadInt(blockPos.x());
            writer.payloadInt(blockPos.y());
            writer.payloadInt(blockPos.z());
            writer.put("nbt", EntityConverter.toStructureNbt(entity));
            writer.endCompound();
            counters.entities++;
        }
    }

    /** Extra pass needed only for {@code --skip-air}: a list must declare its length first. */
    private static long countWrittenBlocks(Structure structure, Palette palette) throws IOException {
        long[] kept = {0};
        structure.forEachBlock(block -> {
            if (!palette.state(block.stateIndex()).isAir()) {
                kept[0]++;
            }
        });
        return kept[0];
    }

    private static final class Counters {
        final long[] usagePerPaletteIndex;
        long air;
        int blockEntities;
        int entities;
        int repairedSignLines;

        Counters(int paletteSize) {
            this.usagePerPaletteIndex = new long[paletteSize];
        }
    }

    /** Read-only view of the options in effect. */
    public CreateWriterOptions options() {
        return options;
    }
}
