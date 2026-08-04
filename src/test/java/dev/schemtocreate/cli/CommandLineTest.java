package dev.schemtocreate.cli;

import dev.schemtocreate.testutil.SchemFixture;
import dev.schemtocreate.testutil.StructureNbtValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommandLineTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("a single input writes a sibling .nbt")
    void convertsSingleFile() throws IOException {
        Path input = fixture("house.schem");

        assertThat(Main.run(new String[]{input.toString()})).isZero();

        Path output = temp.resolve("house.nbt");
        assertThat(output).exists();
        StructureNbtValidator.validate(output);
    }

    @Test
    @DisplayName("two paths are read as input then output")
    void acceptsExplicitOutputPositional() throws IOException {
        Path input = fixture("in.schem");
        Path output = temp.resolve("custom-name.nbt");

        assertThat(Main.run(new String[]{input.toString(), output.toString()})).isZero();

        StructureNbtValidator.validate(output);
        assertThat(temp.resolve("in.nbt")).doesNotExist();
    }

    @Test
    @DisplayName("two schematic inputs are both converted, not treated as input/output")
    void treatsTwoSchematicsAsTwoInputs() throws IOException {
        Path first = fixture("a.schem");
        Path second = fixture("b.schem");

        assertThat(Main.run(new String[]{first.toString(), second.toString()})).isZero();

        assertThat(temp.resolve("a.nbt")).exists();
        assertThat(temp.resolve("b.nbt")).exists();
    }

    @Test
    @DisplayName("a glob is expanded by the tool, which Windows shells do not do")
    void expandsGlobs() throws IOException {
        fixture("one.schem");
        fixture("two.schem");
        fixture("three.schem");

        int exit = Main.run(new String[]{glob("*.schem")});

        assertThat(exit).isZero();
        assertThat(temp.resolve("one.nbt")).exists();
        assertThat(temp.resolve("two.nbt")).exists();
        assertThat(temp.resolve("three.nbt")).exists();
    }

    @Test
    @DisplayName("--recursive walks subdirectories")
    void walksDirectoriesRecursively() throws IOException {
        Files.createDirectories(temp.resolve("builds/medieval"));
        fixture("builds/top.schem");
        fixture("builds/medieval/nested.schem");

        int exit = Main.run(new String[]{"--recursive", temp.resolve("builds").toString()});

        assertThat(exit).isZero();
        assertThat(temp.resolve("builds/top.nbt")).exists();
        assertThat(temp.resolve("builds/medieval/nested.nbt")).exists();
    }

    @Test
    @DisplayName("without --recursive only the top level of a directory is converted")
    void nonRecursiveStopsAtTopLevel() throws IOException {
        Files.createDirectories(temp.resolve("flat/deeper"));
        fixture("flat/top.schem");
        fixture("flat/deeper/ignored.schem");

        assertThat(Main.run(new String[]{temp.resolve("flat").toString()})).isZero();

        assertThat(temp.resolve("flat/top.nbt")).exists();
        assertThat(temp.resolve("flat/deeper/ignored.nbt")).doesNotExist();
    }

    @Test
    @DisplayName("--output-dir collects results in one place")
    void honoursOutputDirectory() throws IOException {
        fixture("x.schem");
        fixture("y.schem");
        Path outputDir = temp.resolve("converted");

        int exit = Main.run(new String[]{
                glob("*.schem"), "--output-dir", outputDir.toString()});

        assertThat(exit).isZero();
        assertThat(outputDir.resolve("x.nbt")).exists();
        assertThat(outputDir.resolve("y.nbt")).exists();
    }

    @Test
    @DisplayName("an existing output is skipped without --overwrite, and replaced with it")
    void respectsOverwriteFlag() throws IOException {
        Path input = fixture("over.schem");
        Path output = temp.resolve("over.nbt");
        Files.writeString(output, "stale");

        assertThat(Main.run(new String[]{input.toString()})).isZero();
        assertThat(Files.readString(output)).isEqualTo("stale");

        assertThat(Main.run(new String[]{input.toString(), "--overwrite"})).isZero();
        StructureNbtValidator.validate(output);
    }

    @Test
    @DisplayName("--dry-run resolves paths without writing anything")
    void dryRunWritesNothing() throws IOException {
        Path input = fixture("dry.schem");

        assertThat(Main.run(new String[]{input.toString(), "--dry-run"})).isZero();

        assertThat(temp.resolve("dry.nbt")).doesNotExist();
    }

    @Test
    @DisplayName("--threads converts a batch in parallel with the same results")
    void convertsInParallel() throws IOException {
        for (int i = 0; i < 12; i++) {
            fixture("batch-" + i + ".schem");
        }

        int exit = Main.run(new String[]{
                glob("batch-*.schem"), "--threads", "4"});

        assertThat(exit).isZero();
        for (int i = 0; i < 12; i++) {
            StructureNbtValidator.validate(temp.resolve("batch-" + i + ".nbt"));
        }
    }

    @Test
    @DisplayName("every documented flag is accepted together")
    void acceptsAllDocumentedFlags() throws IOException {
        Path input = fixture("flags.schem");

        int exit = Main.run(new String[]{
                input.toString(),
                "--overwrite", "--verbose", "--debug", "--benchmark",
                "--threads", "2", "--entities", "false",
                "--skip-air", "--structure-void", "keep",
                "--compression", "9", "--author", "tester",
                "--data-version", "3465", "--stream-threshold", "1"});

        assertThat(exit).isZero();
        StructureNbtValidator.validate(temp.resolve("flags.nbt"));
    }

    @Test
    @DisplayName("a missing input is an error, not a silent success")
    void reportsMissingInput() {
        assertThat(Main.run(new String[]{temp.resolve("absent.schem").toString()}))
                .isEqualTo(ConvertCommand.EXIT_FAILED);
    }

    @Test
    @DisplayName("an invalid option value is rejected with the usage exit code")
    void rejectsInvalidOptionValue() throws IOException {
        Path input = fixture("bad.schem");

        assertThat(Main.run(new String[]{input.toString(), "--compression", "42"}))
                .isEqualTo(ConvertCommand.EXIT_USAGE);
    }

    @Test
    @DisplayName("one bad file in a batch does not stop the others")
    void continuesAfterAFailure() throws IOException {
        fixture("good-1.schem");
        fixture("good-2.schem");
        Files.writeString(temp.resolve("good-3.schem"), "not a schematic at all");

        int exit = Main.run(new String[]{glob("good-*.schem")});

        assertThat(exit).isEqualTo(ConvertCommand.EXIT_FAILED);
        assertThat(temp.resolve("good-1.nbt")).exists();
        assertThat(temp.resolve("good-2.nbt")).exists();
    }

    @Test
    @DisplayName("no arguments prints usage rather than doing something surprising")
    void noArgumentsShowsUsage() {
        assertThat(Main.run(new String[0])).isEqualTo(ConvertCommand.EXIT_USAGE);
    }

    @Test
    @DisplayName("--help and --version succeed")
    void helpAndVersion() {
        assertThat(Main.run(new String[]{"--help"})).isZero();
        assertThat(Main.run(new String[]{"--version"})).isZero();
    }

    /** Builds a glob argument by hand: {@code Path.resolve} rejects '*' on Windows. */
    private String glob(String pattern) {
        return temp.toString() + java.io.File.separator + pattern;
    }

    private Path fixture(String relativePath) throws IOException {
        Path target = temp.resolve(relativePath);
        Files.createDirectories(target.getParent());
        return SchemFixture.of(4, 3, 4)
                .fill(0, 0, 0, 3, 0, 3, "minecraft:stone")
                .set(1, 1, 1, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
                .blockEntity(2, 1, 2, "minecraft:chest[facing=north,type=single,waterlogged=false]",
                        "minecraft:chest", new dev.schemtocreate.io.nbt.NbtCompound())
                .writeV3(target);
    }
}
