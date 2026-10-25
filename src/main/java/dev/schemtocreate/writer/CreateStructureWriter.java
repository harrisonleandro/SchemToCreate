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
import java.util.BitSet;
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

    /**
     * Blocks that were renamed after 1.20.1, mapped back to the name 1.20.1 knows. Unlike a
     * block that is simply new, a rename has an exact equivalent, so it is applied always.
     */
    private static final Map<String, String> NEWER_NAMES = Map.of(
            "minecraft:short_grass", "minecraft:grass");

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

    /**
     * A structure with its palette rewrites and block filters worked out, so several slices
     * can be written without recomputing them — hidden blocks in particular must be found on
     * the whole structure, or every cut between parts would count as an exposed face.
     */
    public record Prepared(Structure structure, Palette palette, boolean[] dropped, BitSet hidden) {
    }

    /**
     * A contiguous range of rows, where row {@code y * length + z} runs along X. Rows are
     * ordered bottom-up, so consecutive slices are horizontal bands printed from the ground.
     *
     * @param fromRow  first row, inclusive
     * @param toRow    last row, exclusive
     * @param entities whether this slice carries the structure's entities
     */
    public record Slice(int fromRow, int toRow, boolean entities) {

        public static final Slice ALL = new Slice(0, Integer.MAX_VALUE, true);

        boolean isAll() {
            return fromRow <= 0 && toRow == Integer.MAX_VALUE;
        }

        boolean contains(int row) {
            return row >= fromRow && row < toRow;
        }
    }

    @Override
    public WriteResult write(Structure structure, Path target, ProgressListener progress) throws IOException {
        return write(prepare(structure), target, progress, Slice.ALL);
    }

    public Prepared prepare(Structure structure) throws IOException {
        Palette palette = applyPaletteRewrites(structure.palette());
        boolean[] dropped = droppedStates(palette);
        BitSet hidden = options.removeHidden() ? HiddenBlocks.find(structure, palette) : null;
        if (hidden != null) {
            LOG.info("  Left out {} hidden block(s) that no player can see", hidden.cardinality());
        }
        return new Prepared(structure, palette, dropped, hidden);
    }

    /** How many blocks each row would contribute to the output, for planning slices. */
    public long[] keptBlocksPerRow(Prepared prepared) throws IOException {
        Region region = prepared.structure().region();
        long[] perRow = new long[region.height() * region.length()];
        Counters filter = new Counters(0, prepared.dropped(), prepared.hidden(), Slice.ALL, region.length());
        prepared.structure().forEachBlock(block -> {
            if (!filter.isOmitted(block, region)) {
                perRow[block.pos().y() * region.length() + block.pos().z()]++;
            }
        });
        return perRow;
    }

    public WriteResult write(Prepared prepared, Path target, ProgressListener progress, Slice slice)
            throws IOException {
        Structure structure = prepared.structure();
        Palette palette = prepared.palette();
        boolean[] dropped = prepared.dropped();
        BitSet hidden = prepared.hidden();
        boolean filtering = hidden != null || containsTrue(dropped) || !slice.isAll();
        Counters counters = new Counters(palette.size(), dropped, hidden, slice, structure.region().length());
        long blockCount = filtering
                ? countWrittenBlocks(structure, counters)
                : structure.region().volume();
        requireListSize(blockCount, structure.region());

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
            writeEntities(writer, structure, counters, slice.entities());
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
            if (usage[index] > 0 && !palette.state(index).isAbsent()) {
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
            String renamed = NEWER_NAMES.get(states[i].name());
            if (renamed != null) {
                states[i] = states[i].withName(renamed);
                substituted++;
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
            LOG.info("  Substituted {} palette entr(ies) (--replace and post-1.20.1 renames)", substituted);
        }
        return voids + substituted == 0 ? source : new ArrayPalette(states);
    }

    private static void writeSize(NbtWriter writer, Region region) throws IOException {
        writer.beginList("size", NbtType.INT, 3);
        writer.payloadInt(region.width());
        writer.payloadInt(region.height());
        writer.payloadInt(region.length());
    }

    /**
     * The absent marker is only ever appended after a source's real palette, so trimming the
     * trailing markers keeps every written index valid without renumbering anything.
     */
    private static void writePalette(NbtWriter writer, Palette palette) throws IOException {
        int size = palette.size();
        while (size > 0 && palette.state(size - 1).isAbsent()) {
            size--;
        }
        writer.beginList("palette", NbtType.COMPOUND, size);
        for (BlockState state : palette.states().subList(0, size)) {
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
            boolean inSlice = counters.inSlice(block);
            boolean air = inSlice && palette.state(block.stateIndex()).isAir();
            boolean omitted = counters.isOmitted(block, structure.region());
            // Air is tallied even when skipped; hidden and absent blocks are not part of the build.
            if (!omitted || air) {
                counters.usagePerPaletteIndex[block.stateIndex()]++;
            }
            if (!omitted) {
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

    private void writeEntities(NbtWriter writer, Structure structure, Counters counters,
                               boolean sliceHasEntities) throws IOException {
        if (!sliceHasEntities || !options.includeEntities() || structure.entities().isEmpty()) {
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

    /** Palette entries never written: the absent marker always, air under {@code --skip-air}. */
    private boolean[] droppedStates(Palette palette) {
        boolean[] dropped = new boolean[palette.size()];
        for (int i = 0; i < dropped.length; i++) {
            BlockState state = palette.state(i);
            dropped[i] = state.isAbsent() || (options.skipAir() && state.isAir());
        }
        return dropped;
    }

    private static boolean containsTrue(boolean[] values) {
        for (boolean value : values) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    /** Extra pass needed only when filtering: a list must declare its length first. */
    private static long countWrittenBlocks(Structure structure, Counters filter) throws IOException {
        long[] kept = {0};
        structure.forEachBlock(block -> {
            if (!filter.isOmitted(block, structure.region())) {
                kept[0]++;
            }
        });
        return kept[0];
    }

    private static final class Counters {
        final long[] usagePerPaletteIndex;
        final boolean[] dropped;
        final BitSet hidden;
        final Slice slice;
        final int length;
        long air;
        int blockEntities;
        int entities;
        int repairedSignLines;

        Counters(int paletteSize, boolean[] dropped, BitSet hidden, Slice slice, int length) {
            this.usagePerPaletteIndex = new long[paletteSize];
            this.dropped = dropped;
            this.hidden = hidden;
            this.slice = slice;
            this.length = length;
        }

        boolean inSlice(Block block) {
            return slice.contains(block.pos().y() * length + block.pos().z());
        }

        boolean isOmitted(Block block, Region region) {
            if (dropped[block.stateIndex()] || !inSlice(block)) {
                return true;
            }
            BlockPos pos = block.pos();
            return hidden != null && hidden.get((int) region.indexOf(pos.x(), pos.y(), pos.z()));
        }
    }

    /** Read-only view of the options in effect. */
    public CreateWriterOptions options() {
        return options;
    }
}
