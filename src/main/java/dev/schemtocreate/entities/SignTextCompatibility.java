package dev.schemtocreate.entities;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtString;
import dev.schemtocreate.io.nbt.NbtTag;

import java.util.Set;

/**
 * Repairs sign text written by Minecraft 1.21.5 or newer so that 1.20.1 can read it.
 *
 * <p>This is the one incompatibility worth special-casing, because of how badly it fails.
 * In 1.20.1 each line of a sign is decoded by {@code ExtraCodecs.FLAT_COMPONENT_CODEC},
 * which expects a <em>JSON chat component</em>: an empty line is the four characters
 * {@code {"text":""}}. From 1.21.5 Minecraft stores components as plain NBT instead, so an
 * empty line is written as the empty string.
 *
 * <p>Feeding {@code ""} to 1.20.1's codec makes {@code Component.Serializer.fromJson} return
 * null, {@code ImmutableList.Builder.add} throws a {@link NullPointerException}, and that
 * exception propagates out of {@code SignBlockEntity.load} through
 * {@code StructureTemplate.placeInWorld} — aborting the placement of the <em>entire
 * structure</em>. One blank sign in a build of twenty thousand blocks is enough to make the
 * whole schematic appear empty, and Create reports only "Failed to load Schematic".
 *
 * <p>So each line that is not already JSON is wrapped into a {@code text} component. Lines
 * that are already JSON are left exactly as they are.
 */
public final class SignTextCompatibility {

    /** Block entity types whose payload carries sign text. */
    private static final Set<String> SIGN_IDS = Set.of(
            "minecraft:sign", "minecraft:hanging_sign");

    private static final String[] TEXT_SIDES = {"front_text", "back_text"};

    private SignTextCompatibility() {
    }

    public static boolean isSign(String blockEntityId) {
        return SIGN_IDS.contains(blockEntityId);
    }

    /**
     * Rewrites the sign's message lines in place.
     *
     * @return the number of lines that had to be converted
     */
    public static int repair(NbtCompound signData) {
        int repaired = 0;
        for (String side : TEXT_SIDES) {
            NbtCompound text = signData.getCompound(side);
            if (text != null) {
                repaired += repairSide(text);
            }
        }
        return repaired;
    }

    private static int repairSide(NbtCompound text) {
        NbtList messages = text.getList("messages");
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        NbtList converted = new NbtList();
        int repaired = 0;
        for (NbtTag line : messages) {
            String raw = line instanceof NbtString string ? string.value() : "";
            if (looksLikeJson(raw)) {
                converted.add(new NbtString(raw));
            } else {
                converted.add(new NbtString(asTextComponent(raw)));
                repaired++;
            }
        }
        if (repaired > 0) {
            text.put("messages", converted);
        }
        return repaired;
    }

    /**
     * A chat component serialises as a JSON object, array or string. Anything else is plain
     * text that 1.20.1 would fail to parse.
     */
    private static boolean looksLikeJson(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        char first = trimmed.charAt(0);
        return first == '{' || first == '[' || first == '"';
    }

    /** Wraps plain text as {@code {"text":"..."}}, escaping it for JSON. */
    private static String asTextComponent(String plain) {
        StringBuilder json = new StringBuilder(plain.length() + 12);
        json.append("{\"text\":\"");
        for (int i = 0; i < plain.length(); i++) {
            appendEscaped(json, plain.charAt(i));
        }
        return json.append("\"}").toString();
    }

    private static void appendEscaped(StringBuilder target, char character) {
        switch (character) {
            case '"' -> target.append("\\\"");
            case '\\' -> target.append("\\\\");
            case '\n' -> target.append("\\n");
            case '\r' -> target.append("\\r");
            case '\t' -> target.append("\\t");
            case '\b' -> target.append("\\b");
            case '\f' -> target.append("\\f");
            default -> {
                if (character < 0x20) {
                    target.append(String.format("\\u%04x", (int) character));
                } else {
                    target.append(character);
                }
            }
        }
    }
}
