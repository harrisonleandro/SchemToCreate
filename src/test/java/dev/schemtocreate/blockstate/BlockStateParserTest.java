package dev.schemtocreate.blockstate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockStateParserTest {

    @Test
    @DisplayName("a stateless block parses to a bare name")
    void parsesPlainBlock() {
        BlockState state = BlockStateParser.parse("minecraft:stone");

        assertThat(state.name()).isEqualTo("minecraft:stone");
        assertThat(state.properties()).isEmpty();
        assertThat(state.toStateString()).isEqualTo("minecraft:stone");
    }

    @Test
    @DisplayName("properties keep their source order so output matches input")
    void preservesPropertyOrder() {
        String source = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";

        BlockState state = BlockStateParser.parse(source);

        assertThat(state.properties().keySet())
                .containsExactly("facing", "half", "shape", "waterlogged");
        assertThat(state.toStateString()).isEqualTo(source);
    }

    @Test
    @DisplayName("an unqualified name gets the implicit minecraft namespace")
    void addsDefaultNamespace() {
        assertThat(BlockStateParser.parse("stone").name()).isEqualTo("minecraft:stone");
        assertThat(BlockStateParser.parse("create:cogwheel").name()).isEqualTo("create:cogwheel");
    }

    @Test
    @DisplayName("modded blocks and unknown properties parse without a block registry")
    void parsesModdedBlocks() {
        BlockState state = BlockStateParser.parse(
                "create:large_cogwheel[axis=y,waterlogged=true,some_future_property=17]");

        assertThat(state.name()).isEqualTo("create:large_cogwheel");
        assertThat(state.property("some_future_property")).isEqualTo("17");
        assertThat(state.isWaterlogged()).isTrue();
    }

    @Test
    @DisplayName("an empty property list is accepted")
    void acceptsEmptyPropertyList() {
        assertThat(BlockStateParser.parse("minecraft:stone[]").properties()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "minecraft:stone[facing=north", "minecraft:stone[facing]"})
    @DisplayName("malformed state strings are rejected rather than silently repaired")
    void rejectsMalformedInput(String input) {
        assertThatThrownBy(() -> BlockStateParser.parse(input))
                .isInstanceOf(BlockStateFormatException.class);
    }

    @Test
    @DisplayName("equal states compare equal regardless of instance")
    void valueEquality() {
        BlockState first = BlockStateParser.parse("minecraft:oak_slab[type=top,waterlogged=true]");
        BlockState second = new BlockState("minecraft:oak_slab",
                Map.of("type", "top", "waterlogged", "true"));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("air and structure void are recognised")
    void identifiesSpecialBlocks() {
        assertThat(BlockStateParser.parse("minecraft:air").isAir()).isTrue();
        assertThat(BlockStateParser.parse("minecraft:structure_void").isStructureVoid()).isTrue();
        assertThat(BlockStateParser.parse("minecraft:stone").isAir()).isFalse();
    }
}
