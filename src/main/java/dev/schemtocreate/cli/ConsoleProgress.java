package dev.schemtocreate.cli;

import dev.schemtocreate.util.Formats;
import dev.schemtocreate.util.ProgressListener;

import java.io.PrintStream;
import java.util.Locale;

/**
 * Single-line progress indicator on stderr.
 *
 * <p>stderr keeps stdout free for machine-readable output, and rate limiting means a
 * hundred million callbacks cost a handful of writes rather than a hundred million.
 */
final class ConsoleProgress implements ProgressListener, AutoCloseable {

    private static final long MIN_INTERVAL_NANOS = 100_000_000L;
    private static final int BAR_WIDTH = 28;

    private final PrintStream out;
    private final String label;
    private final long startNanos = System.nanoTime();
    private long lastEmitNanos;
    private boolean dirty;

    ConsoleProgress(PrintStream out, String label) {
        this.out = out;
        this.label = label;
    }

    @Override
    public void onProgress(long completed, long total) {
        long now = System.nanoTime();
        boolean finished = total > 0 && completed >= total;
        if (!finished && now - lastEmitNanos < MIN_INTERVAL_NANOS) {
            return;
        }
        lastEmitNanos = now;
        out.print('\r');
        out.print(render(completed, total, now - startNanos));
        out.flush();
        dirty = true;
    }

    private String render(long completed, long total, long elapsedNanos) {
        if (total <= 0) {
            return String.format(Locale.ROOT, "  %s  %s blocks  %s",
                    label, Formats.count(completed), Formats.rate(completed, elapsedNanos));
        }
        int filled = (int) (BAR_WIDTH * Math.min(1.0, (double) completed / total));
        String bar = "=".repeat(filled) + " ".repeat(BAR_WIDTH - filled);
        return String.format(Locale.ROOT, "  %s [%s] %5.1f%%  %s blocks  %s",
                label, bar, 100.0 * completed / total,
                Formats.count(completed), Formats.rate(completed, elapsedNanos));
    }

    /** Clears the progress line so it does not collide with whatever is logged next. */
    @Override
    public void close() {
        if (dirty) {
            dirty = false;
            out.print('\r');
            out.print(" ".repeat(90));
            out.print('\r');
            out.flush();
        }
    }
}
