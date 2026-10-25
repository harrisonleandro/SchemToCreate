package dev.schemtocreate.converter;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtIo;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.testutil.SchemFixture;
import dev.schemtocreate.testutil.StructureNbtValidator;
import dev.schemtocreate.util.ProgressListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Splitting an output into parts that each fit a size limit. */
class SplitTest {

    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow",
            "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    @TempDir
    Path temp;

    @Test
    @DisplayName("an oversized output becomes parts that each fit, and together hold every block once")
    void splitsIntoFittingParts() throws IOException {
        Path input = noisyCube(16).entity(1.5, 15.5, 1.5, "minecraft:armor_stand", new NbtCompound())
                .writeV2(temp.resolve("noisy.schem"));
        long limit = 4 * 1024;

        ConversionResult result = convert(input, limit);

        assertThat(result.isSplit()).isTrue();
        assertThat(result.parts()).hasSizeGreaterThan(1);
        assertThat(result.parts().get(0).getFileName()).hasToString("noisy-parte1.nbt");
        assertThat(Files.exists(temp.resolve("noisy.nbt"))).as("no unsplit file is left").isFalse();

        Set<String> positions = new HashSet<>();
        int entities = 0;
        for (int i = 0; i < result.parts().size(); i++) {
            Path part = result.parts().get(i);
            var summary = StructureNbtValidator.validate(part);
            assertThat(summary.fileSizeBytes()).isLessThanOrEqualTo(limit);
            // Every part keeps the full size, so all are placed at the same anchor.
            assertThat(List.of(summary.width(), summary.height(), summary.length())).containsExactly(16, 16, 16);
            entities += summary.entityCount();
            if (i < result.parts().size() - 1) {
                assertThat(summary.entityCount()).as("entities ride in the last part").isZero();
            }
            for (NbtCompound block : blocks(part)) {
                int[] pos = block.getIntTriple("pos");
                assertThat(positions.add(pos[0] + "," + pos[1] + "," + pos[2])).as("written once").isTrue();
            }
        }
        assertThat(positions).hasSize(16 * 16 * 16);
        assertThat(entities).isEqualTo(1);
        assertThat(result.write().blocksWritten()).isEqualTo(16 * 16 * 16);
    }

    @Test
    @DisplayName("parts are bottom first: no part has blocks below the one before it")
    void partsGoBottomUp() throws IOException {
        Path input = noisyCube(16).writeV2(temp.resolve("layers.schem"));

        ConversionResult result = convert(input, 4 * 1024);

        int previousTop = -1;
        for (Path part : result.parts()) {
            int bottom = Integer.MAX_VALUE;
            int top = -1;
            for (NbtCompound block : blocks(part)) {
                int y = block.getIntTriple("pos")[1];
                bottom = Math.min(bottom, y);
                top = Math.max(top, y);
            }
            assertThat(bottom).isGreaterThanOrEqualTo(previousTop);
            previousTop = top;
        }
    }

    @Test
    @DisplayName("an existing unsplit file does not block writing parts beside it")
    void existingWholeFileDoesNotBlockParts() throws IOException {
        Path input = noisyCube(16).writeV2(temp.resolve("again.schem"));
        Files.writeString(temp.resolve("again.nbt"), "an earlier single-file conversion");

        ConversionResult result = convert(input, 4 * 1024);

        assertThat(result.isSplit()).isTrue();
        assertThat(Files.readString(temp.resolve("again.nbt"))).isEqualTo("an earlier single-file conversion");
    }

    @Test
    @DisplayName("an output already under the limit stays a single file")
    void smallOutputIsNotSplit() throws IOException {
        Path input = SchemFixture.of(2, 2, 2).fill(0, 0, 0, 1, 1, 1, "minecraft:stone")
                .writeV2(temp.resolve("small.schem"));

        ConversionResult result = convert(input, 256 * 1024);

        assertThat(result.isSplit()).isFalse();
        assertThat(result.output().getFileName()).hasToString("small.nbt");
        assertThat(Files.exists(result.output())).isTrue();
    }

    private ConversionResult convert(Path input, long limit) throws IOException {
        Path output = temp.resolve(input.getFileName().toString().replace(".schem", ".nbt"));
        return new SchematicConverter(ConversionOptions.defaults().withSplitMaxBytes(limit))
                .convert(input, output, ProgressListener.noop());
    }

    /** Random wool, so the output compresses poorly and needs several parts. */
    private static SchemFixture noisyCube(int size) {
        SchemFixture fixture = SchemFixture.of(size, size, size);
        Random random = new Random(42);
        for (int y = 0; y < size; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    fixture.set(x, y, z, "minecraft:" + COLORS[random.nextInt(COLORS.length)] + "_wool");
                }
            }
        }
        return fixture;
    }

    private static List<NbtCompound> blocks(Path part) throws IOException {
        NbtList blocks = NbtIo.read(part).requireCompound().getList("blocks");
        return blocks.compounds();
    }
}
