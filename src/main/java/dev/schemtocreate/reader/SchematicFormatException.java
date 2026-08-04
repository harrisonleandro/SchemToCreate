package dev.schemtocreate.reader;

import java.io.IOException;

/** Raised when a file is recognised as a schematic but violates its format's rules. */
public class SchematicFormatException extends IOException {

    public SchematicFormatException(String message) {
        super(message);
    }

    public SchematicFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
