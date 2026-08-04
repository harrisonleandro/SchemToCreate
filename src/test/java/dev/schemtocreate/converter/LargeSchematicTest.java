package dev.schemtocreate.converter;

import dev.schemtocreate.io.nbt.NbtLimits;
import dev.schemtocreate.model.Structure;
import dev.schemtocreate.reader.sponge.SpongeSchematicReader;
import dev.schemtocreate.testutil.SchemFixture;
import dev.schemtocreate.testutil.StructureNbtValidator;
import dev.schemtocreate.util.ProgressListener;
import dev.schemtocreate.writer.CreateCompatibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the streaming design.
 *
 * <p>The suite runs with a 512 MiB heap (see {@code build.gradle.kts}). A structure of
 * several million blocks cannot be held as a tag tree in that space, so if any of these
 * tests passes it is because the pipeline really is streaming; if someone reintroduces a
 * buffering step, they fail with an OutOfMemoryError rather than merely getting slower.
 */
class LargeSchematicTest {

    // Static so the multi-million block fixture is generated once for the whole class.
    @TempDir
    static Path temp;

    private static Path sharedFixture;

    private static final int WIDTH = 192;
    private static final int HEIGHT = 96;
    private static final int LENGTH = 192;
    private static final long BLOCKS = (long) WIDTH * HEIGHT * LENGTH;

    @Test
    @DisplayName("millions of blocks convert within a heap far smaller than the tag tree would need")
    void convertsMillionsOfBlocks() throws IOException {
        Path input = largeFixture("giant");
        Path output = temp.resolve("giant.nbt");

        long before = usedHeap();
        AtomicLong lastReported = new AtomicLong();
        new SchematicConverter(ConversionOptions.defaults())
                .convert(input, output, (done, total) -> lastReported.set(done));
        long growth = usedHeap() - before;

        var summary = StructureNbtValidator.validate(output);
        assertThat(summary.blockCount()).isEqualTo((int) BLOCKS);
        assertThat(lastReported.get()).isEqualTo(BLOCKS);
        // A materialised blocks list would be several GiB; anything under a few hundred MiB
        // of growth means the data never accumulated.
        assertThat(growth).isLessThan(320L * 1024 * 1024);
    }

    @Test
    @DisplayName("the deferred second-pass path produces the same result as the buffered one")
    void streamedAndBufferedAgree() throws IOException {
        Path input = largeFixture("stream-compare");

        Structure streamed = new SpongeSchematicReader(4096, NbtLimits.DEFAULT).read(input);
        Structure buffered = new SpongeSchematicReader(Long.MAX_VALUE, NbtLimits.DEFAULT).read(input);

        assertThat(streamed.blockCount()).isEqualTo(buffered.blockCount());
        assertThat(checksum(streamed)).isEqualTo(checksum(buffered));
    }

    @Test
    @DisplayName("Create's parse budget is estimated and reported before it is exceeded")
    void reportsCreateParseBudget() throws IOException {
        Path input = largeFixture("budget");
        Path output = temp.resolve("budget.nbt");

        ConversionResult result = new SchematicConverter(ConversionOptions.defaults())
                .convert(input, output, ProgressListener.noop());

        long accounted = CreateCompatibility.estimateAccountedBytes(
                new SpongeSchematicReader().read(input), result.write());
        // 3.5M blocks at roughly 220 accounted bytes each sits above Create's 512 MiB budget,
        // which is exactly the situation a user needs to be told about up front.
        assertThat(accounted).isGreaterThan(CreateCompatibility.NBT_ACCOUNTER_BUDGET_BYTES);
        assertThat(result.warnings())
                .anyMatch(warning -> warning.contains("512 MiB parse limit"));
    }

    @Test
    @DisplayName("byte accounting is 64-bit, not a saturating int")
    void countsBytesBeyondIntRange() throws IOException {
        // DataOutputStream.size() saturates at Integer.MAX_VALUE, which a large structure
        // passes: the reported NBT size would silently freeze at 2 GiB.
        java.io.OutputStream sink = java.io.OutputStream.nullOutputStream();
        long target = 3L * 1024 * 1024 * 1024;
        byte[] chunk = new byte[1 << 20];

        long counted;
        try (var writer = new dev.schemtocreate.io.nbt.NbtWriter(sink)) {
            writer.beginRootCompound("");
            for (long written = 0; written < target; written += chunk.length) {
                writer.putByteArray("chunk", chunk);
            }
            writer.endCompound();
            counted = writer.bytesWritten();
        }

        assertThat(counted).isGreaterThan(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("progress is reported monotonically and reaches the total exactly once")
    void reportsProgress() throws IOException {
        Path input = largeFixture("progress");
        AtomicLong previous = new AtomicLong(-1);
        AtomicLong updates = new AtomicLong();

        new SchematicConverter(ConversionOptions.defaults())
                .convert(input, temp.resolve("progress.nbt"), (done, total) -> {
                    assertThat(done).isGreaterThan(previous.get());
                    assertThat(total).isEqualTo(BLOCKS);
                    previous.set(done);
                    updates.incrementAndGet();
                });

        assertThat(updates.get()).isGreaterThan(1);
        assertThat(previous.get()).isEqualTo(BLOCKS);
    }

    /** A palette above 128 entries forces multi-byte varints across the whole array. */
    private static synchronized Path largeFixture(String name) throws IOException {
        if (sharedFixture != null) {
            return sharedFixture;
        }
        Path input = temp.resolve(name + ".schem");
        SchemFixture fixture = SchemFixture.of(WIDTH, HEIGHT, LENGTH);
        for (int i = 0; i < 200; i++) {
            fixture.declareState("minecraft:stone[filler=" + i + "]");
        }
        fixture.fill(0, 0, 0, WIDTH - 1, 0, LENGTH - 1, "minecraft:bedrock");
        fixture.fill(0, 1, 0, WIDTH - 1, 40, LENGTH - 1, "minecraft:stone[filler=199]");
        fixture.fill(10, 41, 10, WIDTH - 11, 60, LENGTH - 11, "minecraft:oak_planks");
        sharedFixture = fixture.writeV3(input);
        return sharedFixture;
    }

    private static long checksum(Structure structure) throws IOException {
        AtomicLong hash = new AtomicLong(17);
        structure.forEachBlock(block ->
                hash.set(hash.get() * 31 + block.stateIndex() + block.pos().hashCode()));
        return hash.get();
    }

    private static long usedHeap() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
