package dev.schemtocreate.gui;

import dev.schemtocreate.testutil.SchemFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window's non-visual logic.
 *
 * <p>Runs headless (see the {@code test} task), so it covers the queue model and the launch
 * decision without ever creating a window — which also means it stays runnable on a build
 * machine with no display.
 */
class GuiModelTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("only schematic files are queued")
    void ignoresUnrelatedFiles() throws IOException {
        Path schematic = fixture("house.schem");
        Path text = Files.writeString(temp.resolve("notes.txt"), "not a schematic");
        Path nbt = Files.writeString(temp.resolve("already.nbt"), "output, not input");

        FileQueueModel model = new FileQueueModel();
        int added = model.add(List.of(schematic, text, nbt));

        assertThat(added).isEqualTo(1);
        assertThat(model.getRowCount()).isEqualTo(1);
        assertThat(model.getValueAt(0, 0)).isEqualTo("house.schem");
    }

    @Test
    @DisplayName("dropping a folder queues every schematic inside it, at any depth")
    void expandsDroppedFolders() throws IOException {
        Files.createDirectories(temp.resolve("builds/medieval"));
        fixture("builds/a.schem");
        fixture("builds/medieval/b.schem");
        Files.writeString(temp.resolve("builds/readme.txt"), "ignored");

        FileQueueModel model = new FileQueueModel();
        int added = model.add(List.of(temp.resolve("builds")));

        assertThat(added).isEqualTo(2);
    }

    @Test
    @DisplayName("dropping the same files twice does not duplicate rows")
    void deduplicates() throws IOException {
        Path schematic = fixture("dup.schem");
        FileQueueModel model = new FileQueueModel();

        assertThat(model.add(List.of(schematic))).isEqualTo(1);
        assertThat(model.add(List.of(schematic))).isZero();
        assertThat(model.getRowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the status column reports the row's state and its message")
    void rendersStatus() throws IOException {
        FileQueueModel model = new FileQueueModel();
        model.add(List.of(fixture("status.schem")));
        QueuedFile row = model.row(0);

        assertThat(model.getValueAt(0, 2)).isEqualTo("Na fila");
        assertThat(row.isPending()).isTrue();

        row.markFailed("arquivo corrompido");
        assertThat(model.getValueAt(0, 2)).isEqualTo("Erro — arquivo corrompido");
        assertThat(row.isPending()).isFalse();
    }

    @Test
    @DisplayName("clearing and resetting behave as the buttons promise")
    void clearAndReset() throws IOException {
        FileQueueModel model = new FileQueueModel();
        model.add(List.of(fixture("one.schem"), fixture("two.schem")));
        model.row(0).markFailed("boom");

        model.resetStatuses();
        assertThat(model.row(0).isPending()).isTrue();
        assertThat(model.getRowCount()).isEqualTo(2);

        model.clear();
        assertThat(model.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a file re-added after clearing is accepted again")
    void clearAlsoForgetsSeenPaths() throws IOException {
        Path schematic = fixture("again.schem");
        FileQueueModel model = new FileQueueModel();
        model.add(List.of(schematic));
        model.clear();

        assertThat(model.add(List.of(schematic))).isEqualTo(1);
    }

    @Test
    @DisplayName("command line arguments never trigger the window")
    void argumentsSuppressTheGui() {
        assertThat(GuiLauncher.shouldLaunch(new String[]{"house.schem"})).isFalse();
        assertThat(GuiLauncher.shouldLaunch(new String[]{"--help"})).isFalse();
    }

    @Test
    @DisplayName("without a display the window is not opened, and --gui says why")
    void headlessIsHandled() {
        assertThat(GuiLauncher.isDisplayAvailable()).isFalse();
        assertThat(GuiLauncher.shouldLaunch(new String[0])).isFalse();
        assertThat(GuiLauncher.wasRequestedWithoutDisplay(new String[]{"--gui"})).isTrue();
        assertThat(GuiLauncher.wasRequestedWithoutDisplay(new String[]{"house.schem"})).isFalse();
    }

    @Test
    @DisplayName("looking for the Minecraft folder never throws, whatever the machine")
    void minecraftLookupIsSafe() {
        assertThat(MinecraftPaths.schematicsFolder())
                .isNotNull()
                .satisfiesAnyOf(
                        folder -> assertThat(folder).isEmpty(),
                        folder -> assertThat(folder.orElseThrow().getFileName())
                                .hasToString("schematics"));
        assertThat(MinecraftPaths.schematicsFolderExists()).isIn(true, false);
    }

    private Path fixture(String relativePath) throws IOException {
        Path target = temp.resolve(relativePath);
        Files.createDirectories(target.getParent());
        return SchemFixture.of(2, 2, 2).set(0, 0, 0, "minecraft:stone").writeV3(target);
    }
}
