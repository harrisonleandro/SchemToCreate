package dev.schemtocreate.model;

/** Floating point position, relative to the structure origin. */
public record Vec3d(double x, double y, double z) {

    public static final Vec3d ZERO = new Vec3d(0, 0, 0);

    /**
     * The block containing this position. Uses floor rather than truncation so that
     * negative coordinates map to the block below, matching Minecraft's own rounding.
     */
    public BlockPos containingBlock() {
        return new BlockPos(
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.floor(z));
    }

    public double[] toArray() {
        return new double[]{x, y, z};
    }
}
