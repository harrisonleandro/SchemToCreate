package dev.schemtocreate.testutil;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtDouble;
import dev.schemtocreate.io.nbt.NbtInt;
import dev.schemtocreate.io.nbt.NbtIo;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtReader;
import dev.schemtocreate.io.nbt.NbtString;
import dev.schemtocreate.io.nbt.NbtTag;
import dev.schemtocreate.io.nbt.NbtType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks that a produced {@code .nbt} file satisfies everything Create's load path needs.
 *
 * <p>Create parses schematics with
 * {@code NbtIo.read(new GZIPInputStream(...), new NbtAccounter(0x20000000L))} and hands the
 * result to vanilla {@code StructureTemplate.load}. That method reads each field with a
 * <em>typed</em> accessor — {@code getList("size", 3)} for a list of ints,
 * {@code getList("blocks", 10)} for a list of compounds, {@code getInt("state")} and so on —
 * and a type mismatch is not an error but a silent empty result: the schematic would open in
 * the Schematic Table and contain nothing. So the checks below assert exact tag types, not
 * just presence.
 *
 * <p>Validation streams the {@code blocks} list one entry at a time rather than loading the
 * document. A multi-million block output cannot be held as a tag tree in a test-sized heap —
 * which is the whole reason the writer streams — so a tree-based validator would only ever
 * be able to check the small files.
 */
public final class StructureNbtValidator {

    /** What the file contains, once validated. */
    public record Summary(int width, int height, int length, int paletteSize,
                          int blockCount, int blockEntityCount, int entityCount,
                          int dataVersion, long fileSizeBytes) {
    }

    private final List<String> problems = new ArrayList<>();

    private int[] size = {-1, -1, -1};
    private int paletteSize = -1;
    private int blockCount;
    private int blockEntityCount;
    private int entityCount = -1;
    private int dataVersion = Integer.MIN_VALUE;
    private int highestStateIndex = -1;
    private final int[] highestPosition = {-1, -1, -1};
    private boolean sawNegativePosition;

    private StructureNbtValidator() {
    }

    /**
     * @throws AssertionError with every problem found, if the file would not load correctly
     */
    public static Summary validate(Path file) throws IOException {
        StructureNbtValidator validator = new StructureNbtValidator();
        Summary summary = validator.run(file);
        if (!validator.problems.isEmpty()) {
            throw new AssertionError("Create would reject or mis-load " + file.getFileName()
                    + ":\n  - " + String.join("\n  - ", validator.problems));
        }
        return summary;
    }

    private Summary run(Path file) throws IOException {
        require(NbtIo.isGzipped(file),
                "file is not GZIP compressed; Create opens schematics through a GZIPInputStream");

        try (NbtReader reader = new NbtReader(NbtIo.openDecompressed(file))) {
            byte rootType = reader.readTypeId();
            require(rootType == NbtType.COMPOUND, "root tag is not a TAG_Compound");
            require(reader.readName().isEmpty(),
                    "root tag is named; vanilla structures use an unnamed root");
            readRootEntries(reader);
        }

        crossCheck();
        return new Summary(size[0], size[1], size[2], Math.max(paletteSize, 0), blockCount,
                blockEntityCount, Math.max(entityCount, 0), dataVersion, Files.size(file));
    }

    private void readRootEntries(NbtReader reader) throws IOException {
        byte type;
        while ((type = reader.readTypeId()) != NbtType.END) {
            String key = reader.readName();
            switch (key) {
                case "size" -> readSize(reader, type);
                case "palette" -> readPalette(reader, type);
                case "blocks" -> streamBlocks(reader, type);
                case "entities" -> readEntities(reader, type);
                case "DataVersion" -> readDataVersion(reader, type);
                case "author" -> reader.skipPayload(type);
                case "palettes" -> {
                    problems.add("'palettes' (the multi-variant form) is present; "
                            + "Create expects the single 'palette'");
                    reader.skipPayload(type);
                }
                default -> {
                    problems.add("unexpected root key '" + key + "'");
                    reader.skipPayload(type);
                }
            }
        }
    }

    private void readSize(NbtReader reader, byte type) throws IOException {
        if (type != NbtType.LIST) {
            problems.add("'size' must be a TAG_List, was " + NbtType.name(type));
            reader.skipPayload(type);
            return;
        }
        NbtList list = (NbtList) reader.readPayload(type);
        if (list.size() != 3 || list.elementType() != NbtType.INT) {
            problems.add("'size' must be a TAG_List of exactly 3 TAG_Int");
            return;
        }
        for (int axis = 0; axis < 3; axis++) {
            size[axis] = ((NbtInt) list.get(axis)).value();
            require(size[axis] >= 0, "size[" + axis + "] is negative");
        }
    }

    private void readPalette(NbtReader reader, byte type) throws IOException {
        if (type != NbtType.LIST) {
            problems.add("'palette' must be a TAG_List, was " + NbtType.name(type));
            reader.skipPayload(type);
            return;
        }
        NbtList palette = (NbtList) reader.readPayload(type);
        paletteSize = palette.size();
        if (!palette.isEmpty() && palette.elementType() != NbtType.COMPOUND) {
            problems.add("'palette' must be a TAG_List of TAG_Compound");
            return;
        }
        for (int i = 0; i < palette.size(); i++) {
            checkPaletteEntry((NbtCompound) palette.get(i), i);
        }
    }

    private void checkPaletteEntry(NbtCompound entry, int index) {
        if (!entry.contains("Name", NbtType.STRING)) {
            problems.add("palette[" + index + "] has no TAG_String 'Name'");
            return;
        }
        String name = entry.getString("Name", "");
        require(name.indexOf(':') > 0,
                "palette[" + index + "] name '" + name + "' is not namespaced");
        NbtTag properties = entry.get("Properties");
        if (properties == null) {
            return;
        }
        if (!(properties instanceof NbtCompound compound)) {
            problems.add("palette[" + index + "] 'Properties' is not a TAG_Compound");
            return;
        }
        for (Map.Entry<String, NbtTag> property : compound.entries()) {
            require(property.getValue() instanceof NbtString,
                    "palette[" + index + "] property '" + property.getKey() + "' is "
                            + NbtType.name(property.getValue().typeId())
                            + "; block state properties must be TAG_String");
        }
    }

    /** Reads one entry at a time and keeps only aggregates, so memory stays constant. */
    private void streamBlocks(NbtReader reader, byte type) throws IOException {
        if (type != NbtType.LIST) {
            problems.add("'blocks' must be a TAG_List, was " + NbtType.name(type));
            reader.skipPayload(type);
            return;
        }
        byte elementType = reader.readByteValue();
        blockCount = reader.readIntValue();
        if (blockCount > 0 && elementType != NbtType.COMPOUND) {
            problems.add("'blocks' must be a TAG_List of TAG_Compound, was "
                    + NbtType.name(elementType));
            return;
        }
        int reported = 0;
        for (int i = 0; i < blockCount; i++) {
            NbtCompound entry = (NbtCompound) reader.readPayload(NbtType.COMPOUND);
            // Only the first few failures are worth naming; the rest would be the same fault.
            reported += checkBlockEntry(entry, i, reported < 5) ? 1 : 0;
        }
    }

    /** @return true if a problem was recorded for this entry */
    private boolean checkBlockEntry(NbtCompound entry, int index, boolean report) {
        int before = problems.size();
        NbtList pos = entry.getList("pos");
        if (pos == null || pos.size() != 3 || pos.elementType() != NbtType.INT) {
            if (report) {
                problems.add("blocks[" + index + "] 'pos' must be a TAG_List of 3 TAG_Int");
            }
            return true;
        }
        for (int axis = 0; axis < 3; axis++) {
            int value = ((NbtInt) pos.get(axis)).value();
            sawNegativePosition |= value < 0;
            highestPosition[axis] = Math.max(highestPosition[axis], value);
        }
        if (!entry.contains("state", NbtType.INT)) {
            if (report) {
                problems.add("blocks[" + index + "] has no TAG_Int 'state'");
            }
            return true;
        }
        highestStateIndex = Math.max(highestStateIndex, entry.getInt("state", -1));
        checkAttachedBlockEntity(entry, index, report);
        return problems.size() > before;
    }

    private void checkAttachedBlockEntity(NbtCompound entry, int index, boolean report) {
        NbtTag nbt = entry.get("nbt");
        if (nbt == null) {
            return;
        }
        blockEntityCount++;
        if (!(nbt instanceof NbtCompound compound)) {
            if (report) {
                problems.add("blocks[" + index + "] 'nbt' is not a TAG_Compound");
            }
            return;
        }
        if (!compound.contains("id", NbtType.STRING) && report) {
            problems.add("blocks[" + index + "] block entity has no TAG_String 'id'");
        }
        for (String stale : new String[]{"x", "y", "z"}) {
            if (compound.contains(stale) && report) {
                problems.add("blocks[" + index + "] block entity still carries a stale '"
                        + stale + "'");
            }
        }
    }

    private void readEntities(NbtReader reader, byte type) throws IOException {
        if (type != NbtType.LIST) {
            problems.add("'entities' must be a TAG_List, was " + NbtType.name(type));
            reader.skipPayload(type);
            return;
        }
        byte elementType = reader.readByteValue();
        entityCount = reader.readIntValue();
        if (entityCount > 0 && elementType != NbtType.COMPOUND) {
            problems.add("'entities' must be a TAG_List of TAG_Compound");
            return;
        }
        for (int i = 0; i < entityCount; i++) {
            checkEntity((NbtCompound) reader.readPayload(NbtType.COMPOUND), i);
        }
    }

    private void checkEntity(NbtCompound entry, int index) {
        NbtList pos = entry.getList("pos");
        NbtList blockPos = entry.getList("blockPos");
        require(pos != null && pos.size() == 3 && pos.elementType() == NbtType.DOUBLE,
                "entities[" + index + "] 'pos' must be a TAG_List of 3 TAG_Double");
        require(blockPos != null && blockPos.size() == 3 && blockPos.elementType() == NbtType.INT,
                "entities[" + index + "] 'blockPos' must be a TAG_List of 3 TAG_Int");
        if (!(entry.get("nbt") instanceof NbtCompound payload)) {
            problems.add("entities[" + index + "] 'nbt' must be a TAG_Compound");
            return;
        }
        require(payload.contains("id", NbtType.STRING),
                "entities[" + index + "] payload has no TAG_String 'id'; EntityType.create would fail");
        if (pos != null && pos.size() == 3 && blockPos != null && blockPos.size() == 3) {
            checkBlockPosMatchesPos(pos, blockPos, index);
        }
    }

    private void checkBlockPosMatchesPos(NbtList pos, NbtList blockPos, int index) {
        for (int axis = 0; axis < 3; axis++) {
            double exact = ((NbtDouble) pos.get(axis)).value();
            int block = ((NbtInt) blockPos.get(axis)).value();
            require(block == (int) Math.floor(exact),
                    "entities[" + index + "] blockPos[" + axis + "]=" + block
                            + " is not floor(pos[" + axis + "]=" + exact + ")");
        }
    }

    private void readDataVersion(NbtReader reader, byte type) throws IOException {
        if (type != NbtType.INT) {
            problems.add("'DataVersion' must be a TAG_Int, was " + NbtType.name(type));
            reader.skipPayload(type);
            return;
        }
        dataVersion = reader.readIntValue();
    }

    /** Checks the relationships between sections, which are only known once all are read. */
    private void crossCheck() {
        require(size[0] >= 0, "'size' is missing");
        require(paletteSize >= 0, "'palette' is missing");
        require(entityCount >= 0,
                "'entities' is missing; StructureTemplate.load expects the key to exist");
        require(dataVersion != Integer.MIN_VALUE, "'DataVersion' is missing");
        require(!sawNegativePosition, "at least one block position is negative");
        require(highestStateIndex < paletteSize,
                "highest block state index " + highestStateIndex
                        + " is outside the palette (size " + paletteSize + ")");
        for (int axis = 0; axis < 3; axis++) {
            require(highestPosition[axis] < size[axis],
                    "highest position on axis " + axis + " is " + highestPosition[axis]
                            + ", outside the declared size " + size[axis]);
        }
    }

    private void require(boolean condition, String problem) {
        if (!condition) {
            problems.add(problem);
        }
    }
}
