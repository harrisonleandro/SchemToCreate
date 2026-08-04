package dev.schemtocreate.testutil;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtIo;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtWriter;
import dev.schemtocreate.util.Varints;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds real Sponge {@code .schem} files for tests.
 *
 * <p>Written against the specification rather than against this project's reader, so a
 * reader bug cannot hide behind a matching fixture bug. Emits both the v1/v2 layout (fields
 * at the root, payloads spliced into each entry) and the v3 layout (nested {@code Schematic}
 * and {@code Blocks} containers, payloads under {@code Data}).
 */
public final class SchemFixture {

    private final int width;
    private final int height;
    private final int length;
    private final int[] indices;
    private final Map<String, Integer> palette = new LinkedHashMap<>();
    private final List<BlockEntityEntry> blockEntities = new ArrayList<>();
    private final List<EntityEntry> entities = new ArrayList<>();
    private int[] offset = {0, 0, 0};
    private int dataVersion = 3465;

    private record BlockEntityEntry(int x, int y, int z, String id, NbtCompound data) {
    }

    private record EntityEntry(double x, double y, double z, String id, NbtCompound data) {
    }

    private SchemFixture(int width, int height, int length) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.indices = new int[Math.toIntExact((long) width * height * length)];
        indexOfState("minecraft:air");
    }

    public static SchemFixture of(int width, int height, int length) {
        return new SchemFixture(width, height, length);
    }

    public SchemFixture dataVersion(int value) {
        this.dataVersion = value;
        return this;
    }

    public SchemFixture offset(int x, int y, int z) {
        this.offset = new int[]{x, y, z};
        return this;
    }

    public SchemFixture set(int x, int y, int z, String stateString) {
        indices[linearIndex(x, y, z)] = indexOfState(stateString);
        return this;
    }

    public SchemFixture fill(int x0, int y0, int z0, int x1, int y1, int z1, String stateString) {
        int index = indexOfState(stateString);
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    indices[linearIndex(x, y, z)] = index;
                }
            }
        }
        return this;
    }

    /** Sets a block and attaches a block entity to it in one call. */
    public SchemFixture blockEntity(int x, int y, int z, String stateString,
                                    String id, NbtCompound data) {
        set(x, y, z, stateString);
        blockEntities.add(new BlockEntityEntry(x, y, z, id, data));
        return this;
    }

    public SchemFixture entity(double x, double y, double z, String id, NbtCompound data) {
        entities.add(new EntityEntry(x, y, z, id, data));
        return this;
    }

    /** Registers a palette entry without placing it, for large-palette tests. */
    public SchemFixture declareState(String stateString) {
        indexOfState(stateString);
        return this;
    }

    public int paletteSize() {
        return palette.size();
    }

    private int indexOfState(String stateString) {
        return palette.computeIfAbsent(stateString, key -> palette.size());
    }

    /** Sponge stores blocks in YZX order: {@code x + z * Width + y * Width * Length}. */
    private int linearIndex(int x, int y, int z) {
        return x + z * width + y * width * length;
    }

    /**
     * Sponge v1: like v2 but with {@code TileEntities} instead of {@code BlockEntities}, and
     * without {@code DataVersion} or {@code Entities} — both arrived in v2. {@code Offset}
     * and {@code PaletteMax} already exist in v1.
     */
    public Path writeV1(Path target) throws IOException {
        try (OutputStream sink = NbtIo.gzipSink(Files.newOutputStream(target), 6);
             NbtWriter writer = new NbtWriter(sink)) {
            writer.beginRootCompound("Schematic");
            writer.putInt("Version", 1);
            writeMetadataAndDimensions(writer);
            writer.putInt("PaletteMax", palette.size());
            writePalette(writer, "Palette");
            writer.putByteArray("BlockData", encodeBlockData());
            writeBlockEntities(writer, "TileEntities", false);
            writer.endCompound();
        }
        return target;
    }

    public Path writeV2(Path target) throws IOException {
        try (OutputStream sink = NbtIo.gzipSink(Files.newOutputStream(target), 6);
             NbtWriter writer = new NbtWriter(sink)) {
            // v2 keeps the schematic at the root, and names that root "Schematic".
            writer.beginRootCompound("Schematic");
            writer.putInt("Version", 2);
            writeCommonHeader(writer);
            writer.putInt("PaletteMax", palette.size());
            writePalette(writer, "Palette");
            writer.putByteArray("BlockData", encodeBlockData());
            writeBlockEntities(writer, "BlockEntities", false);
            writeEntities(writer, false);
            writer.endCompound();
        }
        return target;
    }

    public Path writeV3(Path target) throws IOException {
        try (OutputStream sink = NbtIo.gzipSink(Files.newOutputStream(target), 6);
             NbtWriter writer = new NbtWriter(sink)) {
            // v3 nests everything under an unnamed root, as Minecraft's own data files do.
            writer.beginRootCompound("");
            writer.beginCompound("Schematic");
            writer.putInt("Version", 3);
            writeCommonHeader(writer);
            writer.beginCompound("Blocks");
            writePalette(writer, "Palette");
            writer.putByteArray("Data", encodeBlockData());
            writeBlockEntities(writer, "BlockEntities", true);
            writer.endCompound();
            writeEntities(writer, true);
            writer.endCompound();
            writer.endCompound();
        }
        return target;
    }

    private void writeCommonHeader(NbtWriter writer) throws IOException {
        writer.putInt("DataVersion", dataVersion);
        writeMetadataAndDimensions(writer);
    }

    private void writeMetadataAndDimensions(NbtWriter writer) throws IOException {
        writer.beginCompound("Metadata");
        writer.putString("Name", "fixture");
        writer.putLong("Date", 1_700_000_000_000L);
        writer.endCompound();
        writer.putShort("Width", (short) width);
        writer.putShort("Height", (short) height);
        writer.putShort("Length", (short) length);
        writer.putIntArray("Offset", offset);
    }

    private void writePalette(NbtWriter writer, String key) throws IOException {
        writer.beginCompound(key);
        for (Map.Entry<String, Integer> entry : palette.entrySet()) {
            writer.putInt(entry.getKey(), entry.getValue());
        }
        writer.endCompound();
    }

    private byte[] encodeBlockData() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(indices.length * 2);
        for (int index : indices) {
            Varints.write(buffer, index);
        }
        return buffer.toByteArray();
    }

    private void writeBlockEntities(NbtWriter writer, String key, boolean nestData) throws IOException {
        writer.beginList(key, dev.schemtocreate.io.nbt.NbtType.COMPOUND, blockEntities.size());
        for (BlockEntityEntry entry : blockEntities) {
            writer.putString("Id", entry.id());
            writer.putIntArray("Pos", entry.x(), entry.y(), entry.z());
            writePayload(writer, entry.data(), nestData);
            writer.endCompound();
        }
    }

    private void writeEntities(NbtWriter writer, boolean nestData) throws IOException {
        writer.beginList("Entities", dev.schemtocreate.io.nbt.NbtType.COMPOUND, entities.size());
        for (EntityEntry entry : entities) {
            writer.putString("Id", entry.id());
            writer.put("Pos", NbtList.ofDoubles(entry.x(), entry.y(), entry.z()));
            writePayload(writer, entry.data(), nestData);
            writer.endCompound();
        }
    }

    private static void writePayload(NbtWriter writer, NbtCompound data, boolean nestData)
            throws IOException {
        if (nestData) {
            writer.put("Data", data);
        } else {
            writer.putAll(data);
        }
    }
}
