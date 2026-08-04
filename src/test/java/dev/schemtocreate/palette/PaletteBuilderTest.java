package dev.schemtocreate.palette;

import dev.schemtocreate.blockstate.BlockState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaletteBuilderTest {

    @Test
    @DisplayName("entries land at the index the source assigned, not their declaration order")
    void respectsDeclaredIndices() {
        Map<String, Integer> mapping = new LinkedHashMap<>();
        mapping.put("minecraft:stone", 2);
        mapping.put("minecraft:air", 0);
        mapping.put("minecraft:dirt", 1);

        Palette palette = PaletteBuilder.fromSpongeMapping(mapping, 3).build();

        assertThat(palette.size()).isEqualTo(3);
        assertThat(palette.state(0).name()).isEqualTo("minecraft:air");
        assertThat(palette.state(1).name()).isEqualTo("minecraft:dirt");
        assertThat(palette.state(2).name()).isEqualTo("minecraft:stone");
    }

    @Test
    @DisplayName("gaps in a sparse palette become air rather than shifting later indices")
    void fillsGapsWithAir() {
        Map<String, Integer> mapping = new LinkedHashMap<>();
        mapping.put("minecraft:stone", 0);
        mapping.put("minecraft:dirt", 3);

        PaletteBuilder builder = PaletteBuilder.fromSpongeMapping(mapping, -1);
        Palette palette = builder.build();

        assertThat(builder.holes()).isEqualTo(2);
        assertThat(palette.size()).isEqualTo(4);
        assertThat(palette.state(1)).isEqualTo(BlockState.AIR);
        assertThat(palette.state(2)).isEqualTo(BlockState.AIR);
        assertThat(palette.state(3).name()).isEqualTo("minecraft:dirt");
    }

    @Test
    @DisplayName("PaletteMax extends the palette beyond the highest used index")
    void honoursDeclaredPaletteMax() {
        Map<String, Integer> mapping = Map.of("minecraft:stone", 0);

        Palette palette = PaletteBuilder.fromSpongeMapping(mapping, 5).build();

        assertThat(palette.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("a large palette is handled without special casing")
    void handlesLargePalettes() {
        Map<String, Integer> mapping = new LinkedHashMap<>();
        for (int i = 0; i < 5000; i++) {
            mapping.put("minecraft:stone[variant=" + i + "]", i);
        }

        Palette palette = PaletteBuilder.fromSpongeMapping(mapping, 5000).build();

        assertThat(palette.size()).isEqualTo(5000);
        assertThat(palette.state(4999).property("variant")).isEqualTo("4999");
    }

    @Test
    @DisplayName("a negative index is a corrupt file, not something to work around")
    void rejectsNegativeIndices() {
        Map<String, Integer> mapping = Map.of("minecraft:stone", -1);

        assertThatThrownBy(() -> PaletteBuilder.fromSpongeMapping(mapping, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Negative palette index");
    }

    @Test
    @DisplayName("reading outside the palette fails loudly")
    void rejectsOutOfRangeLookups() {
        Palette palette = ArrayPalette.of(BlockState.AIR);

        assertThatThrownBy(() -> palette.state(1))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
