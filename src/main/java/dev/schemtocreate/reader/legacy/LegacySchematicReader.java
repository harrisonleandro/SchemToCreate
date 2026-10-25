package dev.schemtocreate.reader.legacy;

import dev.schemtocreate.blockstate.BlockState;
import dev.schemtocreate.io.ByteSource;
import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtDocument;
import dev.schemtocreate.io.nbt.NbtLimits;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtString;
import dev.schemtocreate.io.nbt.NbtTag;
import dev.schemtocreate.model.ArrayBlockIndexSource;
import dev.schemtocreate.model.BlockEntity;
import dev.schemtocreate.model.BlockPos;
import dev.schemtocreate.model.Entity;
import dev.schemtocreate.model.Region;
import dev.schemtocreate.model.SchematicMetadata;
import dev.schemtocreate.model.Structure;
import dev.schemtocreate.model.Vec3d;
import dev.schemtocreate.palette.ArrayPalette;
import dev.schemtocreate.reader.SchematicFormatException;
import dev.schemtocreate.reader.SchematicReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads pre-1.13 MCEdit / Schematica {@code .schematic} files ({@code Materials = "Alpha"}).
 *
 * <p>The layout is one byte per block for the id, an optional nibble array ({@code AddBlocks})
 * or byte array ({@code Add}) for ids above 255, and one byte per block of data. Every block
 * is translated to a flattened state by {@link LegacyBlockTable}. Because door halves and
 * tall plants need their neighbours, the whole volume is held in memory — which is fine for
 * this format, as its byte-per-block encoding already caps it far below Sponge's sizes.
 */
public final class LegacySchematicReader implements SchematicReader {

    private static final Logger LOG = LoggerFactory.getLogger(LegacySchematicReader.class);

    private final NbtLimits limits;

    public LegacySchematicReader() {
        this(NbtLimits.DEFAULT);
    }

    public LegacySchematicReader(NbtLimits limits) {
        this.limits = limits;
    }

    @Override
    public String formatName() {
        return "MCEdit/Schematica (.schematic, pre-1.13)";
    }

    @Override
    public boolean supports(Path path) throws IOException {
        try {
            NbtDocument document = NbtDocument.load(path, 0, limits);
            return document.byteSource("Blocks") != null && document.byteSource("Data") != null;
        } catch (IOException e) {
            LOG.debug("{} is not a legacy schematic: {}", path.getFileName(), e.getMessage());
            return false;
        }
    }

    @Override
    public Structure read(Path path) throws IOException {
        NbtDocument document = NbtDocument.load(path, 0, limits);
        NbtCompound root = document.root();
        String materials = root.getString("Materials", "Alpha");
        if (!"Alpha".equalsIgnoreCase(materials)) {
            throw new SchematicFormatException("Unsupported legacy material set '" + materials
                    + "' (only Alpha, i.e. Java Edition, is supported)");
        }
        int width = root.getUnsignedShort("Width", -1);
        int height = root.getUnsignedShort("Height", -1);
        int length = root.getUnsignedShort("Length", -1);
        if (width < 0 || height < 0 || length < 0) {
            throw new SchematicFormatException("Missing or invalid dimensions: Width=" + width
                    + " Height=" + height + " Length=" + length);
        }
        Region region = Region.of(width, height, length);
        int volume = Math.toIntExact(region.volume());

        int[] ids = readIds(document, volume);
        byte[] data = readAll(document.byteSource("Data"), volume, "Data");
        String[] names = idNames(root);
        Map<BlockPos, NbtCompound> legacyBlockEntities = readLegacyBlockEntities(root, region);

        Conversion conversion = new Conversion(region, ids, data, names, legacyBlockEntities);
        conversion.run();
        if (!conversion.unknown.isEmpty()) {
            LOG.warn("{} block type(s) are not vanilla 1.12 blocks and became air: {}",
                    conversion.unknown.size(), conversion.unknown);
        }
        LOG.debug("Legacy schematic | {} | {} distinct states", region, conversion.palette.size());

        return Structure.builder()
                .region(region)
                .palette(new ArrayPalette(conversion.palette.toArray(new BlockState[0])))
                .blockIndices(new ArrayBlockIndexSource(conversion.indices))
                .blockEntities(conversion.blockEntities)
                .entities(readEntities(root))
                .metadata(SchematicMetadata.empty())
                // Unknown source version: the writer then stamps 1.20.1, which is what the
                // translated states are.
                .dataVersion(0)
                .build();
    }

    private static int[] readIds(NbtDocument document, int volume) throws IOException {
        byte[] low = readAll(document.byteSource("Blocks"), volume, "Blocks");
        ByteSource addNibbles = document.byteSource("AddBlocks");
        ByteSource addBytes = document.byteSource("Add");
        byte[] add = addNibbles != null ? readAll(addNibbles, -1, "AddBlocks")
                : addBytes != null ? readAll(addBytes, -1, "Add") : null;
        int[] ids = new int[volume];
        for (int i = 0; i < volume; i++) {
            int id = low[i] & 0xFF;
            if (add != null && addNibbles != null && (i >> 1) < add.length) {
                // Same packing WorldEdit and MCEdit use: even index in the low nibble.
                id |= (i & 1) == 0 ? (add[i >> 1] & 0x0F) << 8 : (add[i >> 1] & 0xF0) << 4;
            } else if (add != null && addNibbles == null && i < add.length) {
                id |= (add[i] & 0xFF) << 8;
            }
            ids[i] = id;
        }
        return ids;
    }

    private static byte[] readAll(ByteSource source, int expected, String name) throws IOException {
        if (source == null) {
            throw new SchematicFormatException("Missing '" + name + "' array");
        }
        if (expected >= 0 && source.length() < expected) {
            throw new SchematicFormatException("'" + name + "' holds " + source.length()
                    + " bytes but the region needs " + expected);
        }
        try (InputStream in = source.open()) {
            return in.readNBytes(Math.toIntExact(source.length()));
        }
    }

    /** Numeric id to 1.12 block name: the file's own mapping when present, else vanilla's. */
    private static String[] idNames(NbtCompound root) {
        String[] names = Arrays.copyOf(LegacyBlockTable.VANILLA_IDS, 4096);
        NbtCompound schematica = root.getCompound("SchematicaMapping");
        if (schematica != null) {
            for (Map.Entry<String, NbtTag> entry : schematica.entries()) {
                int id = schematica.getInt(entry.getKey(), -1);
                if (id >= 0 && id < names.length) {
                    names[id] = entry.getKey();
                }
            }
        }
        NbtCompound mcedit = root.getCompound("BlockIDs");
        if (mcedit != null) {
            for (Map.Entry<String, NbtTag> entry : mcedit.entries()) {
                try {
                    int id = Integer.parseInt(entry.getKey());
                    if (id >= 0 && id < names.length && entry.getValue() instanceof NbtString name) {
                        names[id] = name.value();
                    }
                } catch (NumberFormatException ignored) {
                    // Not an id entry.
                }
            }
        }
        return names;
    }

    private static Map<BlockPos, NbtCompound> readLegacyBlockEntities(NbtCompound root, Region region) {
        NbtList list = root.getList("TileEntities");
        Map<BlockPos, NbtCompound> result = new HashMap<>();
        if (list == null) {
            return result;
        }
        for (NbtCompound entry : list.compounds()) {
            if (!entry.contains("x") || !entry.contains("y") || !entry.contains("z")) {
                continue;
            }
            BlockPos pos = new BlockPos(entry.getInt("x", 0), entry.getInt("y", 0), entry.getInt("z", 0));
            if (region.contains(pos)) {
                result.put(pos, entry);
            }
        }
        return result;
    }

    private static List<Entity> readEntities(NbtCompound root) {
        NbtList list = root.getList("Entities");
        List<Entity> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        for (NbtCompound entry : list.compounds()) {
            double[] pos = entry.getDoubleTriple("Pos");
            String id = LegacyBlockEntities.entityId(entry.getString("id", null));
            if (pos == null || id == null) {
                continue;
            }
            NbtCompound payload = entry.copy();
            payload.remove("id");
            result.add(new Entity(new Vec3d(pos[0], pos[1], pos[2]), id, payload));
        }
        return result;
    }

    /** The per-block translation pass. */
    private static final class Conversion implements LegacyBlockTable.Context {

        private final Region region;
        private final int[] ids;
        private final byte[] data;
        private final String[] names;
        private final Map<BlockPos, NbtCompound> legacyBlockEntities;

        final List<BlockState> palette = new ArrayList<>();
        final Map<BlockState, Integer> paletteIndex = new HashMap<>();
        final int[] indices;
        final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
        final Map<String, Integer> unknown = new TreeMap<>();

        private int below;
        private int above;
        private NbtCompound blockEntity;

        Conversion(Region region, int[] ids, byte[] data, String[] names,
                   Map<BlockPos, NbtCompound> legacyBlockEntities) {
            this.region = region;
            this.ids = ids;
            this.data = data;
            this.names = names;
            this.legacyBlockEntities = legacyBlockEntities;
            this.indices = new int[ids.length];
        }

        void run() {
            indexOf(BlockState.AIR);
            int layer = region.width() * region.length();
            // Most blocks depend only on id:data, so their palette index is cached per pair.
            int[] cache = new int[4096 * 16];
            Arrays.fill(cache, -1);
            for (int i = 0; i < ids.length; i++) {
                int id = ids[i];
                int meta = data[i] & 15;
                String name = names[id];
                boolean twoHigh = name != null && LegacyBlockTable.isTwoHigh(name);
                boolean usesBlockEntity = name != null && LegacyBlockTable.needsBlockEntity(name);
                BlockPos pos = null;
                if (!twoHigh && !usesBlockEntity && !legacyBlockEntities.isEmpty()) {
                    pos = region.positionOf(i);
                    if (legacyBlockEntities.containsKey(pos)) {
                        usesBlockEntity = true;
                    }
                }
                int key = (id << 4) | meta;
                if (!twoHigh && !usesBlockEntity && cache[key] >= 0) {
                    indices[i] = cache[key];
                    continue;
                }
                below = i - layer >= 0 && ids[i - layer] == id ? data[i - layer] & 15 : -1;
                above = i + layer < ids.length && ids[i + layer] == id ? data[i + layer] & 15 : -1;
                if (pos == null) {
                    pos = region.positionOf(i);
                }
                blockEntity = legacyBlockEntities.get(pos);

                BlockState state = LegacyBlockTable.convert(name, meta, this);
                if (state == null) {
                    unknown.merge(name == null ? "#" + id : name, 1, Integer::sum);
                    state = BlockState.AIR;
                }
                int index = indexOf(state);
                indices[i] = index;
                if (!twoHigh && !usesBlockEntity) {
                    cache[key] = index;
                }
                if (blockEntity != null) {
                    BlockEntity converted = LegacyBlockEntities.convert(pos, blockEntity, state);
                    if (converted != null) {
                        blockEntities.put(pos, converted);
                    }
                }
            }
        }

        private int indexOf(BlockState state) {
            return paletteIndex.computeIfAbsent(state, s -> {
                palette.add(s);
                return palette.size() - 1;
            });
        }

        @Override
        public int dataBelow() {
            return below;
        }

        @Override
        public int dataAbove() {
            return above;
        }

        @Override
        public NbtCompound blockEntity() {
            return blockEntity;
        }
    }

}
