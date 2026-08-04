package dev.schemtocreate.writer;

import dev.schemtocreate.model.Structure;
import dev.schemtocreate.util.ProgressListener;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Serialises the internal model into an on-disk format.
 *
 * <p>Counterpart to {@link dev.schemtocreate.reader.SchematicReader}; a new output format is
 * a new implementation and nothing else.
 */
public interface StructureWriter {

    String formatName();

    /** Conventional file extension, without the dot. */
    String fileExtension();

    WriteResult write(Structure structure, Path target, ProgressListener progress) throws IOException;
}
