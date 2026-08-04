package dev.schemtocreate.cli;

import dev.schemtocreate.converter.ConversionResult;
import dev.schemtocreate.converter.SchematicConverter;
import dev.schemtocreate.util.MemorySampler;
import dev.schemtocreate.util.ProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

/**
 * Runs conversions, optionally several at once.
 *
 * <p>Parallelism is per file, not within a file. A single conversion is a sequential
 * pipeline by nature — varint block data cannot be indexed without decoding everything
 * before it, and the output list must be written in order — so splitting one file across
 * threads would mean buffering it, which is exactly what the streaming design avoids.
 * Converting many files at once, by contrast, scales cleanly.
 *
 * <p>A failure is reported and counted; the remaining files still convert.
 */
final class BatchRunner {

    private static final Logger LOG = LoggerFactory.getLogger("SchemToCreate");

    /**
     * @param converted files written
     * @param failed    files that raised an error
     * @param skipped   files that already had an output and were left alone
     * @param peakHeap  highest heap usage sampled during the run, or 0 if not sampled
     */
    record Outcome(int converted, int failed, int skipped, long totalBlocks,
                   long totalBytes, long peakHeap) {

        boolean isSuccess() {
            return failed == 0;
        }
    }

    private final SchematicConverter converter;
    private final int threads;
    private final boolean verbose;
    private final boolean benchmark;
    private final boolean listPalette;

    BatchRunner(SchematicConverter converter, int threads, boolean verbose, boolean benchmark,
                boolean listPalette) {
        this.converter = converter;
        this.threads = threads;
        this.verbose = verbose;
        this.benchmark = benchmark;
        this.listPalette = listPalette;
    }

    Outcome run(List<Path> inputs, UnaryOperator<Path> outputResolver) throws InterruptedException {
        MemorySampler sampler = benchmark ? MemorySampler.start() : null;
        try {
            return threads > 1 && inputs.size() > 1
                    ? runParallel(inputs, outputResolver, sampler)
                    : runSequential(inputs, outputResolver, sampler);
        } finally {
            if (sampler != null) {
                sampler.close();
            }
        }
    }

    private Outcome runSequential(List<Path> inputs, UnaryOperator<Path> outputResolver,
                                  MemorySampler sampler) {
        Tally tally = new Tally();
        // A redrawing progress bar only makes sense on a terminal; piped or redirected
        // output would collect a line of control characters per update instead.
        boolean live = inputs.size() == 1 && System.console() != null;
        for (Path input : inputs) {
            convertOne(input, outputResolver.apply(input), tally, live);
        }
        return tally.toOutcome(sampler);
    }

    private Outcome runParallel(List<Path> inputs, UnaryOperator<Path> outputResolver,
                                MemorySampler sampler) throws InterruptedException {
        Tally tally = new Tally();
        int poolSize = Math.min(threads, inputs.size());
        LOG.debug("Converting {} files on {} threads", inputs.size(), poolSize);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "convert");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Callable<Void>> tasks = inputs.stream()
                    .map(input -> (Callable<Void>) () -> {
                        convertOne(input, outputResolver.apply(input), tally, false);
                        return null;
                    })
                    .toList();
            for (Future<Void> ignored : pool.invokeAll(tasks)) {
                // invokeAll already waited; failures were recorded inside convertOne.
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
        return tally.toOutcome(sampler);
    }

    private void convertOne(Path input, Path output, Tally tally, boolean liveProgress) {
        ConsoleProgress progress = liveProgress
                ? new ConsoleProgress(System.err, input.getFileName().toString())
                : null;
        try {
            ConversionResult result = converter.convert(input, output,
                    progress == null ? ProgressListener.noop() : progress);
            // Clear the progress line before anything is logged over the top of it.
            closeQuietly(progress);
            report(result);
            tally.record(result);
        } catch (SchematicConverter.FileExistsException e) {
            LOG.warn("{}", e.getMessage());
            tally.skipped.incrementAndGet();
        } catch (Exception e) {
            LOG.error("{}: {}", input.getFileName(), e.getMessage());
            LOG.debug("Conversion failed", e);
            tally.failed.incrementAndGet();
        } finally {
            closeQuietly(progress);
        }
    }

    private static void closeQuietly(ConsoleProgress progress) {
        if (progress != null) {
            progress.close();
        }
    }

    /**
     * Synchronized so a file's summary, details and warnings stay together: with several
     * conversion threads logging, unsynchronized reporting interleaves lines from different
     * files and makes the output unreadable.
     */
    private synchronized void report(ConversionResult result) {
        ConversionReport.summary(result);
        if (verbose) {
            ConversionReport.details(result);
        }
        if (benchmark) {
            ConversionReport.benchmark(result, MemorySampler.usedHeapBytes());
        }
        if (listPalette) {
            ConversionReport.palette(result);
        }
        ConversionReport.warnings(result);
    }

    private static final class Tally {
        final AtomicInteger converted = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicInteger skipped = new AtomicInteger();
        final AtomicLong blocks = new AtomicLong();
        final AtomicLong bytes = new AtomicLong();

        void record(ConversionResult result) {
            converted.incrementAndGet();
            blocks.addAndGet(result.write().blocksWritten());
            bytes.addAndGet(result.write().compressedBytes());
        }

        Outcome toOutcome(MemorySampler sampler) {
            return new Outcome(converted.get(), failed.get(), skipped.get(),
                    blocks.get(), bytes.get(), sampler == null ? 0 : sampler.peakBytes());
        }
    }
}
