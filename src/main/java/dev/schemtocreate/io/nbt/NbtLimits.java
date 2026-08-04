package dev.schemtocreate.io.nbt;

/**
 * Guard rails for parsing untrusted files. A malformed length prefix would otherwise turn
 * into a multi-gigabyte allocation before any validation could run.
 *
 * @param maxDepth        maximum nesting of compounds and lists
 * @param maxArrayLength  maximum element count of any single array or list read into memory
 */
public record NbtLimits(int maxDepth, int maxArrayLength) {

    public static final NbtLimits DEFAULT = new NbtLimits(512, 1 << 28);

    public NbtLimits {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxArrayLength < 1) {
            throw new IllegalArgumentException("maxArrayLength must be positive");
        }
    }
}
