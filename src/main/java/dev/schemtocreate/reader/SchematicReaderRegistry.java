package dev.schemtocreate.reader;

import dev.schemtocreate.reader.sponge.SpongeSchematicReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses a reader for a file by asking each registered reader whether it recognises it.
 *
 * <p>Detection is content based, so a {@code .schematic} or extension-less file still
 * converts correctly.
 */
public final class SchematicReaderRegistry {

    private final List<SchematicReader> readers = new ArrayList<>();

    public static SchematicReaderRegistry withDefaults() {
        SchematicReaderRegistry registry = new SchematicReaderRegistry();
        registry.register(new SpongeSchematicReader());
        return registry;
    }

    public SchematicReaderRegistry register(SchematicReader reader) {
        readers.add(reader);
        return this;
    }

    public List<SchematicReader> readers() {
        return List.copyOf(readers);
    }

    /**
     * @return the first reader that recognises {@code path}
     * @throws SchematicFormatException if no registered reader does
     */
    public SchematicReader detect(Path path) throws IOException {
        for (SchematicReader reader : readers) {
            if (reader.supports(path)) {
                return reader;
            }
        }
        throw new SchematicFormatException(
                "Unrecognised schematic format: " + path.getFileName()
                        + " (supported: " + describeSupported() + ")");
    }

    private String describeSupported() {
        return String.join(", ", readers.stream().map(SchematicReader::formatName).toList());
    }
}
