package dev.schemtocreate.entities;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sign text from Minecraft 1.21.5+ crashes 1.20.1's decoder.
 *
 * <p>1.20.1 decodes each line with {@code FLAT_COMPONENT_CODEC}, which needs a JSON chat
 * component. Newer versions write plain text, so a blank line arrives as {@code ""} —
 * {@code Component.Serializer.fromJson("")} returns null and the resulting NPE aborts
 * placement of the entire structure, not just the sign.
 */
class SignTextCompatibilityTest {

    @Test
    @DisplayName("a blank line becomes an empty text component, not an empty string")
    void wrapsBlankLines() {
        NbtCompound sign = signWith("", "", "", "");

        int repaired = SignTextCompatibility.repair(sign);

        assertThat(repaired).isEqualTo(4);
        assertThat(lines(sign)).containsExactly(
                "{\"text\":\"\"}", "{\"text\":\"\"}", "{\"text\":\"\"}", "{\"text\":\"\"}");
    }

    @Test
    @DisplayName("plain text is wrapped and kept readable")
    void wrapsPlainText() {
        NbtCompound sign = signWith("Taverna", "do", "Lettuce", "");

        SignTextCompatibility.repair(sign);

        assertThat(lines(sign)).containsExactly(
                "{\"text\":\"Taverna\"}", "{\"text\":\"do\"}",
                "{\"text\":\"Lettuce\"}", "{\"text\":\"\"}");
    }

    @Test
    @DisplayName("text already in JSON form is left untouched")
    void leavesJsonAlone() {
        String json = "{\"text\":\"Bem-vindo\",\"color\":\"gold\"}";
        NbtCompound sign = signWith(json, "", "", "");

        int repaired = SignTextCompatibility.repair(sign);

        assertThat(repaired).isEqualTo(3);
        assertThat(lines(sign).get(0)).isEqualTo(json);
    }

    @Test
    @DisplayName("characters that would break the JSON are escaped")
    void escapesJsonSpecials() {
        NbtCompound sign = signWith("say \"hi\"", "back\\slash", "tab\there", "");

        SignTextCompatibility.repair(sign);

        assertThat(lines(sign)).containsExactly(
                "{\"text\":\"say \\\"hi\\\"\"}",
                "{\"text\":\"back\\\\slash\"}",
                "{\"text\":\"tab\\there\"}",
                "{\"text\":\"\"}");
    }

    @Test
    @DisplayName("both sides of the sign are repaired")
    void repairsBothSides() {
        NbtCompound sign = signWith("front", "", "", "");
        sign.put("back_text", new NbtCompound()
                .put("messages", new NbtList()
                        .add(new NbtString("back"))
                        .add(new NbtString(""))));

        int repaired = SignTextCompatibility.repair(sign);

        assertThat(repaired).isEqualTo(6);
        assertThat(sign.getCompound("back_text").getList("messages").get(0))
                .isEqualTo(new NbtString("{\"text\":\"back\"}"));
    }

    @Test
    @DisplayName("only sign block entities are treated as signs")
    void identifiesSigns() {
        assertThat(SignTextCompatibility.isSign("minecraft:sign")).isTrue();
        assertThat(SignTextCompatibility.isSign("minecraft:hanging_sign")).isTrue();
        assertThat(SignTextCompatibility.isSign("minecraft:chest")).isFalse();
    }

    @Test
    @DisplayName("a sign with no text at all is left alone")
    void toleratesMissingText() {
        assertThat(SignTextCompatibility.repair(new NbtCompound())).isZero();
        assertThat(SignTextCompatibility.repair(
                new NbtCompound().put("front_text", new NbtCompound()))).isZero();
    }

    private static NbtCompound signWith(String... messages) {
        NbtList list = new NbtList();
        for (String message : messages) {
            list.add(new NbtString(message));
        }
        return new NbtCompound().put("front_text", new NbtCompound()
                .putString("color", "black")
                .put("messages", list));
    }

    private static java.util.List<String> lines(NbtCompound sign) {
        return sign.getCompound("front_text").getList("messages").elements().stream()
                .map(tag -> ((NbtString) tag).value())
                .toList();
    }
}
