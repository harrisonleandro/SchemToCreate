package dev.schemtocreate.model;

/** Integer position, relative to the structure origin. */
public record BlockPos(int x, int y, int z) {

    public static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    public BlockPos add(BlockPos other) {
        return new BlockPos(x + other.x, y + other.y, z + other.z);
    }

    public int[] toArray() {
        return new int[]{x, y, z};
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
