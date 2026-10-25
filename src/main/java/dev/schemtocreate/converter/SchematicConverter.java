package dev.schemtocreate.converter;

import dev.schemtocreate.io.nbt.NbtDocument;
import dev.schemtocreate.model.Structure;
import dev.schemtocreate.reader.SchematicReader;
import dev.schemtocreate.reader.SchematicReaderRegistry;
import dev.schemtocreate.reader.legacy.LegacySchematicReader;
import dev.schemtocreate.reader.sponge.SpongeSchematicReader;
import dev.schemtocreate.reader.structure.StructureNbtReader;
import dev.schemtocreate.util.ProgressListener;
import dev.schemtocreate.writer.CreateCompatibility;
import dev.schemtocreate.writer.CreateStructureWriter;
import dev.schemtocreate.util.Formats;
import dev.schemtocreate.writer.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Drives one conversion: pick a reader, read into the internal model, write with the Create
 * writer.
 *
 * <p>Instances are immutable and safe to share across threads; each {@link #convert} call
 * keeps its own state, which is what lets the batch runner convert several files in
 * parallel.
 */
public final class SchematicConverter {

    private static final Logger LOG = LoggerFactory.getLogger(SchematicConverter.class);

    private final SchematicReaderRegistry readers;
    private final ConversionOptions options;

    public SchematicConverter(ConversionOptions options) {
        this(options, defaultRegistry(options));
    }

    public SchematicConverter(ConversionOptions options, SchematicReaderRegistry readers) {
        this.options = options;
        this.readers = readers;
    }

    private static SchematicReaderRegistry defaultRegistry(ConversionOptions options) {
        return new SchematicReaderRegistry()
                .register(new SpongeSchematicReader(options.inlineThreshold(), options.nbtLimits()))
                .register(new LegacySchematicReader(options.nbtLimits()))
                .register(new StructureNbtReader(options.nbtLimits()));
    }

    /**
     * Replaces the source extension with {@code .nbt}. A source that is already {@code .nbt}
     * gets a {@code -converted} suffix instead, so the output never lands on the input.
     */
    public static Path defaultOutputFor(Path input) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".nbt")) {
            base += "-converted";
        }
        Path parent = input.toAbsolutePath().getParent();
        return parent == null ? Path.of(base + ".nbt") : parent.resolve(base + ".nbt");
    }

    public ConversionResult convert(Path input, Path output, ProgressListener progress) throws IOException {
        validateInput(input);
        // When splitting, whether `output` itself is written is only known after measuring,
        // so its existence is checked then; the parts are checked by the splitter.
        validateOutput(output, options.splitMaxBytes() <= 0);

        long readStart = System.nanoTime();
        SchematicReader reader = readers.detect(input);
        Structure structure = reader.read(input);
        long readNanos = System.nanoTime() - readStart;

        LOG.debug("{}: {} blocks, palette {}, {} block entities, {} entities",
                input.getFileName(), structure.blockCount(), structure.palette().size(),
                structure.blockEntities().size(), structure.entities().size());

        long writeStart = System.nanoTime();
        CreateStructureWriter writer = new CreateStructureWriter(options.writer());
        CreateStructureWriter.Prepared prepared = writer.prepare(structure);
        // Written to a sibling temporary file and moved into place, so an interrupted run
        // never leaves a truncated .nbt that Create would try to parse.
        Path temporary = temporaryBeside(output);
        WriteResult whole;
        try {
            whole = writer.write(prepared, temporary, progress, CreateStructureWriter.Slice.ALL);
            if (options.splitMaxBytes() <= 0 || whole.compressedBytes() <= options.splitMaxBytes()) {
                validateOutput(output, true);
                move(temporary, output);
                return new ConversionResult(input, output, reader.formatName(), structure.region(),
                        whole, readNanos, System.nanoTime() - writeStart,
                        CreateCompatibility.warnings(structure, whole), List.of());
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        PartSplitter.Split split = new PartSplitter(writer, prepared, options).write(output, whole);
        LOG.info("  Split into {} part(s) of at most {}; place every part at the same position",
                split.parts().size(), Formats.bytes(options.splitMaxBytes()));
        return new ConversionResult(input, split.parts().get(0), reader.formatName(), structure.region(),
                split.total(), readNanos, System.nanoTime() - writeStart,
                CreateCompatibility.warnings(structure, split.largest()), split.parts());
    }

    static Path temporaryBeside(Path output) throws IOException {
        Path directory = output.toAbsolutePath().getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        return Files.createTempFile(directory, ".schemtocreate-", ".nbt.part");
    }

    static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void validateInput(Path input) throws IOException {
        if (!Files.exists(input)) {
            throw new NoSuchFileException(input.toString());
        }
        if (Files.isDirectory(input)) {
            throw new IOException("Input is a directory: " + input + " (use --recursive)");
        }
        if (!Files.isReadable(input)) {
            throw new IOException("Input is not readable: " + input);
        }
        if (Files.size(input) == 0) {
            throw new IOException("Input file is empty: " + input);
        }
    }

    private void validateOutput(Path output, boolean checkExisting) throws IOException {
        if (checkExisting && Files.exists(output) && !options.overwrite()) {
            throw new FileExistsException(output);
        }
        if (Files.isDirectory(output)) {
            throw new IOException("Output path is a directory: " + output);
        }
    }

    /** Signals that an output file exists and {@code --overwrite} was not given. */
    public static final class FileExistsException extends IOException {
        private final Path path;

        FileExistsException(Path path) {
            super("Output already exists: " + path + " (use --overwrite to replace it)");
            this.path = path;
        }

        public Path path() {
            return path;
        }
    }

    /** Threshold above which the reader streams block data instead of buffering it. */
    public long inlineThreshold() {
        return options.inlineThreshold() > 0
                ? options.inlineThreshold()
                : NbtDocument.DEFAULT_INLINE_THRESHOLD;
    }
}
