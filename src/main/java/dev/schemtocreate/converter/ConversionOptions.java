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
 */
public record ConversionOptions(CreateWriterOptions writer,
                                boolean overwrite,
                                long inlineThreshold,
                                NbtLimits nbtLimits) {

    public static ConversionOptions defaults() {
        return new ConversionOptions(
                CreateWriterOptions.defaults(),
                false,
                NbtDocument.DEFAULT_INLINE_THRESHOLD,
                NbtLimits.DEFAULT);
    }

    public ConversionOptions withWriter(CreateWriterOptions value) {
        return new ConversionOptions(value, overwrite, inlineThreshold, nbtLimits);
    }

    public ConversionOptions withOverwrite(boolean value) {
        return new ConversionOptions(writer, value, inlineThreshold, nbtLimits);
    }

    public ConversionOptions withInlineThreshold(long value) {
        return new ConversionOptions(writer, overwrite, value, nbtLimits);
    }
}
