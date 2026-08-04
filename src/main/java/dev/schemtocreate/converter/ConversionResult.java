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
 */
public record ConversionResult(Path input,
                               Path output,
                               String sourceFormat,
                               Region region,
                               WriteResult write,
                               long readNanos,
                               long writeNanos,
                               List<String> warnings) {

    public long totalNanos() {
        return readNanos + writeNanos;
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
