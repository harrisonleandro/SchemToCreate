package dev.schemtocreate.reader;

import dev.schemtocreate.converter.ConversionOptions;
import dev.schemtocreate.converter.ConversionResult;
import dev.schemtocreate.converter.SchematicConverter;
import dev.schemtocreate.io.nbt.NbtByteArray;
import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtIo;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.model.BlockEntity;
import dev.schemtocreate.model.BlockPos;
import dev.schemtocreate.model.Structure;
import dev.schemtocreate.reader.sponge.SpongeSchematicReader;
import dev.schemtocreate.testutil.SchemFixture;
import dev.schemtocreate.testutil.StructureNbtValidator;
import dev.schemtocreate.util.ProgressListener;
import dev.schemtocreate.writer.CreateWriterOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sponge Schematic v1.
 *
 * <p>Separated from the v2/v3 tests because v1 is not just a renamed v2: it has no
 * {@code DataVersion} and no {@code Entities} list at all, and its block entities live under
 * {@code TileEntities}. Those absences are the interesting part — the reader has to cope
 * with missing keys rather than differently named ones.
 */
class SpongeV1Test {

    @TempDir
    Path temp;

    private final SpongeSchematicReader reader = new SpongeSchematicReader();

    @Test
    @DisplayName("a v1 schematic is detected and read despite having no DataVersion")
    void readsV1Layout() throws IOException {
        Path file = SchemFixture.of(3, 2, 2)
                .offset(-1, 63, -2)
                .set(0, 0, 0, "minecraft:stone")
                .set(2, 1, 1, "minecraft:oak_stairs[facing=south,half=top,shape=straight,waterlogged=true]")
                .writeV1(temp.resolve("legacy.schem"));

        assertThat(reader.supports(file)).isTrue();
        Structure structure = reader.read(file);

        assertThat(structure.region().width()).isEqualTo(3);
        assertThat(structure.region().height()).isEqualTo(2);
        assertThat(structure.region().length()).isEqualTo(2);
        // v1 does carry Offset and PaletteMax; only DataVersion and Entities are absent.
        assertThat(structure.region().offset()).isEqualTo(new BlockPos(-1, 63, -2));
        assertThat(structure.dataVersion()).isZero();
        assertThat(structure.entities()).isEmpty();
    }

    @Test
    @DisplayName("block entities are found under the v1 name 'TileEntities'")
    void readsTileEntities() throws IOException {
        Path file = SchemFixture.of(2, 1, 1)
                .blockEntity(1, 0, 0, "minecraft:chest[facing=north,type=single,waterlogged=false]",
                        "minecraft:chest",
                        new NbtCompound()
                                .putString("CustomName", "{\"text\":\"Old Loot\"}")
                                .put("Items", new NbtList().add(new NbtCompound()
                                        .putByte("Slot", (byte) 0)
                                        .putString("id", "minecraft:gold_ingot")
                                        .putByte("Count", (byte) 12))))
                .writeV1(temp.resolve("tiles.schem"));

        Structure structure = reader.read(file);

        BlockEntity chest = structure.blockEntities().get(new BlockPos(1, 0, 0));
        assertThat(chest).isNotNull();
        assertThat(chest.id()).isEqualTo("minecraft:chest");
        assertThat(chest.data().getString("CustomName", null)).isEqualTo("{\"text\":\"Old Loot\"}");
        assertThat(chest.data().getList("Items").compounds().get(0).getInt("Count", -1))
                .isEqualTo(12);
    }

    @Test
    @DisplayName("a v1 source converts to a file Create can load")
    void convertsV1EndToEnd() throws IOException {
        Path input = SchemFixture.of(4, 3, 4)
                .fill(0, 0, 0, 3, 0, 3, "minecraft:stone_bricks")
                .set(1, 1, 1, "minecraft:oak_stairs[facing=west,half=bottom,shape=inner_left,waterlogged=false]")
                .blockEntity(2, 1, 2, "minecraft:furnace[facing=north,lit=true]",
                        "minecraft:furnace", new NbtCompound().putShort("BurnTime", (short) 200))
                .writeV1(temp.resolve("build.schem"));
        Path output = temp.resolve("build.nbt");

        new SchematicConverter(ConversionOptions.defaults())
                .convert(input, output, ProgressListener.noop());

        var summary = StructureNbtValidator.validate(output);
        assertThat(summary.blockCount()).isEqualTo(48);
        assertThat(summary.blockEntityCount()).isEqualTo(1);
        assertThat(summary.entityCount()).isZero();
        // No source DataVersion means the writer falls back to 1.20.1.
        assertThat(summary.dataVersion()).isEqualTo(CreateWriterOptions.DATA_VERSION_1_20_1);
    }

    @Test
    @DisplayName("a missing DataVersion is not reported as a version mismatch")
    void doesNotWarnAboutAbsentDataVersion() throws IOException {
        Path input = SchemFixture.of(2, 2, 2).writeV1(temp.resolve("quiet.schem"));

        ConversionResult result = new SchematicConverter(ConversionOptions.defaults())
                .convert(input, temp.resolve("quiet.nbt"), ProgressListener.noop());

        assertThat(result.warnings()).noneMatch(warning -> warning.contains("DataVersion"));
    }

    @Test
    @DisplayName("v1, v2 and v3 sources of the same build produce identical output")
    void allVersionsAgree() throws IOException {
        Path fromV1 = convertVia(SchemFixture::writeV1, "v1");
        Path fromV2 = convertVia(SchemFixture::writeV2, "v2");
        Path fromV3 = convertVia(SchemFixture::writeV3, "v3");

        NbtCompound v1 = NbtIo.read(fromV1).requireCompound();
        assertThat(NbtIo.read(fromV2).requireCompound()).isEqualTo(v1);
        assertThat(NbtIo.read(fromV3).requireCompound()).isEqualTo(v1);
    }

    @Test
    @DisplayName("a v1 schematic without a palette is reported, not guessed at")
    void rejectsLegacyNumericIds() throws IOException {
        // Pre-1.13 files store global numeric block IDs and omit Palette entirely.
        Path file = temp.resolve("numeric.schem");
        NbtCompound schematic = new NbtCompound().putInt("Version", 1);
        schematic.putShort("Width", (short) 2);
        schematic.putShort("Height", (short) 1);
        schematic.putShort("Length", (short) 1);
        schematic.put("BlockData", new NbtByteArray(new byte[]{1, 1}));
        NbtIo.writeCompressed(file, "Schematic", schematic);

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(SchematicFormatException.class)
                .hasMessageContaining("pre-1.13 numeric block IDs");
    }

    /** Same build, written through a different specification version each time. */
    private Path convertVia(FixtureWriter write, String label) throws IOException {
        SchemFixture fixture = SchemFixture.of(3, 2, 3)
                .fill(0, 0, 0, 2, 0, 2, "minecraft:cobblestone")
                .set(1, 1, 1, "minecraft:oak_slab[type=top,waterlogged=true]")
                .blockEntity(0, 1, 0, "minecraft:barrel[facing=up,open=false]",
                        "minecraft:barrel", new NbtCompound().putString("CustomName", "shared"));
        Path input = write.write(fixture, temp.resolve(label + ".schem"));
        Path output = temp.resolve(label + ".nbt");
        // v1 has no DataVersion, so pin it to keep the three outputs comparable.
        new SchematicConverter(ConversionOptions.defaults().withWriter(
                CreateWriterOptions.defaults().withDataVersionOverride(3465)))
                .convert(input, output, ProgressListener.noop());
        return output;
    }

    @FunctionalInterface
    private interface FixtureWriter {
        Path write(SchemFixture fixture, Path target) throws IOException;
    }
}
