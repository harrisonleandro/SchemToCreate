package dev.schemtocreate.blockstate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the textual block state form used by Sponge schematic palettes.
 *
 * <p>Grammar: {@code [namespace:]path[ '[' key=value (',' key=value)* ']' ]}. Property
 * values are opaque strings; no vocabulary of known blocks is consulted, so modded blocks
 * and future vanilla blocks parse just as well as current ones.
 */
public final class BlockStateParser {

    private static final String DEFAULT_NAMESPACE = "minecraft";

    private BlockStateParser() {
    }

    /**
     * @param stateString e.g. {@code minecraft:oak_stairs[facing=north,waterlogged=false]}
     * @throws BlockStateFormatException if the string is empty or has unbalanced brackets
     */
    public static BlockState parse(String stateString) {
        if (stateString == null) {
            throw new BlockStateFormatException("Block state string is null");
        }
        String trimmed = stateString.trim();
        if (trimmed.isEmpty()) {
            throw new BlockStateFormatException("Block state string is empty");
        }

        int bracket = trimmed.indexOf('[');
        if (bracket < 0) {
            return new BlockState(normalizeName(trimmed), Map.of());
        }
        if (trimmed.charAt(trimmed.length() - 1) != ']') {
            throw new BlockStateFormatException("Unterminated property list in: " + stateString);
        }

        String name = normalizeName(trimmed.substring(0, bracket).trim());
        String body = trimmed.substring(bracket + 1, trimmed.length() - 1).trim();
        return new BlockState(name, body.isEmpty() ? Map.of() : parseProperties(body, stateString));
    }

    private static Map<String, String> parseProperties(String body, String original) {
        Map<String, String> properties = new LinkedHashMap<>();
        int start = 0;
        while (start <= body.length()) {
            int comma = body.indexOf(',', start);
            String pair = comma < 0 ? body.substring(start) : body.substring(start, comma);
            addProperty(properties, pair, original);
            if (comma < 0) {
                break;
            }
            start = comma + 1;
        }
        return properties;
    }

    private static void addProperty(Map<String, String> properties, String pair, String original) {
        int equals = pair.indexOf('=');
        if (equals < 0) {
            throw new BlockStateFormatException(
                    "Property '" + pair.trim() + "' has no '=' in: " + original);
        }
        String key = pair.substring(0, equals).trim();
        String value = pair.substring(equals + 1).trim();
        if (key.isEmpty()) {
            throw new BlockStateFormatException("Empty property name in: " + original);
        }
        properties.put(key, value);
    }

    /** Adds the implicit {@code minecraft:} namespace so equal blocks compare equal. */
    private static String normalizeName(String name) {
        if (name.isEmpty()) {
            throw new BlockStateFormatException("Empty block name");
        }
        return name.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ':' + name : name;
    }
}
