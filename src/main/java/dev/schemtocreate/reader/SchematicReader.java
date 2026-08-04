package dev.schemtocreate.reader;

import dev.schemtocreate.model.Structure;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Parses one on-disk schematic format into the internal {@link Structure} model.
 *
 * <p>Adding a format means adding an implementation and registering it in
 * {@link SchematicReaderRegistry}; nothing downstream of this interface changes.
 */
public interface SchematicReader {

    /** Display name used in logs, e.g. {@code "Sponge Schematic"}. */
    String formatName();

    /**
     * Cheap check of whether this reader recognises the file, based on its header rather
     * than its extension.
     */
    boolean supports(Path path) throws IOException;

    /**
     * @throws SchematicFormatException if the file is recognised but malformed
     * @throws IOException              on I/O failure
     */
    Structure read(Path path) throws IOException;
}
