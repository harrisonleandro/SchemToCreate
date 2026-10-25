package dev.schemtocreate.converter;

import dev.schemtocreate.io.nbt.NbtByteArray;
import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtIo;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtString;
import dev.schemtocreate.testutil.SchemFixture;
import dev.schemtocreate.testutil.StructureNbtValidator;
import dev.schemtocreate.util.ProgressListener;
import dev.schemtocreate.writer.CreateWriterOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Legacy {@code .schematic} input, {@code .nbt} input, hidden block removal and the
 * post-1.20.1 rename table.
 */
class InputFormatsAndHollowTest {

    @TempDir
    Path temp;

    // ------------------------------------------------------------------ legacy .schematic

    @Test
    @DisplayName("a pre-1.13 schematic converts to flattened block states")
    void legacySchematicIsFlattened() throws IOException {
        Path input = legacy(3, 2, 1, new int[][]{
                // x, y, z, id, data
                {0, 0, 0, 109, 2},  // stone brick stairs facing south
                {1, 0, 0, 35, 14},  // red wool
                {2, 0, 0, 64, 1},   // oak door, lower half, facing south
                {2, 1, 0, 64, 9},   // oak door, upper half, hinge right
                {0, 1, 0, 68, 3},   // wall sign facing south
        }, signAt(0, 1, 0, "{\"text\":\"Hi\"}"), null);

        Path output = convert(input, "legacy.nbt", CreateWriterOptions.defaults().withSkipAir(true));

        var summary = StructureNbtValidator.validate(output);
        assertThat(summary.blockCount()).isEqualTo(5);
        assertThat(summary.blockEntityCount()).isEqualTo(1);
        assertThat(summary.dataVersion()).isEqualTo(CreateWriterOptions.DATA_VERSION_1_20_1);
        assertThat(paletteStrings(output)).contains(
                "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
                "minecraft:red_wool",
                "minecraft:oak_door[facing=south,half=lower,hinge=right,open=false,powered=false]",
                "minecraft:oak_door[facing=south,half=upper,hinge=right,open=false,powered=false]",
                "minecraft:oak_wall_sign[facing=south,waterlogged=false]");

        NbtCompound sign = blockEntities(output).get(0);
        assertThat(sign.getString("id", null)).isEqualTo("minecraft:sign");
        assertThat(sign.contains("Text1")).isFalse();
        NbtList messages = sign.getCompound("front_text").getList("messages");
        assertThat(((NbtString) messages.get(0)).value()).isEqualTo("{\"text\":\"Hi\"}");
    }

    @Test
    @DisplayName("a Schematica id mapping is honoured over vanilla numbering")
    void schematicaMappingWins() throws IOException {
        NbtCompound mapping = new NbtCompound();
        mapping.putShort("minecraft:air", (short) 0);
        mapping.putShort("minecraft:glowstone", (short) 1);  // vanilla 1 would be stone
        Path input = legacy(1, 1, 1, new int[][]{{0, 0, 0, 1, 0}}, null, mapping);

        Path output = convert(input, "mapped.nbt", CreateWriterOptions.defaults());

        assertThat(paletteStrings(output)).contains("minecraft:glowstone").doesNotContain("minecraft:stone");
    }

    // ------------------------------------------------------------------ hidden blocks

    @Test
    @DisplayName("--hollow keeps only the shell of a solid cube")
    void hollowKeepsTheShell() throws IOException {
        Path input = SchemFixture.of(5, 5, 5).fill(0, 0, 0, 4, 4, 4, "minecraft:stone")
                .writeV2(temp.resolve("cube.schem"));

        Path output = convert(input, "cube.nbt", CreateWriterOptions.defaults().withRemoveHidden(true));

        // 125 blocks, minus the 3x3x3 core that touches nothing but stone.
        assertThat(StructureNbtValidator.validate(output).blockCount()).isEqualTo(125 - 27);
    }

    @Test
    @DisplayName("--hollow never removes a block next to something see-through")
    void hollowRespectsTransparentNeighbours() throws IOException {
        Path input = SchemFixture.of(3, 3, 3).fill(0, 0, 0, 2, 2, 2, "minecraft:stone")
                .set(1, 2, 1, "minecraft:glass")
                .writeV2(temp.resolve("glass.schem"));

        Path output = convert(input, "glass.nbt", CreateWriterOptions.defaults().withRemoveHidden(true));

        assertThat(StructureNbtValidator.validate(output).blockCount()).isEqualTo(27);
    }

    // ------------------------------------------------------------------ .nbt input

    @Test
    @DisplayName("an .nbt structure can be re-processed, and unlisted positions stay unlisted")
    void structureInputRoundTrips() throws IOException {
        Path schem = SchemFixture.of(3, 1, 1)
                .set(0, 0, 0, "minecraft:stone")
                .set(2, 0, 0, "minecraft:stone")
                .writeV2(temp.resolve("row.schem"));
        Path first = convert(schem, "row.nbt", CreateWriterOptions.defaults().withSkipAir(true));

        // Without --skip-air: the gap was never listed, so it must not come back as air.
        Path second = convert(first, "row-again.nbt", CreateWriterOptions.defaults());

        var summary = StructureNbtValidator.validate(second);
        assertThat(summary.blockCount()).isEqualTo(2);
        assertThat(paletteStrings(second)).noneMatch(state -> state.startsWith("schemtocreate:"));
    }

    @Test
    @DisplayName("converting an .nbt never targets the input file by default")
    void defaultOutputForNbtInputIsDistinct() {
        assertThat(SchematicConverter.defaultOutputFor(temp.resolve("house.nbt")).getFileName())
                .hasToString("house-converted.nbt");
        assertThat(SchematicConverter.defaultOutputFor(temp.resolve("house.schem")).getFileName())
                .hasToString("house.nbt");
    }

    // ------------------------------------------------------------------ renames

    @Test
    @DisplayName("short_grass from 1.20.3+ is written as 1.20.1's grass")
    void shortGrassIsRenamed() throws IOException {
        Path input = SchemFixture.of(1, 1, 1).set(0, 0, 0, "minecraft:short_grass")
                .writeV2(temp.resolve("grass.schem"));

        Path output = convert(input, "grass.nbt", CreateWriterOptions.defaults());

        assertThat(paletteStrings(output)).contains("minecraft:grass").doesNotContain("minecraft:short_grass");
    }

    // ------------------------------------------------------------------ helpers

    private Path convert(Path input, String outputName, CreateWriterOptions writer) throws IOException {
        Path output = temp.resolve(outputName);
        new SchematicConverter(ConversionOptions.defaults().withWriter(writer))
                .convert(input, output, ProgressListener.noop());
        return output;
    }

    /** Writes an MCEdit/Schematica file; {@code blocks} rows are {x, y, z, id, data}. */
    private Path legacy(int width, int height, int length, int[][] blocks,
                        NbtCompound tileEntity, NbtCompound mapping) throws IOException {
        byte[] ids = new byte[width * height * length];
        byte[] data = new byte[ids.length];
        for (int[] block : blocks) {
            int index = (block[1] * length + block[2]) * width + block[0];
            ids[index] = (byte) block[3];
            data[index] = (byte) block[4];
        }
        NbtCompound root = new NbtCompound();
        root.putShort("Width", (short) width);
        root.putShort("Height", (short) height);
        root.putShort("Length", (short) length);
        root.putString("Materials", "Alpha");
        root.put("Blocks", new NbtByteArray(ids));
        root.put("Data", new NbtByteArray(data));
        NbtList tileEntities = new NbtList();
        if (tileEntity != null) {
            tileEntities.add(tileEntity);
        }
        root.put("TileEntities", tileEntities);
        root.put("Entities", new NbtList());
        if (mapping != null) {
            root.put("SchematicaMapping", mapping);
        }
        Path path = temp.resolve("legacy-" + System.nanoTime() + ".schematic");
        NbtIo.writeCompressed(path, "Schematic", root);
        return path;
    }

    private static NbtCompound signAt(int x, int y, int z, String firstLine) {
        NbtCompound sign = new NbtCompound();
        sign.putString("id", "minecraft:sign");
        sign.putInt("x", x);
        sign.putInt("y", y);
        sign.putInt("z", z);
        sign.putString("Text1", firstLine);
        sign.putString("Text2", "{\"text\":\"\"}");
        sign.putString("Text3", "{\"text\":\"\"}");
        sign.putString("Text4", "{\"text\":\"\"}");
        return sign;
    }

    private static List<String> paletteStrings(Path output) throws IOException {
        NbtList palette = NbtIo.read(output).requireCompound().getList("palette");
        return palette.compounds().stream().map(entry -> {
            String name = entry.getString("Name", "?");
            NbtCompound properties = entry.getCompound("Properties");
            if (properties == null || properties.isEmpty()) {
                return name;
            }
            StringBuilder builder = new StringBuilder(name).append('[');
            properties.entries().forEach(property -> builder
                    .append(property.getKey()).append('=')
                    .append(((NbtString) property.getValue()).value()).append(','));
            builder.setLength(builder.length() - 1);
            return builder.append(']').toString();
        }).toList();
    }

    private static List<NbtCompound> blockEntities(Path output) throws IOException {
        NbtList blocks = NbtIo.read(output).requireCompound().getList("blocks");
        return blocks.compounds().stream()
                .map(block -> block.getCompound("nbt"))
                .filter(nbt -> nbt != null)
                .toList();
    }
}
