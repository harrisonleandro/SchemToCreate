package dev.schemtocreate.blockstate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A block identifier plus its full property set — {@code minecraft:oak_stairs} with
 * {@code facing=north, half=bottom, shape=straight, waterlogged=false}.
 *
 * <p>Properties are stored verbatim and never interpreted. That is deliberate: it is what
 * makes stairs, slabs, walls, fences, logs, rails, trapdoors, buttons, banners, campfires
 * and every other stateful block survive conversion without the converter needing to know
 * anything about them. Order is preserved so output matches the source byte for byte.
 */
public final class BlockState {

    public static final String AIR_NAME = "minecraft:air";
    public static final BlockState AIR = new BlockState(AIR_NAME, Map.of());

    private final String name;
    private final Map<String, String> properties;
    private final int hash;

    public BlockState(String name, Map<String, String> properties) {
        this.name = Objects.requireNonNull(name, "name");
        this.properties = properties.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        this.hash = 31 * name.hashCode() + this.properties.hashCode();
    }

    /** Namespaced block id, e.g. {@code minecraft:oak_stairs}. */
    public String name() {
        return name;
    }

    /** Immutable, insertion ordered property map. Empty for stateless blocks. */
    public Map<String, String> properties() {
        return properties;
    }

    public boolean hasProperties() {
        return !properties.isEmpty();
    }

    public String property(String key) {
        return properties.get(key);
    }

    public boolean isAir() {
        return AIR_NAME.equals(name);
    }

    public boolean isStructureVoid() {
        return "minecraft:structure_void".equals(name);
    }

    public boolean isWaterlogged() {
        return "true".equals(properties.get("waterlogged"));
    }

    /** Same block with a different id, keeping every property. */
    public BlockState withName(String newName) {
        return new BlockState(newName, properties);
    }

    /** Canonical Sponge/vanilla text form: {@code name[key=value,...]}. */
    public String toStateString() {
        if (properties.isEmpty()) {
            return name;
        }
        StringBuilder builder = new StringBuilder(name.length() + properties.size() * 16);
        builder.append(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.append(']').toString();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof BlockState state
                    && hash == state.hash
                    && name.equals(state.name)
                    && properties.equals(state.properties));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return toStateString();
    }
}
