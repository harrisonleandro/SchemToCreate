package dev.schemtocreate.converter;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtIo;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtType;
import dev.schemtocreate.testutil.Builds;
import dev.schemtocreate.testutil.SchemFixture;
import dev.schemtocreate.testutil.StructureNbtValidator;
import dev.schemtocreate.util.ProgressListener;
import dev.schemtocreate.writer.CreateWriterOptions;
import dev.schemtocreate.writer.StructureVoidPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Converts real fixtures and checks the result against everything Create's load path needs.
 * {@link StructureNbtValidator} is the gate: it asserts the exact tag types vanilla's
 * {@code StructureTemplate.load} reads, where a mismatch would show up in game as an empty
 * schematic rather than as an error.
 */
class ConversionEndToEndTest {

    @TempDir
    Path temp;

    @ParameterizedTest(name = "Sponge v{0}")
    @ValueSource(ints = {2, 3})
    @DisplayName("a house converts to a file Create can load")
    void convertsHouse(int version) throws IOException {
        Path output = convert(Builds.house(), version, "house");

        var summary = StructureNbtValidator.validate(output);
        assertThat(summary.width()).isEqualTo(9);
        assertThat(summary.height()).isEqualTo(6);
        assertThat(summary.length()).isEqualTo(7);
        assertThat(summary.blockCount()).isEqualTo(9 * 6 * 7);
        assertThat(summary.blockEntityCount()).isEqualTo(17);
        assertThat(summary.entityCount()).isEqualTo(2);
        assertThat(summary.dataVersion()).isEqualTo(3465);
    }

    @Test
    @DisplayName("stateful blocks keep every property through the conversion")
    void preservesBlockStates() throws IOException {
        Path output = convert(Builds.house(), 3, "states");

        List<String> palette = paletteStrings(output);
        assertThat(palette).contains(
                "minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=true]",
                "minecraft:oak_slab[type=double,waterlogged=false]",
                "minecraft:oak_door[facing=east,half=upper,hinge=left,open=false,powered=false]",
                "minecraft:cobblestone_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=tall]",
                "minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=true]",
                "minecraft:stripped_birch_log[axis=x]",
                "minecraft:lantern[hanging=true,waterlogged=false]",
                "minecraft:redstone_wire[east=side,north=up,power=9,south=none,west=side]",
                "minecraft:stone_button[face=wall,facing=north,powered=false]",
                "minecraft:powered_rail[powered=true,shape=east_west,waterlogged=false]",
                "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]",
                "minecraft:oak_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]",
                "minecraft:beehive[facing=north,honey_level=5]",
                "minecraft:respawn_anchor[charges=3]",
                "minecraft:white_banner[rotation=4]");
    }

    @Test
    @DisplayName("block entity payloads arrive intact, with vanilla's lowercase id")
    void preservesBlockEntityPayloads() throws IOException {
        Path output = convert(Builds.house(), 2, "entities");

        Map<String, NbtCompound> byId = blockEntitiesById(output);
        assertThat(byId.keySet()).contains(
                "minecraft:chest", "minecraft:barrel", "minecraft:furnace", "minecraft:smoker",
                "minecraft:blast_furnace", "minecraft:shulker_box", "minecraft:hopper",
                "minecraft:dropper", "minecraft:dispenser", "minecraft:sign", "minecraft:lectern",
                "minecraft:beehive", "minecraft:jukebox", "minecraft:respawn_anchor",
                "minecraft:mob_spawner", "minecraft:banner", "minecraft:campfire");

        NbtCompound chest = byId.get("minecraft:chest");
        assertThat(chest.getList("Items").compounds().get(0).getString("id", null))
                .isEqualTo("minecraft:diamond");

        NbtCompound sign = byId.get("minecraft:sign");
        assertThat(sign.getCompound("front_text").getList("messages").size()).isEqualTo(4);

        NbtCompound spawner = byId.get("minecraft:mob_spawner");
        assertThat(spawner.getCompound("SpawnData").getCompound("entity").getString("id", null))
                .isEqualTo("minecraft:zombie");

        NbtCompound beehive = byId.get("minecraft:beehive");
        assertThat(beehive.getList("Bees").compounds().get(0).getInt("TicksInHive", -1))
                .isEqualTo(100);
    }

    @Test
    @DisplayName("entities carry pos, blockPos and an id-bearing payload")
    void convertsEntities() throws IOException {
        Path output = convert(Builds.house(), 3, "ents");

        NbtList entities = NbtIo.read(output).requireCompound().getList("entities");
        assertThat(entities.size()).isEqualTo(2);
        NbtCompound armorStand = entities.compounds().stream()
                .filter(e -> "minecraft:armor_stand".equals(e.getCompound("nbt").getString("id", "")))
                .findFirst()
                .orElseThrow();
        assertThat(armorStand.getDoubleTriple("pos")).containsExactly(4.5, 1.0, 3.5);
        assertThat(armorStand.getIntTriple("blockPos")).containsExactly(4, 1, 3);
        assertThat(armorStand.getCompound("nbt").getDoubleTriple("Pos"))
                .containsExactly(4.5, 1.0, 3.5);
    }

    @Test
    @DisplayName("--entities false leaves an empty list rather than omitting the key")
    void canDropEntities() throws IOException {
        Path output = convert(Builds.house(), 3, "no-ents",
                ConversionOptions.defaults().withWriter(
                        CreateWriterOptions.defaults().withIncludeEntities(false)));

        StructureNbtValidator.validate(output);
        assertThat(NbtIo.read(output).requireCompound().getList("entities").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("structure void becomes air by default, matching Create's own saver")
    void replacesStructureVoidByDefault() throws IOException {
        SchemFixture fixture = SchemFixture.of(2, 1, 1)
                .set(0, 0, 0, "minecraft:structure_void")
                .set(1, 0, 0, "minecraft:stone");

        Path replaced = convert(fixture, 3, "void-air");
        Path kept = convert(fixture, 3, "void-keep",
                ConversionOptions.defaults().withWriter(
                        CreateWriterOptions.defaults().withStructureVoid(StructureVoidPolicy.KEEP)));

        assertThat(paletteStrings(replaced)).doesNotContain("minecraft:structure_void");
        assertThat(paletteStrings(kept)).contains("minecraft:structure_void");
    }

    @Test
    @DisplayName("--skip-air shrinks the blocks list and still validates")
    void skipsAirOnRequest() throws IOException {
        SchemFixture fixture = SchemFixture.of(4, 4, 4).set(0, 0, 0, "minecraft:stone");

        Path full = convert(fixture, 3, "with-air");
        Path trimmed = convert(fixture, 3, "no-air",
                ConversionOptions.defaults().withWriter(
                        CreateWriterOptions.defaults().withSkipAir(true)));

        assertThat(StructureNbtValidator.validate(full).blockCount()).isEqualTo(64);
        assertThat(StructureNbtValidator.validate(trimmed).blockCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("an empty schematic produces a valid, empty structure")
    void convertsEmptySchematic() throws IOException {
        Path output = convert(SchemFixture.of(0, 0, 0), 3, "empty");

        var summary = StructureNbtValidator.validate(output);
        assertThat(summary.blockCount()).isZero();
        assertThat(summary.entityCount()).isZero();
        assertThat(summary.width()).isZero();
    }

    @Test
    @DisplayName("a single block schematic round trips")
    void convertsSingleBlock() throws IOException {
        Path output = convert(SchemFixture.of(1, 1, 1).set(0, 0, 0, "minecraft:bedrock"), 2, "one");

        var summary = StructureNbtValidator.validate(output);
        assertThat(summary.blockCount()).isEqualTo(1);
        // The source palette declares air at index 0; preserving unused entries keeps block
        // indices identical to the source's, so the palette is carried over as written.
        assertThat(paletteStrings(output)).containsExactly("minecraft:air", "minecraft:bedrock");
    }

    @Test
    @DisplayName("a palette of thousands of states converts without truncation")
    void convertsLargePalette() throws IOException {
        SchemFixture fixture = SchemFixture.of(64, 4, 64);
        for (int i = 0; i < 4000; i++) {
            fixture.declareState("minecraft:light[level=" + (i % 16) + ",waterlogged=" + (i % 2 == 0) + "_" + i + "]");
        }
        fixture.set(0, 0, 0, "minecraft:stone");

        Path output = convert(fixture, 3, "big-palette");

        // Palette indices above 127 need multi-byte varints; a truncating decoder shows up here.
        var summary = StructureNbtValidator.validate(output);
        assertThat(summary.paletteSize()).isEqualTo(fixture.paletteSize());
    }

    @Test
    @DisplayName("a castle converts and every block position is within bounds")
    void convertsCastle() throws IOException {
        Path output = convert(Builds.castle(48), 3, "castle");

        var summary = StructureNbtValidator.validate(output);
        assertThat(summary.blockCount()).isEqualTo(48 * 24 * 48);
        assertThat(summary.blockEntityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a city with many block entities converts intact")
    void convertsCity() throws IOException {
        Path output = convert(Builds.city(64, 500), 3, "city");

        var summary = StructureNbtValidator.validate(output);
        assertThat(summary.blockCount()).isEqualTo(64 * 16 * 64);
        assertThat(summary.blockEntityCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("v2 and v3 sources produce byte-identical structures")
    void versionsProduceEquivalentOutput() throws IOException {
        Path fromV2 = convert(Builds.house(), 2, "cmp-v2");
        Path fromV3 = convert(Builds.house(), 3, "cmp-v3");

        assertThat(NbtIo.read(fromV3).requireCompound())
                .isEqualTo(NbtIo.read(fromV2).requireCompound());
    }

    @Test
    @DisplayName("the source DataVersion is preserved, and can be overridden")
    void handlesDataVersion() throws IOException {
        SchemFixture legacy = SchemFixture.of(1, 1, 1).dataVersion(3120);

        Path preserved = convert(legacy, 3, "dv-keep");
        Path forced = convert(legacy, 3, "dv-force",
                ConversionOptions.defaults().withWriter(
                        CreateWriterOptions.defaults().withDataVersionOverride(3465)));

        assertThat(StructureNbtValidator.validate(preserved).dataVersion()).isEqualTo(3120);
        assertThat(StructureNbtValidator.validate(forced).dataVersion()).isEqualTo(3465);
    }

    @Test
    @DisplayName("an older source names its Minecraft version and says what may break")
    void warnsAboutOlderSource() throws IOException {
        Path input = SchemFixture.of(1, 1, 1).dataVersion(3120).writeV3(temp.resolve("old.schem"));

        ConversionResult result = new SchematicConverter(ConversionOptions.defaults())
                .convert(input, temp.resolve("old.nbt"), ProgressListener.noop());

        assertThat(result.warnings())
                .anyMatch(w -> w.contains("Minecraft 1.19.2") && w.contains("older"))
                .anyMatch(w -> w.contains("No data fixer"));
    }

    @Test
    @DisplayName("a newer source warns that unknown blocks will silently become air")
    void warnsAboutNewerSource() throws IOException {
        // 4440 is what Axiom writes for a current build; the blocks it uses may not exist
        // in the version Create runs on, and vanilla resolves those to air without a word.
        Path input = SchemFixture.of(1, 1, 1).dataVersion(4440).writeV3(temp.resolve("new.schem"));

        ConversionResult result = new SchematicConverter(ConversionOptions.defaults())
                .convert(input, temp.resolve("new.nbt"), ProgressListener.noop());

        assertThat(result.warnings())
                .anyMatch(w -> w.contains("newer") && w.contains("becomes air"))
                .anyMatch(w -> w.contains("--list-palette"));
    }

    @Test
    @DisplayName("--replace swaps a block everywhere and keeps its properties")
    void substitutesBlocks() throws IOException {
        SchemFixture fixture = SchemFixture.of(3, 1, 1)
                .set(0, 0, 0, "minecraft:pale_oak_planks")
                .set(1, 0, 0, "minecraft:stripped_pale_oak_log[axis=x]")
                .set(2, 0, 0, "minecraft:stone");
        Path input = fixture.writeV3(temp.resolve("pale.schem"));
        Path output = temp.resolve("pale.nbt");

        new SchematicConverter(ConversionOptions.defaults().withWriter(
                CreateWriterOptions.defaults().withBlockReplacements(Map.of(
                        "minecraft:pale_oak_planks", "minecraft:spruce_planks",
                        "minecraft:stripped_pale_oak_log", "minecraft:stripped_spruce_log"))))
                .convert(input, output, ProgressListener.noop());

        StructureNbtValidator.validate(output);
        List<String> palette = paletteStrings(output);
        assertThat(palette).noneMatch(entry -> entry.contains("pale_oak"));
        // The axis survives the swap: a stripped log replaced by another stripped log must
        // keep its orientation or the build comes out rotated.
        assertThat(palette).contains(
                "minecraft:spruce_planks",
                "minecraft:stripped_spruce_log[axis=x]",
                "minecraft:stone");
    }

    @Test
    @DisplayName("a substitution for a block that is not present changes nothing")
    void ignoresIrrelevantSubstitutions() throws IOException {
        Path input = SchemFixture.of(1, 1, 1)
                .set(0, 0, 0, "minecraft:stone")
                .writeV3(temp.resolve("plain.schem"));
        Path untouched = temp.resolve("plain-a.nbt");
        Path substituted = temp.resolve("plain-b.nbt");

        new SchematicConverter(ConversionOptions.defaults())
                .convert(input, untouched, ProgressListener.noop());
        new SchematicConverter(ConversionOptions.defaults().withWriter(
                CreateWriterOptions.defaults().withBlockReplacements(
                        Map.of("minecraft:dirt", "minecraft:gravel"))))
                .convert(input, substituted, ProgressListener.noop());

        assertThat(NbtIo.read(substituted).requireCompound())
                .isEqualTo(NbtIo.read(untouched).requireCompound());
    }

    @Test
    @DisplayName("the block usage listing counts every distinct block")
    void reportsBlockUsage() throws IOException {
        SchemFixture fixture = SchemFixture.of(4, 1, 1)
                .set(0, 0, 0, "minecraft:stone")
                .set(1, 0, 0, "minecraft:stone")
                .set(2, 0, 0, "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]");
        Path input = fixture.writeV3(temp.resolve("usage.schem"));

        ConversionResult result = new SchematicConverter(ConversionOptions.defaults())
                .convert(input, temp.resolve("usage.nbt"), ProgressListener.noop());

        assertThat(result.write().blockUsage())
                .extracting(dev.schemtocreate.writer.WriteResult.BlockUsage::name)
                .containsExactly("minecraft:stone", "minecraft:air", "minecraft:oak_stairs");
        assertThat(result.write().blockUsage().get(0).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("an existing output is protected unless --overwrite is given")
    void refusesToOverwriteByDefault() throws IOException {
        Path input = SchemFixture.of(1, 1, 1).writeV3(temp.resolve("in.schem"));
        Path output = temp.resolve("out.nbt");
        Files.writeString(output, "existing");

        assertThatThrownBy(() -> new SchematicConverter(ConversionOptions.defaults())
                .convert(input, output, ProgressListener.noop()))
                .isInstanceOf(SchematicConverter.FileExistsException.class);

        new SchematicConverter(ConversionOptions.defaults().withOverwrite(true))
                .convert(input, output, ProgressListener.noop());
        StructureNbtValidator.validate(output);
    }

    @Test
    @DisplayName("a failed write leaves no partial output behind")
    void doesNotLeavePartialFiles() throws IOException {
        Path input = SchemFixture.of(2, 2, 2).writeV3(temp.resolve("partial.schem"));
        Path corrupted = temp.resolve("corrupted.schem");
        byte[] bytes = Files.readAllBytes(input);
        Files.write(corrupted, java.util.Arrays.copyOf(bytes, bytes.length / 2));
        Path output = temp.resolve("partial.nbt");

        assertThatThrownBy(() -> new SchematicConverter(ConversionOptions.defaults())
                .convert(corrupted, output, ProgressListener.noop()))
                .isInstanceOf(IOException.class);

        assertThat(Files.exists(output)).isFalse();
        try (var listing = Files.list(temp)) {
            assertThat(listing.map(p -> p.getFileName().toString()))
                    .noneMatch(name -> name.endsWith(".part"));
        }
    }

    @Test
    @DisplayName("the default output path replaces the extension with .nbt")
    void derivesOutputPath() {
        assertThat(SchematicConverter.defaultOutputFor(Path.of("builds", "medieval-house.schem")))
                .hasFileName("medieval-house.nbt");
    }

    private Path convert(SchemFixture fixture, int version, String name) throws IOException {
        return convert(fixture, version, name, ConversionOptions.defaults());
    }

    private Path convert(SchemFixture fixture, int version, String name, ConversionOptions options)
            throws IOException {
        Path input = temp.resolve(name + ".schem");
        if (version == 3) {
            fixture.writeV3(input);
        } else {
            fixture.writeV2(input);
        }
        Path output = temp.resolve(name + ".nbt");
        new SchematicConverter(options).convert(input, output, ProgressListener.noop());
        return output;
    }

    private static List<String> paletteStrings(Path output) throws IOException {
        NbtList palette = NbtIo.read(output).requireCompound().getList("palette");
        return palette.compounds().stream().map(ConversionEndToEndTest::toStateString).toList();
    }

    private static String toStateString(NbtCompound entry) {
        String name = entry.getString("Name", "?");
        NbtCompound properties = entry.getCompound("Properties");
        if (properties == null || properties.isEmpty()) {
            return name;
        }
        StringBuilder builder = new StringBuilder(name).append('[');
        boolean first = true;
        for (var property : properties.entries()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(property.getKey()).append('=')
                    .append(((dev.schemtocreate.io.nbt.NbtString) property.getValue()).value());
            first = false;
        }
        return builder.append(']').toString();
    }

    private static Map<String, NbtCompound> blockEntitiesById(Path output) throws IOException {
        NbtList blocks = NbtIo.read(output).requireCompound().getList("blocks");
        java.util.Map<String, NbtCompound> result = new java.util.LinkedHashMap<>();
        for (NbtCompound block : blocks.compounds()) {
            NbtCompound nbt = block.getCompound("nbt");
            if (nbt != null) {
                result.put(nbt.getString("id", "?"), nbt);
            }
        }
        return result;
    }

    @Test
    @DisplayName("the output really is GZIP compressed, as Create's reader requires")
    void outputIsGzipped() throws IOException {
        Path output = convert(SchemFixture.of(2, 2, 2), 3, "gzip");

        assertThat(NbtIo.isGzipped(output)).isTrue();
        try (var in = Files.newInputStream(output)) {
            assertThat(in.read()).isEqualTo(0x1F);
            assertThat(in.read()).isEqualTo(0x8B);
        }
    }

    @Test
    @DisplayName("the root tag is unnamed and holds exactly the keys vanilla writes")
    void rootMatchesVanillaShape() throws IOException {
        Path output = convert(Builds.house(), 3, "shape");

        var named = NbtIo.read(output);
        assertThat(named.name()).isEmpty();
        NbtCompound root = named.requireCompound();
        assertThat(root.keys()).containsExactlyInAnyOrder(
                "size", "palette", "blocks", "entities", "DataVersion");
        assertThat(root.getList("size").elementType()).isEqualTo(NbtType.INT);
        assertThat(root.getList("palette").elementType()).isEqualTo(NbtType.COMPOUND);
        assertThat(root.getList("blocks").elementType()).isEqualTo(NbtType.COMPOUND);
    }
}
