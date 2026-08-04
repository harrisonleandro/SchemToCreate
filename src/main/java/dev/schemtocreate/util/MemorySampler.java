package dev.schemtocreate.util;

/**
 * Polls heap usage on a daemon thread to report a peak.
 *
 * <p>Sampling rather than instrumenting: the interesting question is whether a conversion
 * stays flat while streaming, and a periodic reading answers that without perturbing the
 * hot loop.
 */
public final class MemorySampler implements AutoCloseable {

    private static final long SAMPLE_INTERVAL_MILLIS = 50;

    private final Thread thread;
    private volatile boolean running = true;
    private volatile long peakBytes;

    private MemorySampler() {
        this.thread = new Thread(this::sample, "memory-sampler");
        this.thread.setDaemon(true);
    }

    public static MemorySampler start() {
        MemorySampler sampler = new MemorySampler();
        sampler.thread.start();
        return sampler;
    }

    private void sample() {
        Runtime runtime = Runtime.getRuntime();
        while (running) {
            peakBytes = Math.max(peakBytes, runtime.totalMemory() - runtime.freeMemory());
            try {
                Thread.sleep(SAMPLE_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Highest heap usage observed, in bytes. */
    public long peakBytes() {
        return peakBytes;
    }

    public static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    @Override
    public void close() {
        running = false;
        thread.interrupt();
    }
}
