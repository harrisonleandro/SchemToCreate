package dev.schemtocreate.reader.legacy;

import dev.schemtocreate.blockstate.BlockState;
import dev.schemtocreate.io.nbt.NbtCompound;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates pre-1.13 numeric blocks ({@code id:data}) into flattened block states.
 *
 * <p>Minecraft 1.13 ("the flattening") replaced numeric ids and 4-bit data values with named
 * blocks and properties. Legacy schematics still hold the old pair, so every block has to be
 * rewritten — the game itself does this with its DataFixer, but Create loads schematics
 * without running it, so the conversion happens here.
 *
 * <p>Blocks are keyed by their 1.12 registry name rather than their number: Schematica files
 * carry a name-to-id mapping that may differ from vanilla's numbering, and names survive that
 * where numbers do not. Output targets Minecraft 1.20.1 names ({@code smooth_stone_slab},
 * {@code dirt_path}, {@code grass}).
 *
 * <p>A few blocks cannot be resolved from {@code id:data} alone: door halves and tall plants
 * split their state across two blocks, and skulls, beds, banners and flower pots keep their
 * type in a block entity. Callers supply both through {@link Context}.
 */
public final class LegacyBlockTable {

    /** Vanilla 1.12 numbering, used when a file carries no mapping of its own. */
    static final String[] VANILLA_IDS = new String[256];

    static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

    /** Six-way facing as stored in the low three bits: down, up, north, south, west, east. */
    private static final String[] FACING6 = {"down", "up", "north", "south", "west", "east"};

    /** Horizontal index order used by beds, repeaters, anvils, gates, pumpkins: S, W, N, E. */
    private static final String[] HORIZONTAL = {"south", "west", "north", "east"};

    /** Stair order: E, W, S, N. */
    private static final String[] STAIRS_FACING = {"east", "west", "south", "north"};

    private static final String[] RAIL_SHAPES = {"north_south", "east_west", "ascending_east",
            "ascending_west", "ascending_north", "ascending_south", "south_east", "south_west",
            "north_west", "north_east"};

    private static final String[] STONE_SLABS = {"smooth_stone", "sandstone", "petrified_oak",
            "cobblestone", "brick", "stone_brick", "nether_brick", "quartz"};

    private static final Map<String, Converter> CONVERTERS = new HashMap<>();

    /** Extra information for blocks whose state is spread over neighbours or a block entity. */
    public interface Context {
        /** Data value of the block directly below if it has the same legacy name, else -1. */
        int dataBelow();

        /** Data value of the block directly above if it has the same legacy name, else -1. */
        int dataAbove();

        /** The legacy block entity at this position, or {@code null}. */
        NbtCompound blockEntity();

        Context NONE = new Context() {
            @Override
            public int dataBelow() {
                return -1;
            }

            @Override
            public int dataAbove() {
                return -1;
            }

            @Override
            public NbtCompound blockEntity() {
                return null;
            }
        };
    }

    @FunctionalInterface
    private interface Converter {
        BlockState convert(int data, Context context);
    }

    private LegacyBlockTable() {
    }

    /** Whether the block needs {@link Context#dataBelow()}/{@link Context#dataAbove()}. */
    static boolean isTwoHigh(String legacyName) {
        String name = strip(legacyName);
        return name.endsWith("_door") || name.equals("double_plant");
    }

    /** Whether the block's state depends on its block entity. */
    static boolean needsBlockEntity(String legacyName) {
        String name = strip(legacyName);
        return name.equals("skull") || name.equals("bed") || name.equals("flower_pot")
                || name.equals("standing_banner") || name.equals("wall_banner");
    }

    /**
     * @return the flattened state, or {@code null} if the block is not a known vanilla 1.12 block
     */
    public static BlockState convert(String legacyName, int data, Context context) {
        if (legacyName == null || !(legacyName.indexOf(':') < 0 || legacyName.startsWith("minecraft:"))) {
            return null;
        }
        Converter converter = CONVERTERS.get(strip(legacyName));
        return converter == null ? null : converter.convert(data & 15, context);
    }

    static String strip(String name) {
        return name.startsWith("minecraft:") ? name.substring(10) : name;
    }

    // ---------------------------------------------------------------- helpers

    private static BlockState state(String name, String... properties) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < properties.length; i += 2) {
            map.put(properties[i], properties[i + 1]);
        }
        return new BlockState("minecraft:" + name, map);
    }

    private static void simple(int id, String legacy, String modern) {
        register(id, legacy, (data, context) -> state(modern));
    }

    private static void simple(int id, String legacy) {
        simple(id, legacy, legacy);
    }

    private static void register(int id, String legacy, Converter converter) {
        if (id >= 0) {
            VANILLA_IDS[id] = "minecraft:" + legacy;
        }
        CONVERTERS.put(legacy, converter);
    }

    /** Picks {@code names[data]}, falling back to the first entry for out-of-range data. */
    private static void variants(int id, String legacy, String... names) {
        register(id, legacy, (data, context) -> state(data < names.length ? names[data] : names[0]));
    }

    private static String pick(String[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : values[0];
    }

    private static String bool(boolean value) {
        return value ? "true" : "false";
    }

    /** Ladder, wall sign, chest, furnace: 2 north, 3 south, 4 west, 5 east. */
    private static String facing2to5(int data) {
        return data >= 2 && data <= 5 ? FACING6[data] : "north";
    }

    private static String axis(int bits) {
        return switch (bits) {
            case 4 -> "x";
            case 8 -> "z";
            default -> "y";
        };
    }

    private static void colored(int id, String legacy, String suffix) {
        register(id, legacy, (data, context) -> state(COLORS[data] + "_" + suffix));
    }

    private static void stairs(int id, String legacy, String modern) {
        register(id, legacy, (data, context) -> state(modern,
                "facing", STAIRS_FACING[data & 3],
                "half", (data & 4) != 0 ? "top" : "bottom",
                "shape", "straight",
                "waterlogged", "false"));
    }

    private static void fence(int id, String legacy, String modern) {
        register(id, legacy, (data, context) -> state(modern,
                "east", "false", "north", "false", "south", "false", "waterlogged", "false", "west", "false"));
    }

    private static void fenceGate(int id, String legacy, String modern) {
        register(id, legacy, (data, context) -> state(modern,
                "facing", HORIZONTAL[data & 3],
                "in_wall", "false",
                "open", bool((data & 4) != 0),
                "powered", bool((data & 8) != 0)));
    }

    private static void door(int id, String legacy, String modern) {
        register(id, legacy, (data, context) -> {
            // The lower half stores facing and open; the upper half stores hinge and powered.
            boolean upper = (data & 8) != 0;
            int lower = upper ? context.dataBelow() : data;
            int top = upper ? data : context.dataAbove();
            if (lower < 0) {
                lower = 0;
            }
            if (top < 0) {
                top = 8;
            }
            return state(modern,
                    "facing", pick(new String[]{"east", "south", "west", "north"}, lower & 3),
                    "half", upper ? "upper" : "lower",
                    "hinge", (top & 1) != 0 ? "right" : "left",
                    "open", bool((lower & 4) != 0),
                    "powered", bool((top & 2) != 0));
        });
    }

    private static void trapdoor(int id, String legacy, String modern) {
        register(id, legacy, (data, context) -> state(modern,
                "facing", pick(new String[]{"north", "south", "west", "east"}, data & 3),
                "half", (data & 8) != 0 ? "top" : "bottom",
                "open", bool((data & 4) != 0),
                "powered", "false",
                "waterlogged", "false"));
    }

    private static void slab(int id, String legacy, String[] types, boolean isDouble) {
        register(id, legacy, (data, context) -> {
            int type = data & 7;
            if (isDouble && (data & 8) != 0) {
                // "Seamless" double slabs became full blocks of their own.
                String full = switch (legacy + ":" + type) {
                    case "double_stone_slab:0" -> "smooth_stone";
                    case "double_stone_slab:1" -> "smooth_sandstone";
                    case "double_stone_slab:7" -> "smooth_quartz";
                    case "double_stone_slab2:0" -> "smooth_red_sandstone";
                    default -> null;
                };
                if (full != null) {
                    return state(full);
                }
            }
            return state(pick(types, type) + "_slab",
                    "type", isDouble ? "double" : (data & 8) != 0 ? "top" : "bottom",
                    "waterlogged", "false");
        });
    }

    private static void button(int id, String legacy, String modern) {
        register(id, legacy, (data, context) -> {
            int dir = data & 7;
            String face = dir == 0 ? "ceiling" : dir == 5 ? "floor" : "wall";
            String facing = dir >= 1 && dir <= 4 ? pick(new String[]{"", "east", "west", "south", "north"}, dir) : "north";
            return state(modern, "face", face, "facing", facing, "powered", bool((data & 8) != 0));
        });
    }

    private static void torch(int id, String legacy, String floor, String wall, String lit) {
        register(id, legacy, (data, context) -> {
            boolean onWall = data >= 1 && data <= 4;
            String facing = pick(new String[]{"", "east", "west", "south", "north"}, data);
            if (lit == null) {
                return onWall ? state(wall, "facing", facing) : state(floor);
            }
            return onWall ? state(wall, "facing", facing, "lit", lit) : state(floor, "lit", lit);
        });
    }

    private static void rail(int id, String legacy, String modern, boolean poweredBit) {
        register(id, legacy, (data, context) -> {
            if (!poweredBit) {
                return state(modern, "shape", pick(RAIL_SHAPES, data), "waterlogged", "false");
            }
            return state(modern, "powered", bool((data & 8) != 0),
                    "shape", pick(RAIL_SHAPES, data & 7), "waterlogged", "false");
        });
    }

    private static void sixWay(int id, String legacy, String modern, String bitName) {
        register(id, legacy, (data, context) -> bitName == null
                ? state(modern, "facing", pick(FACING6, data & 7))
                : state(modern, "facing", pick(FACING6, data & 7), bitName, bool((data & 8) != 0)));
    }

    private static void age(int id, String legacy, String modern, int max) {
        register(id, legacy, (data, context) -> state(modern, "age", Integer.toString(Math.min(data, max))));
    }

    private static void mushroomBlock(int id, String legacy, String cap) {
        register(id, legacy, (data, context) -> {
            // north, east, south, west, up, down
            boolean[] f = switch (data) {
                case 1 -> new boolean[]{true, false, false, true, true, false};
                case 2 -> new boolean[]{true, false, false, false, true, false};
                case 3 -> new boolean[]{true, true, false, false, true, false};
                case 4 -> new boolean[]{false, false, false, true, true, false};
                case 5 -> new boolean[]{false, false, false, false, true, false};
                case 6 -> new boolean[]{false, true, false, false, true, false};
                case 7 -> new boolean[]{false, false, true, true, true, false};
                case 8 -> new boolean[]{false, false, true, false, true, false};
                case 9 -> new boolean[]{false, true, true, false, true, false};
                case 10 -> new boolean[]{true, true, true, true, false, false};
                case 14, 15 -> new boolean[]{true, true, true, true, true, true};
                default -> new boolean[]{false, false, false, false, false, false};
            };
            String name = data == 10 || data == 15 ? "mushroom_stem" : cap;
            return state(name, "down", bool(f[5]), "east", bool(f[1]), "north", bool(f[0]),
                    "south", bool(f[2]), "up", bool(f[4]), "west", bool(f[3]));
        });
    }

    // ---------------------------------------------------------------- block entity driven

    private static BlockState skull(int data, Context context) {
        NbtCompound te = context.blockEntity();
        int type = te == null ? 0 : te.getInt("SkullType", 0);
        String base = pick(new String[]{"skeleton", "wither_skeleton", "zombie", "player", "creeper", "dragon"}, type);
        String suffix = type == 0 || type == 1 ? "_skull" : "_head";
        int dir = data & 7;
        if (dir >= 2 && dir <= 5) {
            return state(base + "_wall" + suffix, "facing", FACING6[dir]);
        }
        int rotation = te == null ? 0 : te.getInt("Rot", 0) & 15;
        return state(base + suffix, "rotation", Integer.toString(rotation));
    }

    private static BlockState bed(int data, Context context) {
        NbtCompound te = context.blockEntity();
        int color = te == null ? 14 : te.getInt("color", 14) & 15;
        return state(COLORS[color] + "_bed",
                "facing", HORIZONTAL[data & 3],
                "occupied", "false",
                "part", (data & 8) != 0 ? "head" : "foot");
    }

    private static BlockState banner(String kind, int data, Context context) {
        NbtCompound te = context.blockEntity();
        // Banners store a dye damage value, which counts black-to-white: 15 - x is the colour.
        int base = te == null ? 15 : te.getInt("Base", 15) & 15;
        String color = COLORS[15 - base];
        return kind.equals("wall")
                ? state(color + "_wall_banner", "facing", facing2to5(data))
                : state(color + "_banner", "rotation", Integer.toString(data));
    }

    private static BlockState flowerPot(int data, Context context) {
        NbtCompound te = context.blockEntity();
        if (te == null) {
            return state("flower_pot");
        }
        String item = strip(te.getString("Item", ""));
        int itemData = te.getInt("Data", 0);
        String plant = switch (item) {
            case "red_flower" -> pick(new String[]{"poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                    "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"}, itemData);
            case "yellow_flower" -> "dandelion";
            case "sapling" -> pick(WOODS, itemData) + "_sapling";
            case "red_mushroom", "brown_mushroom", "cactus" -> item;
            case "deadbush" -> "dead_bush";
            case "tallgrass" -> "fern";
            default -> null;
        };
        return plant == null ? state("flower_pot") : state("potted_" + plant);
    }

    // ---------------------------------------------------------------- table

    static {
        simple(0, "air");
        variants(1, "stone", "stone", "granite", "polished_granite", "diorite", "polished_diorite",
                "andesite", "polished_andesite");
        register(2, "grass", (d, c) -> state("grass_block", "snowy", "false"));
        register(3, "dirt", (d, c) -> d == 2 ? state("podzol", "snowy", "false")
                : state(d == 1 ? "coarse_dirt" : "dirt"));
        simple(4, "cobblestone");
        register(5, "planks", (d, c) -> state(pick(WOODS, d) + "_planks"));
        register(6, "sapling", (d, c) -> state(pick(WOODS, d & 7) + "_sapling", "stage", (d & 8) != 0 ? "1" : "0"));
        simple(7, "bedrock");
        register(8, "flowing_water", (d, c) -> state("water", "level", Integer.toString(d)));
        register(9, "water", (d, c) -> state("water", "level", Integer.toString(d)));
        register(10, "flowing_lava", (d, c) -> state("lava", "level", Integer.toString(d)));
        register(11, "lava", (d, c) -> state("lava", "level", Integer.toString(d)));
        variants(12, "sand", "sand", "red_sand");
        simple(13, "gravel");
        simple(14, "gold_ore");
        simple(15, "iron_ore");
        simple(16, "coal_ore");
        register(17, "log", (d, c) -> (d & 12) == 12
                ? state(WOODS[d & 3] + "_wood", "axis", "y")
                : state(WOODS[d & 3] + "_log", "axis", axis(d & 12)));
        // Leaves from a schematic are part of a build: keep them from decaying.
        register(18, "leaves", (d, c) -> state(WOODS[d & 3] + "_leaves",
                "distance", "7", "persistent", "true", "waterlogged", "false"));
        variants(19, "sponge", "sponge", "wet_sponge");
        simple(20, "glass");
        simple(21, "lapis_ore");
        simple(22, "lapis_block");
        sixWay(23, "dispenser", "dispenser", "triggered");
        variants(24, "sandstone", "sandstone", "chiseled_sandstone", "cut_sandstone");
        simple(25, "noteblock", "note_block");
        register(26, "bed", LegacyBlockTable::bed);
        rail(27, "golden_rail", "powered_rail", true);
        rail(28, "detector_rail", "detector_rail", true);
        sixWay(29, "sticky_piston", "sticky_piston", "extended");
        simple(30, "web", "cobweb");
        register(31, "tallgrass", (d, c) -> state(d == 2 ? "fern" : d == 0 ? "dead_bush" : "grass"));
        simple(32, "deadbush", "dead_bush");
        sixWay(33, "piston", "piston", "extended");
        register(34, "piston_head", (d, c) -> state("piston_head", "facing", pick(FACING6, d & 7),
                "short", "false", "type", (d & 8) != 0 ? "sticky" : "normal"));
        colored(35, "wool", "wool");
        simple(36, "piston_extension", "air");
        simple(37, "yellow_flower", "dandelion");
        variants(38, "red_flower", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy");
        simple(39, "brown_mushroom");
        simple(40, "red_mushroom");
        simple(41, "gold_block");
        simple(42, "iron_block");
        slab(43, "double_stone_slab", STONE_SLABS, true);
        slab(44, "stone_slab", STONE_SLABS, false);
        simple(45, "brick_block", "bricks");
        simple(46, "tnt");
        simple(47, "bookshelf");
        simple(48, "mossy_cobblestone");
        simple(49, "obsidian");
        torch(50, "torch", "torch", "wall_torch", null);
        register(51, "fire", (d, c) -> state("fire", "age", Integer.toString(d)));
        simple(52, "mob_spawner", "spawner");
        stairs(53, "oak_stairs", "oak_stairs");
        register(54, "chest", (d, c) -> state("chest", "facing", facing2to5(d), "type", "single", "waterlogged", "false"));
        register(55, "redstone_wire", (d, c) -> state("redstone_wire", "east", "none", "north", "none",
                "power", Integer.toString(d), "south", "none", "west", "none"));
        simple(56, "diamond_ore");
        simple(57, "diamond_block");
        simple(58, "crafting_table");
        age(59, "wheat", "wheat", 7);
        register(60, "farmland", (d, c) -> state("farmland", "moisture", Integer.toString(d & 7)));
        register(61, "furnace", (d, c) -> state("furnace", "facing", facing2to5(d), "lit", "false"));
        register(62, "lit_furnace", (d, c) -> state("furnace", "facing", facing2to5(d), "lit", "true"));
        register(63, "standing_sign", (d, c) -> state("oak_sign", "rotation", Integer.toString(d), "waterlogged", "false"));
        door(64, "wooden_door", "oak_door");
        register(65, "ladder", (d, c) -> state("ladder", "facing", facing2to5(d), "waterlogged", "false"));
        rail(66, "rail", "rail", false);
        stairs(67, "stone_stairs", "cobblestone_stairs");
        register(68, "wall_sign", (d, c) -> state("oak_wall_sign", "facing", facing2to5(d), "waterlogged", "false"));
        register(69, "lever", (d, c) -> {
            String[] face = {"ceiling", "wall", "wall", "wall", "wall", "floor", "floor", "ceiling"};
            String[] facing = {"west", "east", "west", "south", "north", "north", "west", "north"};
            return state("lever", "face", face[d & 7], "facing", facing[d & 7], "powered", bool((d & 8) != 0));
        });
        register(70, "stone_pressure_plate", (d, c) -> state("stone_pressure_plate", "powered", bool(d == 1)));
        door(71, "iron_door", "iron_door");
        register(72, "wooden_pressure_plate", (d, c) -> state("oak_pressure_plate", "powered", bool(d == 1)));
        register(73, "redstone_ore", (d, c) -> state("redstone_ore", "lit", "false"));
        register(74, "lit_redstone_ore", (d, c) -> state("redstone_ore", "lit", "true"));
        torch(75, "unlit_redstone_torch", "redstone_torch", "redstone_wall_torch", "false");
        torch(76, "redstone_torch", "redstone_torch", "redstone_wall_torch", "true");
        button(77, "stone_button", "stone_button");
        register(78, "snow_layer", (d, c) -> state("snow", "layers", Integer.toString((d & 7) + 1)));
        simple(79, "ice");
        simple(80, "snow", "snow_block");
        age(81, "cactus", "cactus", 15);
        simple(82, "clay");
        age(83, "reeds", "sugar_cane", 15);
        register(84, "jukebox", (d, c) -> state("jukebox", "has_record", bool(d == 1)));
        fence(85, "fence", "oak_fence");
        register(86, "pumpkin", (d, c) -> state("carved_pumpkin", "facing", HORIZONTAL[d & 3]));
        simple(87, "netherrack");
        simple(88, "soul_sand");
        simple(89, "glowstone");
        register(90, "portal", (d, c) -> state("nether_portal", "axis", d == 2 ? "z" : "x"));
        register(91, "lit_pumpkin", (d, c) -> state("jack_o_lantern", "facing", HORIZONTAL[d & 3]));
        register(92, "cake", (d, c) -> state("cake", "bites", Integer.toString(Math.min(d, 6))));
        register(93, "unpowered_repeater", (d, c) -> state("repeater", "delay", Integer.toString((d >> 2) + 1),
                "facing", HORIZONTAL[d & 3], "locked", "false", "powered", "false"));
        register(94, "powered_repeater", (d, c) -> state("repeater", "delay", Integer.toString((d >> 2) + 1),
                "facing", HORIZONTAL[d & 3], "locked", "false", "powered", "true"));
        colored(95, "stained_glass", "stained_glass");
        trapdoor(96, "trapdoor", "oak_trapdoor");
        variants(97, "monster_egg", "infested_stone", "infested_cobblestone", "infested_stone_bricks",
                "infested_mossy_stone_bricks", "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks");
        variants(98, "stonebrick", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks");
        mushroomBlock(99, "brown_mushroom_block", "brown_mushroom_block");
        mushroomBlock(100, "red_mushroom_block", "red_mushroom_block");
        fence(101, "iron_bars", "iron_bars");
        fence(102, "glass_pane", "glass_pane");
        simple(103, "melon_block", "melon");
        age(104, "pumpkin_stem", "pumpkin_stem", 7);
        age(105, "melon_stem", "melon_stem", 7);
        register(106, "vine", (d, c) -> state("vine", "east", bool((d & 8) != 0), "north", bool((d & 4) != 0),
                "south", bool((d & 1) != 0), "up", bool(d == 0), "west", bool((d & 2) != 0)));
        fenceGate(107, "fence_gate", "oak_fence_gate");
        stairs(108, "brick_stairs", "brick_stairs");
        stairs(109, "stone_brick_stairs", "stone_brick_stairs");
        register(110, "mycelium", (d, c) -> state("mycelium", "snowy", "false"));
        simple(111, "waterlily", "lily_pad");
        simple(112, "nether_brick", "nether_bricks");
        fence(113, "nether_brick_fence", "nether_brick_fence");
        stairs(114, "nether_brick_stairs", "nether_brick_stairs");
        age(115, "nether_wart", "nether_wart", 3);
        simple(116, "enchanting_table");
        register(117, "brewing_stand", (d, c) -> state("brewing_stand", "has_bottle_0", bool((d & 1) != 0),
                "has_bottle_1", bool((d & 2) != 0), "has_bottle_2", bool((d & 4) != 0)));
        register(118, "cauldron", (d, c) -> (d & 3) == 0 ? state("cauldron")
                : state("water_cauldron", "level", Integer.toString(d & 3)));
        simple(119, "end_portal");
        register(120, "end_portal_frame", (d, c) -> state("end_portal_frame", "eye", bool((d & 4) != 0),
                "facing", HORIZONTAL[d & 3]));
        simple(121, "end_stone");
        simple(122, "dragon_egg");
        register(123, "redstone_lamp", (d, c) -> state("redstone_lamp", "lit", "false"));
        register(124, "lit_redstone_lamp", (d, c) -> state("redstone_lamp", "lit", "true"));
        slab(125, "double_wooden_slab", WOODS, true);
        slab(126, "wooden_slab", WOODS, false);
        register(127, "cocoa", (d, c) -> state("cocoa", "age", Integer.toString(Math.min(d >> 2, 2)),
                "facing", HORIZONTAL[d & 3]));
        stairs(128, "sandstone_stairs", "sandstone_stairs");
        simple(129, "emerald_ore");
        register(130, "ender_chest", (d, c) -> state("ender_chest", "facing", facing2to5(d), "waterlogged", "false"));
        register(131, "tripwire_hook", (d, c) -> state("tripwire_hook", "attached", bool((d & 4) != 0),
                "facing", HORIZONTAL[d & 3], "powered", bool((d & 8) != 0)));
        register(132, "tripwire", (d, c) -> state("tripwire", "attached", bool((d & 4) != 0),
                "disarmed", bool((d & 8) != 0), "east", "false", "north", "false",
                "powered", bool((d & 1) != 0), "south", "false", "west", "false"));
        simple(133, "emerald_block");
        stairs(134, "spruce_stairs", "spruce_stairs");
        stairs(135, "birch_stairs", "birch_stairs");
        stairs(136, "jungle_stairs", "jungle_stairs");
        register(137, "command_block", (d, c) -> state("command_block", "conditional", bool((d & 8) != 0),
                "facing", pick(FACING6, d & 7)));
        simple(138, "beacon");
        register(139, "cobblestone_wall", (d, c) -> state(d == 1 ? "mossy_cobblestone_wall" : "cobblestone_wall",
                "east", "none", "north", "none", "south", "none", "up", "true", "waterlogged", "false", "west", "none"));
        register(140, "flower_pot", LegacyBlockTable::flowerPot);
        age(141, "carrots", "carrots", 7);
        age(142, "potatoes", "potatoes", 7);
        button(143, "wooden_button", "oak_button");
        register(144, "skull", LegacyBlockTable::skull);
        register(145, "anvil", (d, c) -> state(pick(new String[]{"anvil", "chipped_anvil", "damaged_anvil"}, d >> 2),
                "facing", HORIZONTAL[d & 3]));
        register(146, "trapped_chest", (d, c) -> state("trapped_chest", "facing", facing2to5(d), "type", "single",
                "waterlogged", "false"));
        register(147, "light_weighted_pressure_plate", (d, c) -> state("light_weighted_pressure_plate",
                "power", Integer.toString(d)));
        register(148, "heavy_weighted_pressure_plate", (d, c) -> state("heavy_weighted_pressure_plate",
                "power", Integer.toString(d)));
        register(149, "unpowered_comparator", (d, c) -> state("comparator", "facing", HORIZONTAL[d & 3],
                "mode", (d & 4) != 0 ? "subtract" : "compare", "powered", bool((d & 8) != 0)));
        register(150, "powered_comparator", (d, c) -> state("comparator", "facing", HORIZONTAL[d & 3],
                "mode", (d & 4) != 0 ? "subtract" : "compare", "powered", "true"));
        register(151, "daylight_detector", (d, c) -> state("daylight_detector", "inverted", "false",
                "power", Integer.toString(d)));
        simple(152, "redstone_block");
        simple(153, "quartz_ore", "nether_quartz_ore");
        register(154, "hopper", (d, c) -> state("hopper", "enabled", bool((d & 8) == 0),
                "facing", (d & 7) == 0 ? "down" : facing2to5(d & 7)));
        register(155, "quartz_block", (d, c) -> switch (d) {
            case 1 -> state("chiseled_quartz_block");
            case 2 -> state("quartz_pillar", "axis", "y");
            case 3 -> state("quartz_pillar", "axis", "x");
            case 4 -> state("quartz_pillar", "axis", "z");
            default -> state("quartz_block");
        });
        stairs(156, "quartz_stairs", "quartz_stairs");
        rail(157, "activator_rail", "activator_rail", true);
        sixWay(158, "dropper", "dropper", "triggered");
        colored(159, "stained_hardened_clay", "terracotta");
        register(160, "stained_glass_pane", (d, c) -> state(COLORS[d] + "_stained_glass_pane",
                "east", "false", "north", "false", "south", "false", "waterlogged", "false", "west", "false"));
        register(161, "leaves2", (d, c) -> state((d & 1) == 0 ? "acacia_leaves" : "dark_oak_leaves",
                "distance", "7", "persistent", "true", "waterlogged", "false"));
        register(162, "log2", (d, c) -> (d & 12) == 12
                ? state(WOODS[4 + (d & 1)] + "_wood", "axis", "y")
                : state(WOODS[4 + (d & 1)] + "_log", "axis", axis(d & 12)));
        stairs(163, "acacia_stairs", "acacia_stairs");
        stairs(164, "dark_oak_stairs", "dark_oak_stairs");
        simple(165, "slime", "slime_block");
        simple(166, "barrier");
        trapdoor(167, "iron_trapdoor", "iron_trapdoor");
        variants(168, "prismarine", "prismarine", "prismarine_bricks", "dark_prismarine");
        simple(169, "sea_lantern");
        register(170, "hay_block", (d, c) -> state("hay_block", "axis", axis(d & 12)));
        colored(171, "carpet", "carpet");
        simple(172, "hardened_clay", "terracotta");
        simple(173, "coal_block");
        simple(174, "packed_ice");
        register(175, "double_plant", (d, c) -> {
            boolean upper = (d & 8) != 0;
            int type = upper ? c.dataBelow() : d;
            String[] plants = {"sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"};
            return state(pick(plants, Math.max(type, 0) & 7), "half", upper ? "upper" : "lower");
        });
        register(176, "standing_banner", (d, c) -> banner("standing", d, c));
        register(177, "wall_banner", (d, c) -> banner("wall", d, c));
        register(178, "daylight_detector_inverted", (d, c) -> state("daylight_detector", "inverted", "true",
                "power", Integer.toString(d)));
        variants(179, "red_sandstone", "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone");
        stairs(180, "red_sandstone_stairs", "red_sandstone_stairs");
        slab(181, "double_stone_slab2", new String[]{"red_sandstone"}, true);
        slab(182, "stone_slab2", new String[]{"red_sandstone"}, false);
        fenceGate(183, "spruce_fence_gate", "spruce_fence_gate");
        fenceGate(184, "birch_fence_gate", "birch_fence_gate");
        fenceGate(185, "jungle_fence_gate", "jungle_fence_gate");
        fenceGate(186, "dark_oak_fence_gate", "dark_oak_fence_gate");
        fenceGate(187, "acacia_fence_gate", "acacia_fence_gate");
        fence(188, "spruce_fence", "spruce_fence");
        fence(189, "birch_fence", "birch_fence");
        fence(190, "jungle_fence", "jungle_fence");
        fence(191, "dark_oak_fence", "dark_oak_fence");
        fence(192, "acacia_fence", "acacia_fence");
        door(193, "spruce_door", "spruce_door");
        door(194, "birch_door", "birch_door");
        door(195, "jungle_door", "jungle_door");
        door(196, "acacia_door", "acacia_door");
        door(197, "dark_oak_door", "dark_oak_door");
        sixWay(198, "end_rod", "end_rod", null);
        register(199, "chorus_plant", (d, c) -> state("chorus_plant", "down", "false", "east", "false",
                "north", "false", "south", "false", "up", "false", "west", "false"));
        age(200, "chorus_flower", "chorus_flower", 5);
        simple(201, "purpur_block");
        register(202, "purpur_pillar", (d, c) -> state("purpur_pillar", "axis", axis(d & 12)));
        stairs(203, "purpur_stairs", "purpur_stairs");
        slab(204, "purpur_double_slab", new String[]{"purpur"}, true);
        slab(205, "purpur_slab", new String[]{"purpur"}, false);
        simple(206, "end_bricks", "end_stone_bricks");
        age(207, "beetroots", "beetroots", 3);
        simple(208, "grass_path", "dirt_path");
        simple(209, "end_gateway");
        register(210, "repeating_command_block", (d, c) -> state("repeating_command_block",
                "conditional", bool((d & 8) != 0), "facing", pick(FACING6, d & 7)));
        register(211, "chain_command_block", (d, c) -> state("chain_command_block",
                "conditional", bool((d & 8) != 0), "facing", pick(FACING6, d & 7)));
        age(212, "frosted_ice", "frosted_ice", 3);
        simple(213, "magma", "magma_block");
        simple(214, "nether_wart_block");
        simple(215, "red_nether_brick", "red_nether_bricks");
        register(216, "bone_block", (d, c) -> state("bone_block", "axis", axis(d & 12)));
        simple(217, "structure_void");
        sixWay(218, "observer", "observer", "powered");
        for (int i = 0; i < 16; i++) {
            String color = COLORS[i];
            String legacy = (i == 8 ? "silver" : color) + "_shulker_box";
            register(219 + i, legacy, (d, c) -> state(color + "_shulker_box", "facing", pick(FACING6, d & 7)));
            String glazed = (i == 8 ? "silver" : color) + "_glazed_terracotta";
            register(235 + i, glazed, (d, c) -> state(color + "_glazed_terracotta", "facing", HORIZONTAL[d & 3]));
        }
        colored(251, "concrete", "concrete");
        colored(252, "concrete_powder", "concrete_powder");
        register(255, "structure_block", (d, c) -> state("structure_block",
                "mode", pick(new String[]{"save", "load", "corner", "data"}, d)));
    }
}
