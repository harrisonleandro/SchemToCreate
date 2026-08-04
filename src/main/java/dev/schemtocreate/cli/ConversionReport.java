package dev.schemtocreate.cli;

import dev.schemtocreate.converter.ConversionResult;
import dev.schemtocreate.util.Formats;
import dev.schemtocreate.util.MemorySampler;
import dev.schemtocreate.writer.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Renders a {@link ConversionResult} to the log. */
final class ConversionReport {

    private static final Logger LOG = LoggerFactory.getLogger("SchemToCreate");

    private ConversionReport() {
    }

    /** One line per file, always shown. */
    static void summary(ConversionResult result) {
        WriteResult write = result.write();
        LOG.info("{} -> {}  |  {}  |  {} blocks  |  {}  |  {}",
                result.input().getFileName(),
                result.output().getFileName(),
                result.region(),
                Formats.count(write.blocksWritten()),
                Formats.bytes(write.compressedBytes()),
                Formats.duration(result.totalNanos()));
    }

    /** Everything worth knowing about the conversion; shown with {@code --verbose}. */
    static void details(ConversionResult result) {
        WriteResult write = result.write();
        LOG.info("  source format   : {}", result.sourceFormat());
        LOG.info("  region          : {} ({} blocks)", result.region(), Formats.count(write.blocksWritten()));
        LOG.info("  solid / air     : {} / {}",
                Formats.count(write.solidBlocks()), Formats.count(write.airBlocks()));
        LOG.info("  palette         : {} entries", Formats.count(write.paletteSize()));
        LOG.info("  block entities  : {}", Formats.count(write.blockEntitiesWritten()));
        LOG.info("  entities        : {}", Formats.count(write.entitiesWritten()));
        LOG.info("  nbt size        : {} uncompressed -> {} gzip ({}%)",
                Formats.bytes(write.uncompressedBytes()),
                Formats.bytes(write.compressedBytes()),
                Math.round(write.compressionRatio() * 100));
        LOG.info("  read / write    : {} / {}",
                Formats.duration(result.readNanos()), Formats.duration(result.writeNanos()));
    }

    /** Throughput and memory figures; shown with {@code --benchmark}. */
    static void benchmark(ConversionResult result, long peakHeapBytes) {
        WriteResult write = result.write();
        LOG.info("  throughput      : {} ({} total)",
                Formats.rate(write.blocksWritten(), result.totalNanos()),
                Formats.duration(result.totalNanos()));
        LOG.info("  peak heap       : {} (of {} max)",
                Formats.bytes(peakHeapBytes),
                Formats.bytes(Runtime.getRuntime().maxMemory()));
        LOG.info("  heap after      : {}", Formats.bytes(MemorySampler.usedHeapBytes()));
        if (write.blocksWritten() > 0) {
            // Locale.ROOT: a decimal comma in a size figure reads as a thousands separator.
            LOG.info("  bytes per block : {} gzip, {} raw nbt",
                    String.format(java.util.Locale.ROOT, "%.3f",
                            (double) write.compressedBytes() / write.blocksWritten()),
                    String.format(java.util.Locale.ROOT, "%.1f",
                            (double) write.uncompressedBytes() / write.blocksWritten()));
        }
    }

    /** Create compatibility notes, if any. */
    static void warnings(ConversionResult result) {
        for (String warning : result.warnings()) {
            LOG.warn("  {}", warning);
        }
    }

    /**
     * Every distinct block the structure places, commonest first.
     *
     * <p>The practical use is spotting blocks a target version does not have: those resolve
     * to air with nothing logged, so a build made in a newer Minecraft arrives full of holes
     * and this listing is the only way to see why.
     */
    static void palette(ConversionResult result) {
        List<WriteResult.BlockUsage> usage = result.write().blockUsage();
        LOG.info("  {} distinct block(s) in {}:", usage.size(), result.input().getFileName());
        for (WriteResult.BlockUsage entry : usage) {
            LOG.info("    {} x {}", String.format("%9s", Formats.count(entry.count())), entry.name());
        }
    }
}
