package dev.schemtocreate.reader;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtLimits;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.model.Block;
import dev.schemtocreate.model.BlockEntity;
import dev.schemtocreate.model.BlockPos;
import dev.schemtocreate.model.Structure;
import dev.schemtocreate.reader.sponge.SpongeSchematicReader;
import dev.schemtocreate.testutil.Builds;
import dev.schemtocreate.testutil.SchemFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpongeSchematicReaderTest {

    @TempDir
    Path temp;

    private final SpongeSchematicReader reader = new SpongeSchematicReader();

    @ParameterizedTest(name = "Sponge v{0}")
    @ValueSource(ints = {2, 3})
    @DisplayName("both layouts produce the same internal model")
    void readsBothVersionsIdentically(int version) throws IOException {
        SchemFixture fixture = SchemFixture.of(3, 2, 2)
                .set(0, 0, 0, "minecraft:stone")
                .set(2, 1, 1, "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=true]")
                .offset(-1, 63, -2);

        Structure structure = reader.read(write(fixture, version, "dims"));

        assertThat(structure.region().width()).isEqualTo(3);
        assertThat(structure.region().height()).isEqualTo(2);
        assertThat(structure.region().length()).isEqualTo(2);
        assertThat(structure.region().offset()).isEqualTo(new BlockPos(-1, 63, -2));
        assertThat(structure.blockCount()).isEqualTo(12);
        assertThat(stateAt(structure, 0, 0, 0).name()).isEqualTo("minecraft:stone");
        assertThat(stateAt(structure, 2, 1, 1).toStateString())
                .isEqualTo("minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=true]");
    }

    @ParameterizedTest(name = "Sponge v{0}")
    @ValueSource(ints = {2, 3})
    @DisplayName("block entity payloads survive both the inline (v2) and nested (v3) forms")
    void readsBlockEntities(int version) throws IOException {
        SchemFixture fixture = SchemFixture.of(2, 1, 1).blockEntity(
                1, 0, 0, "minecraft:chest[facing=north,type=single,waterlogged=false]",
                "minecraft:chest",
                new NbtCompound()
                        .putString("CustomName", "{\"text\":\"Loot\"}")
                        .put("Items", new NbtList().add(new NbtCompound()
                                .putByte("Slot", (byte) 0)
                                .putString("id", "minecraft:diamond")
                                .putByte("Count", (byte) 5))));

        Structure structure = reader.read(write(fixture, version, "be"));

        BlockEntity chest = structure.blockEntities().get(new BlockPos(1, 0, 0));
        assertThat(chest).isNotNull();
        assertThat(chest.id()).isEqualTo("minecraft:chest");
        assertThat(chest.data().getString("CustomName", null)).isEqualTo("{\"text\":\"Loot\"}");
        assertThat(chest.data().getList("Items").size()).isEqualTo(1);
        // The envelope keys describe the entry, not the chest.
        assertThat(chest.data().contains("Id")).isFalse();
        assertThat(chest.data().contains("Pos")).isFalse();
    }

    @ParameterizedTest(name = "Sponge v{0}")
    @ValueSource(ints = {2, 3})
    @DisplayName("entities keep their position and payload")
    void readsEntities(int version) throws IOException {
        SchemFixture fixture = SchemFixture.of(2, 2, 2).entity(1.5, 0.0, 1.25,
                "minecraft:armor_stand", new NbtCompound().putByte("Invisible", (byte) 1));

        Structure structure = reader.read(write(fixture, version, "entity"));

        assertThat(structure.entities()).hasSize(1);
        var entity = structure.entities().get(0);
        assertThat(entity.id()).isEqualTo("minecraft:armor_stand");
        assertThat(entity.pos().x()).isEqualTo(1.5);
        assertThat(entity.pos().z()).isEqualTo(1.25);
        assertThat(entity.data().getInt("Invisible", -1)).isEqualTo(1);
    }

    @Test
    @DisplayName("blocks are visited in YZX order with the right palette indices")
    void streamsBlocksInOrder() throws IOException {
        SchemFixture fixture = SchemFixture.of(2, 2, 2)
                .set(0, 0, 0, "minecraft:stone")
                .set(1, 0, 0, "minecraft:dirt")
                .set(0, 0, 1, "minecraft:gravel")
                .set(0, 1, 0, "minecraft:sand");

        Structure structure = reader.read(write(fixture, 3, "order"));

        List<String> names = new ArrayList<>();
        structure.forEachBlock(block -> names.add(block.state().name()));
        assertThat(names).startsWith(
                "minecraft:stone", "minecraft:dirt", "minecraft:gravel", "minecraft:air");
        assertThat(names.get(4)).isEqualTo("minecraft:sand");
        assertThat(names).hasSize(8);
    }

    @Test
    @DisplayName("dimensions above 32767 read as unsigned, not negative")
    void readsUnsignedDimensions() throws IOException {
        // Written straight to NBT: allocating a 40000-wide fixture would be pointless here.
        Path file = temp.resolve("wide.schem");
        NbtCompound schematic = new NbtCompound()
                .putInt("Version", 2)
                .putInt("DataVersion", 3465);
        schematic.putShort("Width", (short) 40000);
        schematic.putShort("Height", (short) 1);
        schematic.putShort("Length", (short) 1);
        schematic.put("Palette", new NbtCompound().putInt("minecraft:air", 0));
        schematic.put("BlockData", new dev.schemtocreate.io.nbt.NbtByteArray(new byte[40000]));
        dev.schemtocreate.io.nbt.NbtIo.writeCompressed(file, "Schematic", schematic);

        Structure structure = reader.read(file);

        assertThat(structure.region().width()).isEqualTo(40000);
        assertThat(structure.blockCount()).isEqualTo(40000);
    }

    @Test
    @DisplayName("streaming the block data yields the same blocks as buffering it")
    void deferredBlockDataMatchesInline() throws IOException {
        Path file = write(Builds.house(), 3, "house");

        Structure buffered = new SpongeSchematicReader(Long.MAX_VALUE, NbtLimits.DEFAULT).read(file);
        Structure streamed = new SpongeSchematicReader(16, NbtLimits.DEFAULT).read(file);

        assertThat(collectStates(streamed)).isEqualTo(collectStates(buffered));
        assertThat(streamed.blockEntities()).isEqualTo(buffered.blockEntities());
    }

    @Test
    @DisplayName("an empty region reads as an empty structure rather than failing")
    void readsEmptySchematic() throws IOException {
        Structure structure = reader.read(write(SchemFixture.of(0, 0, 0), 3, "empty"));

        assertThat(structure.blockCount()).isZero();
        assertThat(structure.region().isEmpty()).isTrue();
        structure.forEachBlock(block -> {
            throw new AssertionError("empty structure yielded " + block);
        });
    }

    @Test
    @DisplayName("a single block schematic is not a special case")
    void readsSingleBlock() throws IOException {
        Structure structure = reader.read(
                write(SchemFixture.of(1, 1, 1).set(0, 0, 0, "minecraft:bedrock"), 2, "one"));

        assertThat(structure.blockCount()).isEqualTo(1);
        assertThat(stateAt(structure, 0, 0, 0).name()).isEqualTo("minecraft:bedrock");
    }

    @Test
    @DisplayName("metadata and DataVersion are carried through")
    void readsMetadata() throws IOException {
        Structure structure = reader.read(
                write(SchemFixture.of(1, 1, 1).dataVersion(3120), 3, "meta"));

        assertThat(structure.dataVersion()).isEqualTo(3120);
        assertThat(structure.metadata().name()).isEqualTo("fixture");
        assertThat(structure.metadata().date()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    @DisplayName("a file that is not a schematic is reported, not silently accepted")
    void rejectsNonSchematic() throws IOException {
        Path file = temp.resolve("other.nbt");
        dev.schemtocreate.io.nbt.NbtIo.writeCompressed(file, "",
                new NbtCompound().putString("hello", "world"));

        assertThat(reader.supports(file)).isFalse();
        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(SchematicFormatException.class)
                .hasMessageContaining("No block data found");
    }

    @Test
    @DisplayName("truncated block data is detected before conversion starts")
    void rejectsTruncatedBlockData() throws IOException {
        Path file = temp.resolve("short.schem");
        NbtCompound schematic = new NbtCompound().putInt("Version", 2);
        schematic.putShort("Width", (short) 4);
        schematic.putShort("Height", (short) 4);
        schematic.putShort("Length", (short) 4);
        schematic.put("Palette", new NbtCompound().putInt("minecraft:air", 0));
        schematic.put("BlockData", new dev.schemtocreate.io.nbt.NbtByteArray(new byte[10]));
        dev.schemtocreate.io.nbt.NbtIo.writeCompressed(file, "Schematic", schematic);

        assertThatThrownBy(() -> reader.read(file))
                .isInstanceOf(SchematicFormatException.class)
                .hasMessageContaining("64 varints");
    }

    @Test
    @DisplayName("the registry recognises schematics by content, not by extension")
    void registryDetectsByContent() throws IOException {
        Path renamed = temp.resolve("no-extension");
        Files.copy(write(SchemFixture.of(1, 1, 1), 3, "detect"), renamed);

        assertThat(SchematicReaderRegistry.withDefaults().detect(renamed).formatName())
                .contains("Sponge");
    }

    private Path write(SchemFixture fixture, int version, String name) throws IOException {
        Path target = temp.resolve(name + "-v" + version + ".schem");
        return version == 3 ? fixture.writeV3(target) : fixture.writeV2(target);
    }

    private static dev.schemtocreate.blockstate.BlockState stateAt(Structure structure,
                                                                   int x, int y, int z)
            throws IOException {
        var found = new dev.schemtocreate.blockstate.BlockState[1];
        BlockPos wanted = new BlockPos(x, y, z);
        structure.forEachBlock(block -> {
            if (block.pos().equals(wanted)) {
                found[0] = block.state();
            }
        });
        assertThat(found[0]).as("block at %s", wanted).isNotNull();
        return found[0];
    }

    private static List<String> collectStates(Structure structure) throws IOException {
        List<String> states = new ArrayList<>();
        structure.forEachBlock((Block block) -> states.add(block.state().toStateString()));
        return states;
    }
}
