package dev.schemtocreate.blockstate;

/** Raised when a palette entry cannot be parsed as a block state. */
public class BlockStateFormatException extends RuntimeException {

    public BlockStateFormatException(String message) {
        super(message);
    }
}
