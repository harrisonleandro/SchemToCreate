package dev.schemtocreate.reader.legacy;

import dev.schemtocreate.blockstate.BlockState;
import dev.schemtocreate.io.nbt.NbtByte;
import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtInt;
import dev.schemtocreate.io.nbt.NbtList;
import dev.schemtocreate.io.nbt.NbtString;
import dev.schemtocreate.io.nbt.NbtTag;
import dev.schemtocreate.model.BlockEntity;
import dev.schemtocreate.model.BlockPos;

import java.util.Locale;
import java.util.Map;

/**
 * Upgrades pre-1.13 block entities and entity ids.
 *
 * <p>The block entity type is derived from the <em>translated block</em>, not from the
 * legacy id: 1.12 used {@code minecraft:chest} for trapped chests and had block entities for
 * flower pots and note blocks, which are plain block states today. Payload changes are
 * limited to what makes a block place correctly — sign text layout, skull owners and banner
 * colours. Inventories are carried as they are.
 */
final class LegacyBlockEntities {

    private static final Map<String, String> SIMPLE = Map.ofEntries(
            Map.entry("chest", "chest"),
            Map.entry("trapped_chest", "trapped_chest"),
            Map.entry("ender_chest", "ender_chest"),
            Map.entry("furnace", "furnace"),
            Map.entry("dispenser", "dispenser"),
            Map.entry("dropper", "dropper"),
            Map.entry("hopper", "hopper"),
            Map.entry("brewing_stand", "brewing_stand"),
            Map.entry("enchanting_table", "enchanting_table"),
            Map.entry("beacon", "beacon"),
            Map.entry("jukebox", "jukebox"),
            Map.entry("daylight_detector", "daylight_detector"),
            Map.entry("comparator", "comparator"),
            Map.entry("end_portal", "end_portal"),
            Map.entry("end_gateway", "end_gateway"),
            Map.entry("spawner", "mob_spawner"),
            Map.entry("structure_block", "structure_block"),
            Map.entry("command_block", "command_block"),
            Map.entry("repeating_command_block", "command_block"),
            Map.entry("chain_command_block", "command_block"));

    /** Pre-1.11 entity names that do not convert by lower-casing alone. */
    private static final Map<String, String> ENTITY_RENAMES = Map.ofEntries(
            Map.entry("ItemFrame", "item_frame"),
            Map.entry("ArmorStand", "armor_stand"),
            Map.entry("LeashKnot", "leash_knot"),
            Map.entry("MinecartRideable", "minecart"),
            Map.entry("MinecartChest", "chest_minecart"),
            Map.entry("MinecartFurnace", "furnace_minecart"),
            Map.entry("MinecartTNT", "tnt_minecart"),
            Map.entry("MinecartHopper", "hopper_minecart"),
            Map.entry("MinecartSpawner", "spawner_minecart"),
            Map.entry("MinecartCommandBlock", "command_block_minecart"),
            Map.entry("EnderCrystal", "end_crystal"),
            Map.entry("PigZombie", "zombified_piglin"),
            Map.entry("zombie_pigman", "zombified_piglin"),
            Map.entry("VillagerGolem", "iron_golem"),
            Map.entry("villager_golem", "iron_golem"),
            Map.entry("MushroomCow", "mooshroom"),
            Map.entry("Ozelot", "ocelot"),
            Map.entry("SnowMan", "snow_golem"),
            Map.entry("snowman", "snow_golem"),
            Map.entry("EntityHorse", "horse"),
            Map.entry("LavaSlime", "magma_cube"),
            Map.entry("CaveSpider", "cave_spider"),
            Map.entry("PrimedTnt", "tnt"),
            Map.entry("FallingSand", "falling_block"),
            Map.entry("XPOrb", "experience_orb"),
            Map.entry("xp_orb", "experience_orb"));

    private LegacyBlockEntities() {
    }

    /**
     * @param state the block the entity belongs to, already translated
     * @return the modern block entity, or {@code null} when the block no longer has one
     */
    static BlockEntity convert(BlockPos pos, NbtCompound legacy, BlockState state) {
        String block = LegacyBlockTable.strip(state.name());
        String type;
        NbtCompound payload = legacy.copy();
        payload.remove("id");
        payload.remove("x");
        payload.remove("y");
        payload.remove("z");

        if (block.endsWith("_sign")) {
            type = "sign";
            upgradeSign(payload);
        } else if (block.endsWith("_skull") || block.endsWith("_head")) {
            type = "skull";
            payload.remove("SkullType");
            payload.remove("Rot");
            NbtTag owner = payload.remove("Owner");
            if (owner != null) {
                payload.put("SkullOwner", owner);
            }
        } else if (block.endsWith("_banner")) {
            type = "banner";
            payload.remove("Base");
            invertPatternColors(payload);
        } else if (block.endsWith("_bed")) {
            type = "bed";
            payload.remove("color");
        } else if (block.endsWith("shulker_box")) {
            type = "shulker_box";
        } else {
            type = SIMPLE.get(block);
        }
        return type == null ? null : new BlockEntity(pos, "minecraft:" + type, payload);
    }

    /** {@code Text1..Text4} became {@code front_text.messages} in 1.20. */
    private static void upgradeSign(NbtCompound payload) {
        NbtList messages = new NbtList();
        for (int line = 1; line <= 4; line++) {
            NbtTag text = payload.remove("Text" + line);
            messages.add(text instanceof NbtString string ? string : new NbtString("{\"text\":\"\"}"));
        }
        NbtCompound front = new NbtCompound();
        front.put("messages", messages);
        front.putString("color", "black");
        front.put("has_glowing_text", NbtByte.ZERO);
        NbtCompound back = new NbtCompound();
        NbtList empty = new NbtList();
        for (int line = 0; line < 4; line++) {
            empty.add(new NbtString("{\"text\":\"\"}"));
        }
        back.put("messages", empty);
        back.putString("color", "black");
        back.put("has_glowing_text", NbtByte.ZERO);
        payload.put("front_text", front);
        payload.put("back_text", back);
        payload.put("is_waxed", NbtByte.ZERO);
    }

    /** Pattern colours were stored as dye damage values, which run in the opposite order. */
    private static void invertPatternColors(NbtCompound payload) {
        NbtList patterns = payload.getList("Patterns");
        if (patterns == null) {
            return;
        }
        for (NbtCompound pattern : patterns.compounds()) {
            if (pattern.contains("Color")) {
                pattern.put("Color", new NbtInt(15 - (pattern.getInt("Color", 0) & 15)));
            }
        }
    }

    /** Normalises an entity id: {@code ItemFrame} and {@code item_frame} both become namespaced. */
    static String entityId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String bare = id.startsWith("minecraft:") ? id.substring(10) : id;
        String renamed = ENTITY_RENAMES.get(bare);
        if (renamed != null) {
            return "minecraft:" + renamed;
        }
        if (id.indexOf(':') >= 0) {
            return id;
        }
        // CamelCase to snake_case: "Painting" -> "painting", "WitherSkull" -> "wither_skull".
        return "minecraft:" + bare.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
