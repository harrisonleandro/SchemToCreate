package dev.schemtocreate.io.nbt;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * File level NBT plumbing: GZIP detection on input, GZIP on output.
 *
 * <p>Create reads schematics through a {@code GZIPInputStream}, so every file this tool
 * produces is GZIP compressed. Input is sniffed instead of assumed because some tools emit
 * uncompressed {@code .schem} files.
 */
public final class NbtIo {

    private static final int GZIP_MAGIC_0 = 0x1F;
    private static final int GZIP_MAGIC_1 = 0x8B;
    private static final int BUFFER_SIZE = 1 << 16;

    private NbtIo() {
    }

    /** Opens {@code path}, transparently decompressing it if it is GZIP. */
    public static InputStream openDecompressed(Path path) throws IOException {
        InputStream raw = new BufferedInputStream(Files.newInputStream(path), BUFFER_SIZE);
        return wrapIfCompressed(raw);
    }

    private static InputStream wrapIfCompressed(InputStream buffered) throws IOException {
        buffered.mark(2);
        int first = buffered.read();
        int second = buffered.read();
        buffered.reset();
        if (first == GZIP_MAGIC_0 && second == GZIP_MAGIC_1) {
            return new GZIPInputStream(buffered, BUFFER_SIZE);
        }
        return buffered;
    }

    /** True if {@code path} starts with the GZIP magic number. */
    public static boolean isGzipped(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return in.read() == GZIP_MAGIC_0 && in.read() == GZIP_MAGIC_1;
        }
    }

    /** Reads a whole NBT file into memory. Only appropriate for small files. */
    public static NamedTag read(Path path) throws IOException {
        return read(path, NbtLimits.DEFAULT);
    }

    public static NamedTag read(Path path, NbtLimits limits) throws IOException {
        try (NbtReader reader = new NbtReader(openDecompressed(path), limits)) {
            return reader.readRoot();
        }
    }

    /**
     * Opens a GZIP sink at {@code level}.
     *
     * @param level {@link Deflater} level, 0-9; higher trades CPU for a smaller file
     */
    public static OutputStream gzipSink(OutputStream sink, int level) throws IOException {
        int clamped = clampLevel(level);
        // The deflater level must be set on the stream's own Deflater, which is only
        // reachable from a subclass; the GZIP header is written by super() beforehand and
        // does not pass through the deflater, so setting it here is safe.
        return new GZIPOutputStream(sink, BUFFER_SIZE) {
            {
                def.setLevel(clamped);
            }
        };
    }

    public static final int DEFAULT_COMPRESSION_LEVEL = 6;

    static int clampLevel(int level) {
        return Math.max(Deflater.BEST_SPEED, Math.min(Deflater.BEST_COMPRESSION, level));
    }

    /** Writes a complete in-memory tag as a GZIP compressed NBT file. */
    public static void writeCompressed(Path path, String rootName, NbtCompound root) throws IOException {
        try (OutputStream sink = gzipSink(Files.newOutputStream(path), DEFAULT_COMPRESSION_LEVEL);
             NbtWriter writer = new NbtWriter(sink)) {
            writer.beginRootCompound(rootName);
            writer.putAll(root);
            writer.endCompound();
        }
    }

    /** Writes a complete in-memory tag without compression. Used by tests and debugging. */
    public static void writeUncompressed(Path path, String rootName, NbtCompound root) throws IOException {
        try (NbtWriter writer = new NbtWriter(Files.newOutputStream(path))) {
            writer.beginRootCompound(rootName);
            writer.putAll(root);
            writer.endCompound();
        }
    }
}
