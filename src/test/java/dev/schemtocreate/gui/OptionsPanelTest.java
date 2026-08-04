package dev.schemtocreate.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The block substitution field.
 *
 * <p>Constructing an {@link OptionsPanel} builds Swing components but never shows a window,
 * so this runs headless alongside the rest of the suite.
 */
class OptionsPanelTest {

    @Test
    @DisplayName("comma separated pairs parse, with the namespace filled in")
    void parsesPairs() {
        OptionsPanel panel = new OptionsPanel();
        panel.setReplacementText(
                "pale_oak_planks=spruce_planks, stripped_pale_oak_log=stripped_spruce_log");

        assertThat(panel.toConversionOptions().writer().blockReplacements())
                .containsExactly(
                        org.assertj.core.api.Assertions.entry(
                                "minecraft:pale_oak_planks", "minecraft:spruce_planks"),
                        org.assertj.core.api.Assertions.entry(
                                "minecraft:stripped_pale_oak_log", "minecraft:stripped_spruce_log"));
    }

    @Test
    @DisplayName("an explicit namespace is respected, so modded blocks work")
    void keepsExplicitNamespace() {
        OptionsPanel panel = new OptionsPanel();
        panel.setReplacementText("create:cogwheel=minecraft:oak_planks");

        assertThat(panel.toConversionOptions().writer().blockReplacements())
                .containsEntry("create:cogwheel", "minecraft:oak_planks");
    }

    @Test
    @DisplayName("an empty or half-typed field does not block a conversion")
    void toleratesIncompleteInput() {
        OptionsPanel panel = new OptionsPanel();

        panel.setReplacementText("");
        assertThat(panel.toConversionOptions().writer().blockReplacements()).isEmpty();

        // Mid-typing: no '=' yet, and a trailing pair the user has not finished.
        panel.setReplacementText("pale_oak_planks, stripped_pale_oak_log=");
        assertThat(panel.toConversionOptions().writer().blockReplacements()).isEmpty();

        panel.setReplacementText("a=b, garbage, c=d");
        assertThat(panel.toConversionOptions().writer().blockReplacements())
                .containsOnlyKeys("minecraft:a", "minecraft:c");
    }

    @Test
    @DisplayName("surrounding whitespace is ignored")
    void trimsWhitespace() {
        OptionsPanel panel = new OptionsPanel();
        panel.setReplacementText("  pale_oak_planks  =  spruce_planks  ");

        assertThat(panel.toConversionOptions().writer().blockReplacements())
                .containsEntry("minecraft:pale_oak_planks", "minecraft:spruce_planks");
    }
}
