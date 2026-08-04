package dev.schemtocreate.model;

import dev.schemtocreate.blockstate.BlockState;
import dev.schemtocreate.palette.Palette;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The format-neutral representation every reader produces and every writer consumes.
 *
 * <p>Nothing here refers to Sponge or to Create. Blocks are exposed only through
 * {@link #forEachBlock}, which walks the source once and never builds a collection, so a
 * structure of any size costs the same to hold: its palette, its block entities and its
 * entities.
 */
public final class Structure {

    private final Region region;
    private final Palette palette;
    private final BlockIndexSource blockIndices;
    private final Map<BlockPos, BlockEntity> blockEntities;
    private final List<Entity> entities;
    private final SchematicMetadata metadata;
    private final int dataVersion;

    private Structure(Builder builder) {
        this.region = Objects.requireNonNull(builder.region, "region");
        this.palette = Objects.requireNonNull(builder.palette, "palette");
        this.blockIndices = Objects.requireNonNull(builder.blockIndices, "blockIndices");
        this.blockEntities = Map.copyOf(builder.blockEntities);
        this.entities = List.copyOf(builder.entities);
        this.metadata = builder.metadata == null ? SchematicMetadata.empty() : builder.metadata;
        this.dataVersion = builder.dataVersion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Region region() {
        return region;
    }

    public Palette palette() {
        return palette;
    }

    public BlockIndexSource blockIndices() {
        return blockIndices;
    }

    public Map<BlockPos, BlockEntity> blockEntities() {
        return blockEntities;
    }

    public List<Entity> entities() {
        return entities;
    }

    public SchematicMetadata metadata() {
        return metadata;
    }

    /** Minecraft data version the source was written against; 0 when unknown. */
    public int dataVersion() {
        return dataVersion;
    }

    /** Total block count, air included. */
    public long blockCount() {
        return region.volume();
    }

    /**
     * Streams every block in YZX order, in a single pass over the source.
     *
     * @throws IOException if the underlying block data cannot be read
     */
    public void forEachBlock(BlockVisitor visitor) throws IOException {
        long volume = region.volume();
        if (volume == 0) {
            return;
        }
        boolean hasBlockEntities = !blockEntities.isEmpty();
        try (BlockIndexCursor cursor = blockIndices.open()) {
            for (long index = 0; index < volume; index++) {
                BlockPos pos = region.positionOf(index);
                int stateIndex = cursor.next();
                BlockState state = palette.state(stateIndex);
                BlockEntity blockEntity = hasBlockEntities ? blockEntities.get(pos) : null;
                visitor.visit(new Block(pos, stateIndex, state, blockEntity));
            }
        }
    }

    /** Callback for {@link #forEachBlock}. */
    @FunctionalInterface
    public interface BlockVisitor {
        void visit(Block block) throws IOException;
    }

    public static final class Builder {

        private Region region;
        private Palette palette;
        private BlockIndexSource blockIndices = BlockIndexSource.empty();
        private Map<BlockPos, BlockEntity> blockEntities = Map.of();
        private List<Entity> entities = List.of();
        private SchematicMetadata metadata;
        private int dataVersion;

        public Builder region(Region value) {
            this.region = value;
            return this;
        }

        public Builder palette(Palette value) {
            this.palette = value;
            return this;
        }

        public Builder blockIndices(BlockIndexSource value) {
            this.blockIndices = value;
            return this;
        }

        public Builder blockEntities(Map<BlockPos, BlockEntity> value) {
            this.blockEntities = value;
            return this;
        }

        public Builder entities(List<Entity> value) {
            this.entities = value;
            return this;
        }

        public Builder metadata(SchematicMetadata value) {
            this.metadata = value;
            return this;
        }

        public Builder dataVersion(int value) {
            this.dataVersion = value;
            return this;
        }

        public Structure build() {
            return new Structure(this);
        }
    }
}
