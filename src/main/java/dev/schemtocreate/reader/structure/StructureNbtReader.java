package dev.schemtocreate.reader.structure;

import dev.schemtocreate.blockstate.BlockState;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads vanilla structure files — the {@code .nbt} format Create itself loads and saves.
 *
 * <p>Accepting the output format as input lets an existing Create schematic be re-processed:
 * shrunk with {@code --skip-air} or {@code --hollow}, or have blocks substituted. Positions
 * a structure does not list are not air — they leave the world untouched when placed — so
 * they are mapped to {@link BlockState#ABSENT} and never written back out.
 */
public final class StructureNbtReader implements SchematicReader {

    private static final Logger LOG = LoggerFactory.getLogger(StructureNbtReader.class);

    private final NbtLimits limits;

    public StructureNbtReader() {
        this(NbtLimits.DEFAULT);
    }

    public StructureNbtReader(NbtLimits limits) {
        this.limits = limits;
    }

    @Override
    public String formatName() {
        return "Create / vanilla structure (.nbt)";
    }

    @Override
    public boolean supports(Path path) throws IOException {
        try {
            NbtCompound root = NbtDocument.load(path, 0, limits).root();
            return root.getList("size") != null && root.getList("blocks") != null
                    && (root.getList("palette") != null || root.getList("palettes") != null);
        } catch (IOException e) {
            LOG.debug("{} is not a structure file: {}", path.getFileName(), e.getMessage());
            return false;
        }
    }

    @Override
    public Structure read(Path path) throws IOException {
        NbtCompound root = NbtDocument.load(path, NbtDocument.DEFAULT_INLINE_THRESHOLD, limits).root();
        int[] size = root.getIntTriple("size");
        if (size == null || size[0] < 0 || size[1] < 0 || size[2] < 0) {
            throw new SchematicFormatException("Missing or invalid 'size'");
        }
        Region region = Region.of(size[0], size[1], size[2]);

        List<BlockState> palette = readPalette(root);
        int absent = palette.size();
        palette.add(BlockState.ABSENT);

        int[] indices = new int[Math.toIntExact(region.volume())];
        Arrays.fill(indices, absent);
        Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
        int skipped = 0;
        for (NbtCompound block : root.getList("blocks").compounds()) {
            int[] pos = block.getIntTriple("pos");
            int state = block.getInt("state", -1);
            if (pos == null || state < 0 || state >= absent) {
                skipped++;
                continue;
            }
            BlockPos blockPos = new BlockPos(pos[0], pos[1], pos[2]);
            if (!region.contains(blockPos)) {
                skipped++;
                continue;
            }
            indices[(int) region.indexOf(pos[0], pos[1], pos[2])] = state;
            NbtCompound nbt = block.getCompound("nbt");
            if (nbt != null && nbt.getString("id", null) != null) {
                NbtCompound payload = nbt.copy();
                String id = payload.getString("id", null);
                payload.remove("id");
                payload.remove("x");
                payload.remove("y");
                payload.remove("z");
                blockEntities.put(blockPos, new BlockEntity(blockPos, id, payload));
            }
        }
        if (skipped > 0) {
            LOG.warn("Skipped {} malformed or out-of-bounds block entries", skipped);
        }

        return Structure.builder()
                .region(region)
                .palette(new ArrayPalette(palette.toArray(new BlockState[0])))
                .blockIndices(new ArrayBlockIndexSource(indices))
                .blockEntities(blockEntities)
                .entities(readEntities(root))
                .metadata(SchematicMetadata.empty())
                .dataVersion(root.getInt("DataVersion", 0))
                .build();
    }

    private static List<BlockState> readPalette(NbtCompound root) throws SchematicFormatException {
        NbtList list = root.getList("palette");
        if (list == null) {
            // Multi-variant structures (shipwrecks and the like): the first variant is the default.
            NbtList variants = root.getList("palettes");
            if (variants == null || variants.isEmpty() || !(variants.get(0) instanceof NbtList first)) {
                throw new SchematicFormatException("Structure has no palette");
            }
            list = first;
        }
        List<BlockState> states = new ArrayList<>(list.size() + 1);
        for (NbtCompound entry : list.compounds()) {
            String name = entry.getString("Name", BlockState.AIR_NAME);
            Map<String, String> properties = new LinkedHashMap<>();
            NbtCompound raw = entry.getCompound("Properties");
            if (raw != null) {
                for (Map.Entry<String, NbtTag> property : raw.entries()) {
                    if (property.getValue() instanceof NbtString value) {
                        properties.put(property.getKey(), value.value());
                    }
                }
            }
            states.add(new BlockState(name, properties));
        }
        return states;
    }

    private static List<Entity> readEntities(NbtCompound root) {
        NbtList list = root.getList("entities");
        List<Entity> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        for (NbtCompound entry : list.compounds()) {
            double[] pos = entry.getDoubleTriple("pos");
            NbtCompound nbt = entry.getCompound("nbt");
            String id = nbt == null ? null : nbt.getString("id", null);
            if (pos == null || id == null) {
                continue;
            }
            NbtCompound payload = nbt.copy();
            payload.remove("id");
            result.add(new Entity(new Vec3d(pos[0], pos[1], pos[2]), id, payload));
        }
        return result;
    }
}
