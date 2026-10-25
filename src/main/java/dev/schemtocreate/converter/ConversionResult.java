package dev.schemtocreate.converter;

import dev.schemtocreate.model.Region;
import dev.schemtocreate.writer.WriteResult;

import java.nio.file.Path;
import java.util.List;

/**
 * Outcome of converting one file.
 *
 * @param input        source path
 * @param output       written path
 * @param sourceFormat name of the reader that handled the source
 * @param region       dimensions of the converted structure
 * @param write        what the writer produced
 * @param readNanos    time spent parsing the header, palette and block entities
 * @param writeNanos   time spent streaming blocks to disk
 * @param warnings     Create compatibility notes; empty when the file should load cleanly
 * @param parts        every file written when the output was split, in printing order;
 *                     empty for a single-file output. {@code output} is then the first part
 *                     and {@code write} the totals across all of them.
 */
public record ConversionResult(Path input,
                               Path output,
                               String sourceFormat,
                               Region region,
                               WriteResult write,
                               long readNanos,
                               long writeNanos,
                               List<String> warnings,
                               List<Path> parts) {

    public ConversionResult {
        warnings = List.copyOf(warnings);
        parts = List.copyOf(parts);
    }

    public boolean isSplit() {
        return !parts.isEmpty();
    }

    public long totalNanos() {
        return readNanos + writeNanos;
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
