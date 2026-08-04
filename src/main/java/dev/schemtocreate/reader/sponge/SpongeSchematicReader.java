package dev.schemtocreate.reader.sponge;

import dev.schemtocreate.io.ByteSource;
import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtDocument;
import dev.schemtocreate.io.nbt.NbtInt;
import dev.schemtocreate.io.nbt.NbtLimits;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtTag;
import dev.schemtocreate.model.BlockEntity;
import dev.schemtocreate.model.BlockIndexSource;
import dev.schemtocreate.model.BlockPos;
import dev.schemtocreate.model.Entity;
import dev.schemtocreate.model.Region;
import dev.schemtocreate.model.SchematicMetadata;
import dev.schemtocreate.model.Structure;
import dev.schemtocreate.model.Vec3d;
import dev.schemtocreate.palette.Palette;
import dev.schemtocreate.palette.PaletteBuilder;
import dev.schemtocreate.reader.SchematicFormatException;
import dev.schemtocreate.reader.SchematicReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads WorldEdit / Sponge {@code .schem} files, specification versions 1 through 3.
 *
 * <p>The block data array is never materialised: {@link NbtDocument} hands back a
 * {@link ByteSource} that streams it, so peak memory is governed by the palette and the
 * block entity list rather than by the number of blocks.
 */
public final class SpongeSchematicReader implements SchematicReader {

    private static final Logger LOG = LoggerFactory.getLogger(SpongeSchematicReader.class);

    private final long inlineThreshold;
    private final NbtLimits limits;

    public SpongeSchematicReader() {
        this(NbtDocument.DEFAULT_INLINE_THRESHOLD, NbtLimits.DEFAULT);
    }

    public SpongeSchematicReader(long inlineThreshold, NbtLimits limits) {
        this.inlineThreshold = inlineThreshold;
        this.limits = limits;
    }

    @Override
    public String formatName() {
        return "Sponge Schematic (v1/v2/v3)";
    }

    @Override
    public boolean supports(Path path) throws IOException {
        try {
            NbtCompound root = NbtDocument.load(path, 0, limits).root();
            NbtCompound schematic = root.getCompound("Schematic");
            NbtCompound candidate = schematic != null && !schematic.isEmpty() ? schematic : root;
            return candidate.contains("BlockData") || candidate.getCompound("Blocks") != null;
        } catch (IOException e) {
            LOG.debug("{} is not a Sponge schematic: {}", path.getFileName(), e.getMessage());
            return false;
        }
    }

    @Override
    public Structure read(Path path) throws IOException {
        NbtDocument document = NbtDocument.load(path, inlineThreshold, limits);
        SpongeLayout layout = SpongeLayout.resolve(document.root());
        NbtCompound schematic = layout.schematic();

        Region region = readRegion(schematic);
        Palette palette = readPalette(layout);
        BlockIndexSource blocks = openBlockData(document, layout, region);

        LOG.debug("Sponge v{} | {} | palette {} | block data {} ({})",
                layout.version().number(), region, palette.size(),
                describeBlockData(blocks), document.isDeferred(layout.blockDataPath())
                        ? "streamed from file" : "in memory");
        if (layout.hasBiomes()) {
            LOG.info("Source contains biome data; vanilla structures have no biome field, so it is dropped");
        }

        return Structure.builder()
                .region(region)
                .palette(palette)
                .blockIndices(blocks)
                .blockEntities(readBlockEntities(layout, region))
                .entities(readEntities(layout))
                .metadata(readMetadata(schematic))
                .dataVersion(schematic.getInt("DataVersion", 0))
                .build();
    }

    private static String describeBlockData(BlockIndexSource blocks) {
        return blocks instanceof VarintBlockIndexSource varint
                ? varint.encodedLength() + " bytes"
                : blocks.count() + " indices";
    }

    private static Region readRegion(NbtCompound schematic) throws SchematicFormatException {
        int width = schematic.getUnsignedShort("Width", -1);
        int height = schematic.getUnsignedShort("Height", -1);
        int length = schematic.getUnsignedShort("Length", -1);
        if (width < 0 || height < 0 || length < 0) {
            throw new SchematicFormatException(
                    "Missing or invalid dimensions: Width=" + width
                            + " Height=" + height + " Length=" + length);
        }
        int[] offset = schematic.getIntTriple("Offset");
        BlockPos origin = offset == null
                ? BlockPos.ORIGIN
                : new BlockPos(offset[0], offset[1], offset[2]);
        return new Region(width, height, length, origin);
    }

    private static Palette readPalette(SpongeLayout layout) throws SchematicFormatException {
        NbtCompound source = layout.palette();
        Map<String, Integer> mapping = new LinkedHashMap<>(Math.max(4, source.size() * 2));
        for (Map.Entry<String, NbtTag> entry : source.entries()) {
            if (entry.getValue() instanceof NbtInt index) {
                mapping.put(entry.getKey(), index.value());
            } else {
                throw new SchematicFormatException("Palette entry '" + entry.getKey()
                        + "' is not a TAG_Int");
            }
        }
        PaletteBuilder builder = PaletteBuilder.fromSpongeMapping(mapping, layout.declaredPaletteMax());
        if (builder.holes() > 0) {
            LOG.warn("Palette has {} unused index gap(s); they were filled with minecraft:air",
                    builder.holes());
        }
        return builder.build();
    }

    private static BlockIndexSource openBlockData(NbtDocument document, SpongeLayout layout,
                                                  Region region) throws SchematicFormatException {
        if (region.isEmpty()) {
            return BlockIndexSource.empty();
        }
        ByteSource bytes = document.byteSource(layout.blockDataPath());
        if (bytes == null) {
            throw new SchematicFormatException(
                    "Block data array missing at '" + layout.blockDataPath() + "'");
        }
        // One varint is at least one byte, so fewer bytes than blocks is always truncation.
        if (bytes.length() < region.volume()) {
            throw new SchematicFormatException("Block data holds " + bytes.length()
                    + " bytes but the region needs at least " + region.volume() + " varints");
        }
        return new VarintBlockIndexSource(bytes, region.volume());
    }

    private static Map<BlockPos, BlockEntity> readBlockEntities(SpongeLayout layout, Region region) {
        NbtList entries = layout.blockEntities();
        Map<BlockPos, BlockEntity> result = new HashMap<>(Math.max(4, entries.size() * 2));
        int skipped = 0;
        for (NbtCompound entry : entries.compounds()) {
            int[] position = entry.getIntTriple("Pos");
            String id = SpongePayloads.identifier(entry);
            if (position == null || id == null) {
                skipped++;
                continue;
            }
            BlockPos pos = new BlockPos(position[0], position[1], position[2]);
            if (!region.contains(pos)) {
                LOG.warn("Block entity {} at {} lies outside {}; dropped", id, pos, region);
                skipped++;
                continue;
            }
            result.put(pos, new BlockEntity(pos, id, SpongePayloads.payload(entry, layout.version())));
        }
        if (skipped > 0) {
            LOG.warn("Skipped {} malformed or out-of-bounds block entity entries", skipped);
        }
        return result;
    }

    private static List<Entity> readEntities(SpongeLayout layout) {
        NbtList entries = layout.entities();
        List<Entity> result = new java.util.ArrayList<>(entries.size());
        int skipped = 0;
        for (NbtCompound entry : entries.compounds()) {
            double[] position = entry.getDoubleTriple("Pos");
            String id = SpongePayloads.identifier(entry);
            if (position == null || id == null) {
                skipped++;
                continue;
            }
            result.add(new Entity(new Vec3d(position[0], position[1], position[2]), id,
                    SpongePayloads.payload(entry, layout.version())));
        }
        if (skipped > 0) {
            LOG.warn("Skipped {} entity entries without a usable Id or Pos", skipped);
        }
        return result;
    }

    private static SchematicMetadata readMetadata(NbtCompound schematic) {
        NbtCompound raw = schematic.getCompound("Metadata");
        if (raw == null) {
            return SchematicMetadata.empty();
        }
        Long date = raw.contains("Date") ? raw.getLong("Date", 0) : null;
        return new SchematicMetadata(
                raw.getString("Name", null),
                raw.getString("Author", null),
                date,
                raw.copy());
    }
}
