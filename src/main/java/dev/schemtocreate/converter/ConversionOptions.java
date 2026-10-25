package dev.schemtocreate.converter;

import dev.schemtocreate.io.nbt.NbtDocument;
import dev.schemtocreate.io.nbt.NbtLimits;
import dev.schemtocreate.writer.CreateWriterOptions;

/**
 * Everything that steers a single conversion.
 *
 * @param writer          output format settings
 * @param overwrite       replace an existing output file instead of failing
 * @param inlineThreshold byte arrays larger than this are streamed from the source rather
 *                        than held in memory
 * @param nbtLimits       parser guard rails for untrusted input
 * @param splitMaxBytes   when positive, an output larger than this is written as several
 *                        parts that each fit; 0 writes a single file whatever its size
 */
public record ConversionOptions(CreateWriterOptions writer,
                                boolean overwrite,
                                long inlineThreshold,
                                NbtLimits nbtLimits,
                                long splitMaxBytes) {

    /** Create's default {@code maxTotalSchematicSize}, the usual reason to split. */
    public static final long DEFAULT_SPLIT_BYTES = 256L * 1024;

    public static ConversionOptions defaults() {
        return new ConversionOptions(
                CreateWriterOptions.defaults(),
                false,
                NbtDocument.DEFAULT_INLINE_THRESHOLD,
                NbtLimits.DEFAULT,
                0);
    }

    public ConversionOptions withWriter(CreateWriterOptions value) {
        return new ConversionOptions(value, overwrite, inlineThreshold, nbtLimits, splitMaxBytes);
    }

    public ConversionOptions withOverwrite(boolean value) {
        return new ConversionOptions(writer, value, inlineThreshold, nbtLimits, splitMaxBytes);
    }

    public ConversionOptions withInlineThreshold(long value) {
        return new ConversionOptions(writer, overwrite, value, nbtLimits, splitMaxBytes);
    }

    public ConversionOptions withSplitMaxBytes(long value) {
        return new ConversionOptions(writer, overwrite, inlineThreshold, nbtLimits, value);
    }
}
