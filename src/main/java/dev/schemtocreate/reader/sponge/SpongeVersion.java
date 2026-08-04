package dev.schemtocreate.reader.sponge;

/**
 * The three revisions of the Sponge Schematic specification this reader understands.
 *
 * <p>They differ in where things live, not in how blocks are encoded — the varint palette
 * index array is identical in all three.
 */
public enum SpongeVersion {

    /**
     * Block entities live under {@code TileEntities}. There is no {@code DataVersion} and no
     * {@code Entities} list. {@code Offset} and {@code PaletteMax} are already present.
     */
    V1(1),

    /**
     * {@code TileEntities} renamed to {@code BlockEntities}; added {@code DataVersion},
     * {@code Entities} and the biome fields.
     */
    V2(2),

    /** Blocks and biomes moved into their own containers; per-entity data moved to {@code Data}. */
    V3(3);

    private final int number;

    SpongeVersion(int number) {
        this.number = number;
    }

    public int number() {
        return number;
    }

    public boolean isLegacyLayout() {
        return this != V3;
    }

    public static SpongeVersion fromNumber(int number) {
        return switch (number) {
            case 1 -> V1;
            case 2 -> V2;
            case 3 -> V3;
            default -> null;
        };
    }
}
