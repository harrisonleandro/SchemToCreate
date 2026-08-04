package dev.schemtocreate.util;

/** Receives progress updates from a long running conversion. */
@FunctionalInterface
public interface ProgressListener {

    /**
     * @param completed units finished so far
     * @param total     total units, or -1 when unknown
     */
    void onProgress(long completed, long total);

    static ProgressListener noop() {
        return (completed, total) -> {
        };
    }
}
