package dev.schemtocreate.writer;

import dev.schemtocreate.blockstate.BlockState;
import dev.schemtocreate.model.Region;
import dev.schemtocreate.model.Structure;
import dev.schemtocreate.palette.Palette;

import java.io.IOException;
import java.util.BitSet;
import java.util.Set;

/**
 * Finds blocks that cannot be seen from anywhere: full opaque cubes whose six neighbours are
 * all full opaque cubes too.
 *
 * <p>Terrain and solid builds are mostly interior — a sandstone pyramid is a shell of visible
 * blocks around hundreds of thousands that no player will ever see. Leaving those out shrinks
 * the file, the Schematicannon's material list and its print time, while the finished build
 * looks identical. The structure's outer boundary counts as exposed, so nothing on its edge
 * is ever removed.
 *
 * <p>Classification errs on the side of keeping a block: anything that is not certainly a
 * full, opaque, plain cube (glass, leaves, slabs, stairs, fluids, anything waterlogged or
 * holding a block entity) is treated as see-through, and so are its neighbours' faces.
 */
public final class HiddenBlocks {

    /** Name fragments of blocks that are not full opaque cubes. */
    private static final String[] SEE_THROUGH = {
            "air", "water", "lava", "bubble", "fire", "portal", "glass", "pane", "bars", "slab",
            "stairs", "fence", "wall", "gate", "door", "leaves", "sign", "torch", "lantern",
            "carpet", "ladder", "vine", "button", "lever", "plate", "rail", "sapling", "flower",
            "tulip", "orchid", "allium", "bluet", "daisy", "dandelion", "poppy", "cornflower",
            "lily", "rose", "peony", "lilac", "fern", "bush", "grass", "mushroom", "fungus",
            "roots", "sprouts", "web", "chest", "shulker", "chain", "candle", "head", "skull",
            "banner", "_bed", "anvil", "cauldron", "hopper", "hook", "tripwire", "wire",
            "repeater", "comparator", "piston", "snow", "ice", "kelp", "seagrass", "coral",
            "pickle", "pot", "campfire", "bell", "scaffolding", "cake", "farmland", "path",
            "soul_sand", "mud", "honey", "slime", "spawner", "beacon", "cactus", "bamboo",
            "cane", "wheat", "carrots", "potatoes", "beetroots", "pumpkin_stem", "melon_stem", "wart", "berr",
            "cocoa", "dripleaf", "azalea", "lichen", "vein", "bud", "cluster", "dripstone",
            "rod", "frame", "barrier", "structure_void", "enchanting", "brewing",
            "grindstone", "stonecutter", "lectern", "composter", "daylight", "conduit", "egg",
            "chorus", "sensor", "shrieker", "frogspawn", "petals", "spore", "pitcher",
            "torchflower", "vault", "grate", "heavy_core", "absent"};

    /** Full opaque cubes that the fragments above would wrongly exclude. */
    private static final Set<String> FULL_EXCEPTIONS = Set.of(
            "minecraft:grass_block", "minecraft:snow_block", "minecraft:mud_bricks",
            "minecraft:packed_mud", "minecraft:dripstone_block", "minecraft:honeycomb_block",
            "minecraft:nether_wart_block", "minecraft:warped_wart_block",
            "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block",
            "minecraft:mushroom_stem", "minecraft:bamboo_block", "minecraft:bamboo_planks",
            "minecraft:bamboo_mosaic", "minecraft:stripped_bamboo_block");

    private HiddenBlocks() {
    }

    /** Whether a state is certainly a full cube that hides the faces touching it. */
    public static boolean isFullOpaqueCube(BlockState state) {
        if (state.isAir() || state.isAbsent() || state.isWaterlogged() || "minecraft:light".equals(state.name())) {
            return false;
        }
        String name = state.name();
        if (FULL_EXCEPTIONS.contains(name)) {
            return true;
        }
        for (String fragment : SEE_THROUGH) {
            if (name.contains(fragment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the linear indices (see {@link Region#indexOf}) of every hidden block
     */
    public static BitSet find(Structure structure, Palette palette) throws IOException {
        Region region = structure.region();
        boolean[] opaqueState = new boolean[palette.size()];
        for (int i = 0; i < opaqueState.length; i++) {
            opaqueState[i] = isFullOpaqueCube(palette.state(i));
        }
        BitSet opaque = new BitSet(Math.toIntExact(region.volume()));
        structure.forEachBlock(block -> {
            if (opaqueState[block.stateIndex()] && !block.hasBlockEntity()) {
                opaque.set((int) region.indexOf(block.pos().x(), block.pos().y(), block.pos().z()));
            }
        });

        int width = region.width();
        int height = region.height();
        int length = region.length();
        int layer = width * length;
        BitSet hidden = new BitSet(opaque.size());
        for (int i = opaque.nextSetBit(0); i >= 0; i = opaque.nextSetBit(i + 1)) {
            int x = i % width;
            int z = (i / width) % length;
            int y = i / layer;
            if (x == 0 || y == 0 || z == 0 || x == width - 1 || y == height - 1 || z == length - 1) {
                continue;
            }
            if (opaque.get(i - 1) && opaque.get(i + 1)
                    && opaque.get(i - width) && opaque.get(i + width)
                    && opaque.get(i - layer) && opaque.get(i + layer)) {
                hidden.set(i);
            }
        }
        return hidden;
    }
}
