package dev.schemtocreate.converter;

import dev.schemtocreate.util.ProgressListener;
import dev.schemtocreate.writer.CreateStructureWriter;
import dev.schemtocreate.writer.CreateStructureWriter.Prepared;
import dev.schemtocreate.writer.CreateStructureWriter.Slice;
import dev.schemtocreate.writer.WriteResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a structure as several files that each stay under a size limit.
 *
 * <p>The cut is horizontal: rows ({@code y * length + z}) are taken bottom-up, so part 1 is
 * the ground floor and every later part sits on what was printed before it. Every part keeps
 * the <em>full</em> structure size and the original block positions, which is what makes
 * placement simple — each part is deployed at exactly the same spot, and blocks a part does
 * not list are left untouched by the Schematicannon, so parts never overwrite each other.
 *
 * <p>Compressed size cannot be known before writing, so parts are planned from the whole
 * file's bytes-per-block with some headroom, and any part that still comes out too large is
 * halved and written again.
 */
final class PartSplitter {

    /** Planning headroom: GZIP does worse on a slice than on the whole file. */
    private static final double PLANNING_FILL = 0.85;

    private final CreateStructureWriter writer;
    private final Prepared prepared;
    private final ConversionOptions options;

    PartSplitter(CreateStructureWriter writer, Prepared prepared, ConversionOptions options) {
        this.writer = writer;
        this.prepared = prepared;
        this.options = options;
    }

    /**
     * @param parts   written files in printing order
     * @param total   counts summed over all parts
     * @param largest the result of the biggest part, for limit warnings
     */
    record Split(List<Path> parts, WriteResult total, WriteResult largest) {
    }

    /** Builds the name of part {@code number}: {@code house.nbt} becomes {@code house-parte2.nbt}. */
    static Path partPath(Path output, int number) {
        String name = output.getFileName().toString();
        String base = name.toLowerCase(java.util.Locale.ROOT).endsWith(".nbt")
                ? name.substring(0, name.length() - 4) : name;
        return output.resolveSibling(base + "-parte" + number + ".nbt");
    }

    Split write(Path output, WriteResult whole) throws IOException {
        long limit = options.splitMaxBytes();
        long[] perRow = writer.keptBlocksPerRow(prepared);
        long totalBlocks = 0;
        for (long count : perRow) {
            totalBlocks += count;
        }
        double bytesPerBlock = (double) whole.compressedBytes() / Math.max(1, totalBlocks);
        long blocksPerPart = Math.max(1, (long) (limit * PLANNING_FILL / bytesPerBlock));
        List<int[]> ranges = plan(perRow, blocksPerPart);

        List<Path> temporaries = new ArrayList<>();
        List<WriteResult> results = new ArrayList<>();
        try {
            for (int i = 0; i < ranges.size(); i++) {
                int[] range = ranges.get(i);
                boolean last = range[1] >= perRow.length;
                Path temporary = SchematicConverter.temporaryBeside(output);
                temporaries.add(temporary);
                WriteResult result = writer.write(prepared, temporary, ProgressListener.noop(),
                        new Slice(range[0], range[1], last));
                if (result.compressedBytes() > limit) {
                    Files.deleteIfExists(temporary);
                    temporaries.remove(temporaries.size() - 1);
                    int middle = middleRow(perRow, range[0], range[1]);
                    if (middle < 0) {
                        throw new IOException("One row of the build (" + describeRow(range[0])
                                + ") is larger than " + limit + " bytes on its own, so it cannot "
                                + "be split further; raise the part size");
                    }
                    ranges.set(i, new int[]{range[0], middle});
                    ranges.add(i + 1, new int[]{middle, range[1]});
                    i--;
                    continue;
                }
                results.add(result);
            }
            return publish(output, temporaries, results);
        } finally {
            for (Path temporary : temporaries) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    /** Moves every part into place only once all of them were written successfully. */
    private Split publish(Path output, List<Path> temporaries, List<WriteResult> results)
            throws IOException {
        List<Path> parts = new ArrayList<>();
        for (int i = 0; i < temporaries.size(); i++) {
            Path part = partPath(output, i + 1);
            if (Files.exists(part) && !options.overwrite()) {
                throw new SchematicConverter.FileExistsException(part);
            }
            parts.add(part);
        }
        for (int i = 0; i < temporaries.size(); i++) {
            SchematicConverter.move(temporaries.get(i), parts.get(i));
        }
        return new Split(parts, sum(results), largest(results));
    }

    /** Greedy bottom-up grouping of rows into ranges of about {@code target} blocks. */
    private static List<int[]> plan(long[] perRow, long target) {
        List<int[]> ranges = new ArrayList<>();
        int start = 0;
        long accumulated = 0;
        for (int row = 0; row < perRow.length; row++) {
            if (accumulated > 0 && accumulated + perRow[row] > target) {
                ranges.add(new int[]{start, row});
                start = row;
                accumulated = 0;
            }
            accumulated += perRow[row];
        }
        ranges.add(new int[]{start, perRow.length});
        return ranges;
    }

    /** The row that halves a range by block count, or -1 for a single-row range. */
    private static int middleRow(long[] perRow, int from, int to) {
        if (to - from < 2) {
            return -1;
        }
        long total = 0;
        for (int row = from; row < to; row++) {
            total += perRow[row];
        }
        long half = 0;
        for (int row = from; row < to - 1; row++) {
            half += perRow[row];
            if (half * 2 >= total) {
                return row + 1;
            }
        }
        return to - 1;
    }

    private String describeRow(int row) {
        int length = prepared.structure().region().length();
        return "y=" + row / length + ", z=" + row % length;
    }

    private static WriteResult sum(List<WriteResult> results) {
        WriteResult first = results.get(0);
        long blocks = 0;
        long air = 0;
        int blockEntities = 0;
        int entities = 0;
        int repaired = 0;
        long uncompressed = 0;
        long compressed = 0;
        for (WriteResult result : results) {
            blocks += result.blocksWritten();
            air += result.airBlocks();
            blockEntities += result.blockEntitiesWritten();
            entities += result.entitiesWritten();
            repaired += result.repairedSignLines();
            uncompressed += result.uncompressedBytes();
            compressed += result.compressedBytes();
        }
        return new WriteResult(blocks, air, blockEntities, entities, first.paletteSize(),
                mergedUsage(results), repaired, uncompressed, compressed);
    }

    private static List<WriteResult.BlockUsage> mergedUsage(List<WriteResult> results) {
        java.util.Map<String, Long> totals = new java.util.HashMap<>();
        for (WriteResult result : results) {
            for (WriteResult.BlockUsage usage : result.blockUsage()) {
                totals.merge(usage.name(), usage.count(), Long::sum);
            }
        }
        return totals.entrySet().stream()
                .map(entry -> new WriteResult.BlockUsage(entry.getKey(), entry.getValue()))
                .sorted(java.util.Comparator.comparingLong(WriteResult.BlockUsage::count).reversed()
                        .thenComparing(WriteResult.BlockUsage::name))
                .toList();
    }

    private static WriteResult largest(List<WriteResult> results) {
        WriteResult largest = results.get(0);
        for (WriteResult result : results) {
            if (result.compressedBytes() > largest.compressedBytes()) {
                largest = result;
            }
        }
        return largest;
    }
}
