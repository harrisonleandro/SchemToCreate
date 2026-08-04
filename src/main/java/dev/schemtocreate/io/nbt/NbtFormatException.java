package dev.schemtocreate.io.nbt;

import java.io.IOException;

/** Raised when a stream is not valid NBT, or violates a configured safety limit. */
public class NbtFormatException extends IOException {

    public NbtFormatException(String message) {
        super(message);
    }

    public NbtFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
