package dev.schemtocreate.testutil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the sample {@code .schem} files under {@code examples/}.
 *
 * <p>Run with {@code ./gradlew generateExamples}. Lives in the test source set because it
 * reuses {@link SchemFixture}, which is a specification-driven Sponge writer built for
 * tests; the shipped tool only ever reads that format.
 */
public final class ExampleGenerator {

    private ExampleGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path directory = Path.of(args.length > 0 ? args[0] : "examples");
        Files.createDirectories(directory);

        write(directory.resolve("medieval-house.schem"), Builds.house(), 3);
        write(directory.resolve("medieval-house-v2.schem"), Builds.house(), 2);
        write(directory.resolve("medieval-house-v1.schem"), Builds.house(), 1);
        write(directory.resolve("castle.schem"), Builds.castle(48), 3);
        write(directory.resolve("city-block.schem"), Builds.city(48, 200), 3);
        write(directory.resolve("empty.schem"), SchemFixture.of(0, 0, 0), 3);
        write(directory.resolve("single-block.schem"),
                SchemFixture.of(1, 1, 1).set(0, 0, 0, "minecraft:bedrock"), 3);

        System.out.println("Examples written to " + directory.toAbsolutePath());
    }

    private static void write(Path target, SchemFixture fixture, int version) throws IOException {
        Files.deleteIfExists(target);
        switch (version) {
            case 1 -> fixture.writeV1(target);
            case 2 -> fixture.writeV2(target);
            default -> fixture.writeV3(target);
        }
        System.out.printf("  %-28s %,d bytes (Sponge v%d)%n",
                target.getFileName(), Files.size(target), version);
    }
}
