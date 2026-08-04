package dev.schemtocreate.model;

/**
 * The cuboid a structure occupies.
 *
 * <p>{@code offset} is where the structure's origin sits relative to the world position it
 * was copied from. Both source formats carry it; Create's schematic table applies its own
 * placement offset, so it is preserved as metadata rather than baked into block positions.
 *
 * <p>Linear indices follow the Sponge ordering {@code x + z * width + y * width * length},
 * i.e. Y is the slowest axis and X the fastest.
 */
public record Region(int width, int height, int length, BlockPos offset) {

    public Region {
        if (width < 0 || height < 0 || length < 0) {
            throw new IllegalArgumentException(
                    "Negative region size: " + width + "x" + height + "x" + length);
        }
    }

    public static Region of(int width, int height, int length) {
        return new Region(width, height, length, BlockPos.ORIGIN);
    }

    /** Total block count. A {@code long} because 65535³ overflows an {@code int}. */
    public long volume() {
        return (long) width * height * length;
    }

    public boolean isEmpty() {
        return volume() == 0;
    }

    /** Linear index of a position in Sponge/vanilla YZX order. */
    public long indexOf(int x, int y, int z) {
        return x + (long) z * width + (long) y * width * length;
    }

    /** Inverse of {@link #indexOf}. */
    public BlockPos positionOf(long index) {
        int x = (int) (index % width);
        long rest = index / width;
        int z = (int) (rest % length);
        int y = (int) (rest / length);
        return new BlockPos(x, y, z);
    }

    public boolean contains(BlockPos pos) {
        return pos.x() >= 0 && pos.x() < width
                && pos.y() >= 0 && pos.y() < height
                && pos.z() >= 0 && pos.z() < length;
    }

    @Override
    public String toString() {
        return width + "x" + height + "x" + length;
    }
}
