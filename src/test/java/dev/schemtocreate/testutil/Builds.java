package dev.schemtocreate.testutil;

import dev.schemtocreate.io.nbt.NbtCompound;
import dev.schemtocreate.io.nbt.NbtList;

import java.util.Random;

/**
 * Representative structures for end-to-end tests.
 *
 * <p>Each one exercises a different pressure point: a house covers the stateful blocks that
 * a naive converter mangles (stairs, slabs, trapdoors, signs, waterlogging), a castle covers
 * scale with a modest palette, and a city covers many block entities at once.
 */
public final class Builds {

    private Builds() {
    }

    /** Small build, heavy on directional and waterloggable blocks plus block entities. */
    public static SchemFixture house() {
        SchemFixture fixture = SchemFixture.of(9, 6, 7).offset(-4, 64, -3);
        fixture.fill(0, 0, 0, 8, 0, 6, "minecraft:stone_bricks");
        fixture.fill(0, 1, 0, 8, 3, 0, "minecraft:oak_planks");
        fixture.fill(0, 1, 6, 8, 3, 6, "minecraft:oak_planks");
        fixture.fill(0, 1, 1, 0, 3, 5, "minecraft:oak_planks");
        fixture.fill(8, 1, 1, 8, 3, 5, "minecraft:oak_planks");

        // Every corner of the roof uses a different stair state.
        fixture.set(1, 4, 1, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        fixture.set(2, 4, 1, "minecraft:oak_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]");
        fixture.set(3, 4, 1, "minecraft:oak_stairs[facing=south,half=top,shape=inner_right,waterlogged=false]");
        fixture.set(4, 4, 1, "minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=true]");

        fixture.set(1, 4, 2, "minecraft:oak_slab[type=top,waterlogged=false]");
        fixture.set(2, 4, 2, "minecraft:oak_slab[type=bottom,waterlogged=true]");
        fixture.set(3, 4, 2, "minecraft:oak_slab[type=double,waterlogged=false]");

        fixture.set(1, 1, 3, "minecraft:oak_door[facing=east,half=lower,hinge=left,open=false,powered=false]");
        fixture.set(1, 2, 3, "minecraft:oak_door[facing=east,half=upper,hinge=left,open=false,powered=false]");
        fixture.set(4, 2, 0, "minecraft:oak_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]");
        fixture.set(5, 1, 0, "minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=true]");
        fixture.set(6, 1, 0, "minecraft:cobblestone_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=tall]");
        fixture.set(2, 1, 5, "minecraft:oak_log[axis=y]");
        fixture.set(3, 1, 5, "minecraft:stripped_birch_log[axis=x]");
        fixture.set(7, 1, 1, "minecraft:lantern[hanging=true,waterlogged=false]");
        fixture.set(7, 1, 2, "minecraft:redstone_wire[east=side,north=up,power=9,south=none,west=side]");
        fixture.set(7, 1, 3, "minecraft:stone_button[face=wall,facing=north,powered=false]");
        fixture.set(7, 1, 4, "minecraft:rail[shape=north_east,waterlogged=false]");
        fixture.set(6, 1, 4, "minecraft:powered_rail[powered=true,shape=east_west,waterlogged=false]");
        fixture.set(5, 1, 4, "minecraft:water[level=0]");

        fixture.blockEntity(2, 1, 1, "minecraft:chest[facing=north,type=single,waterlogged=false]",
                "minecraft:chest", chestWith("minecraft:diamond", 3));
        fixture.blockEntity(3, 1, 1, "minecraft:furnace[facing=north,lit=false]",
                "minecraft:furnace", furnace());
        fixture.blockEntity(4, 1, 1,
                "minecraft:oak_sign[rotation=8,waterlogged=false]",
                "minecraft:sign", sign());
        fixture.blockEntity(5, 1, 1, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]",
                "minecraft:campfire", new NbtCompound().putInt("CookingTimes", 0));
        fixture.blockEntity(6, 1, 1, "minecraft:beehive[facing=north,honey_level=5]",
                "minecraft:beehive", beehive());
        fixture.blockEntity(2, 1, 2, "minecraft:barrel[facing=up,open=false]",
                "minecraft:barrel", chestWith("minecraft:bread", 16));
        fixture.blockEntity(3, 1, 2, "minecraft:hopper[enabled=true,facing=down]",
                "minecraft:hopper", chestWith("minecraft:redstone", 64));
        fixture.blockEntity(4, 1, 2, "minecraft:dispenser[facing=north,triggered=false]",
                "minecraft:dispenser", chestWith("minecraft:arrow", 32));
        fixture.blockEntity(5, 1, 2, "minecraft:jukebox[has_record=true]",
                "minecraft:jukebox", jukebox());
        fixture.blockEntity(6, 1, 2, "minecraft:spawner", "minecraft:mob_spawner", spawner());
        fixture.blockEntity(2, 1, 4, "minecraft:shulker_box[facing=up]",
                "minecraft:shulker_box", chestWith("minecraft:emerald", 1));
        fixture.blockEntity(3, 1, 4, "minecraft:lectern[facing=north,has_book=true,powered=false]",
                "minecraft:lectern", lectern());
        fixture.blockEntity(4, 1, 4, "minecraft:respawn_anchor[charges=3]",
                "minecraft:respawn_anchor", new NbtCompound());
        fixture.blockEntity(2, 1, 3, "minecraft:white_banner[rotation=4]",
                "minecraft:banner", banner());
        fixture.blockEntity(3, 1, 3, "minecraft:smoker[facing=east,lit=true]",
                "minecraft:smoker", furnace());
        fixture.blockEntity(4, 1, 3, "minecraft:blast_furnace[facing=west,lit=false]",
                "minecraft:blast_furnace", furnace());
        fixture.blockEntity(5, 1, 3, "minecraft:dropper[facing=south,triggered=false]",
                "minecraft:dropper", chestWith("minecraft:cobblestone", 64));

        fixture.entity(4.5, 1.0, 3.5, "minecraft:armor_stand",
                new NbtCompound().putByte("Invisible", (byte) 1).putByte("NoGravity", (byte) 1));
        fixture.entity(-0.5, 2.0, 2.5, "minecraft:item_frame",
                new NbtCompound().putByte("Facing", (byte) 4).putByte("Fixed", (byte) 1));
        return fixture;
    }

    /** Large-ish build with a small palette, for throughput and memory behaviour. */
    public static SchemFixture castle(int size) {
        SchemFixture fixture = SchemFixture.of(size, size / 2, size);
        int top = size / 2 - 1;
        fixture.fill(0, 0, 0, size - 1, 0, size - 1, "minecraft:stone_bricks");
        fixture.fill(0, 1, 0, size - 1, top, 0, "minecraft:cobblestone");
        fixture.fill(0, 1, size - 1, size - 1, top, size - 1, "minecraft:cobblestone");
        fixture.fill(0, 1, 0, 0, top, size - 1, "minecraft:cobblestone");
        fixture.fill(size - 1, 1, 0, size - 1, top, size - 1, "minecraft:cobblestone");
        for (int x = 0; x < size; x += 2) {
            fixture.set(x, top, 0, "minecraft:cobblestone_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=low]");
            fixture.set(x, top, size - 1, "minecraft:cobblestone_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=low]");
        }
        fixture.blockEntity(1, 1, 1, "minecraft:chest[facing=south,type=single,waterlogged=false]",
                "minecraft:chest", chestWith("minecraft:gold_ingot", 8));
        return fixture;
    }

    /** Many block entities spread over a wide area, with a deliberately varied palette. */
    public static SchemFixture city(int size, int blockEntityCount) {
        SchemFixture fixture = SchemFixture.of(size, 16, size);
        Random random = new Random(20260803L);
        String[] materials = {
                "minecraft:stone", "minecraft:cobblestone", "minecraft:oak_planks",
                "minecraft:glass", "minecraft:bricks", "minecraft:sandstone"
        };
        fixture.fill(0, 0, 0, size - 1, 0, size - 1, "minecraft:grass_block[snowy=false]");
        for (int i = 0; i < size * size / 4; i++) {
            int x = random.nextInt(size);
            int z = random.nextInt(size);
            int height = 1 + random.nextInt(12);
            fixture.fill(x, 1, z, x, height, z, materials[random.nextInt(materials.length)]);
        }
        for (int i = 0; i < blockEntityCount; i++) {
            int x = i % size;
            int z = (i / size) % size;
            fixture.blockEntity(x, 14, z, "minecraft:chest[facing=north,type=single,waterlogged=false]",
                    "minecraft:chest", chestWith("minecraft:stick", 1 + (i % 63)));
        }
        return fixture;
    }

    private static NbtCompound chestWith(String item, int count) {
        NbtCompound slot = new NbtCompound()
                .putByte("Slot", (byte) 0)
                .putString("id", item)
                .putByte("Count", (byte) count);
        return new NbtCompound()
                .put("Items", new NbtList().add(slot))
                .putString("CustomName", "{\"text\":\"Loot\"}");
    }

    private static NbtCompound furnace() {
        return new NbtCompound()
                .putShort("BurnTime", (short) 120)
                .putShort("CookTime", (short) 40)
                .putShort("CookTimeTotal", (short) 200)
                .put("Items", new NbtList().add(new NbtCompound()
                        .putByte("Slot", (byte) 0)
                        .putString("id", "minecraft:raw_iron")
                        .putByte("Count", (byte) 8)));
    }

    /** 1.20 sign layout: front_text/back_text rather than the pre-1.20 Text1..Text4. */
    private static NbtCompound sign() {
        NbtCompound front = new NbtCompound()
                .putByte("has_glowing_text", (byte) 0)
                .putString("color", "black")
                .put("messages", new NbtList()
                        .add(new dev.schemtocreate.io.nbt.NbtString("{\"text\":\"Welcome\"}"))
                        .add(new dev.schemtocreate.io.nbt.NbtString("{\"text\":\"\"}"))
                        .add(new dev.schemtocreate.io.nbt.NbtString("{\"text\":\"\"}"))
                        .add(new dev.schemtocreate.io.nbt.NbtString("{\"text\":\"\"}")));
        return new NbtCompound()
                .put("front_text", front)
                .put("back_text", front.copy())
                .putByte("is_waxed", (byte) 0);
    }

    private static NbtCompound beehive() {
        return new NbtCompound()
                .put("Bees", new NbtList().add(new NbtCompound()
                        .putInt("MinOccupationTicks", 600)
                        .putInt("TicksInHive", 100)
                        .put("EntityData", new NbtCompound().putString("id", "minecraft:bee"))))
                .putIntArray("FlowerPos", 1, 2, 3);
    }

    private static NbtCompound jukebox() {
        return new NbtCompound().put("RecordItem", new NbtCompound()
                .putString("id", "minecraft:music_disc_cat")
                .putByte("Count", (byte) 1));
    }

    private static NbtCompound spawner() {
        return new NbtCompound()
                .putShort("Delay", (short) 20)
                .putShort("MaxNearbyEntities", (short) 6)
                .putShort("RequiredPlayerRange", (short) 16)
                .put("SpawnData", new NbtCompound()
                        .put("entity", new NbtCompound().putString("id", "minecraft:zombie")));
    }

    private static NbtCompound lectern() {
        return new NbtCompound()
                .putInt("Page", 2)
                .put("Book", new NbtCompound()
                        .putString("id", "minecraft:written_book")
                        .putByte("Count", (byte) 1));
    }

    private static NbtCompound banner() {
        return new NbtCompound().put("patterns", new NbtList().add(new NbtCompound()
                .putString("color", "red")
                .putString("pattern", "minecraft:stripe_bottom")));
    }
}
