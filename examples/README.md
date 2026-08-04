# Examples

Sample WorldEdit Sponge schematics, regenerated with:

```bash
./gradlew generateExamples
```

| File | Layout | Size | What it covers |
|---|---|---|---|
| `medieval-house.schem` | Sponge v3 | 9×6×7, 378 blocks | Stairs, slabs, doors, trapdoors, fences, walls, logs, rails, redstone, buttons, lanterns, campfires, waterlogging, plus 17 block entities and 2 entities |
| `medieval-house-v2.schem` | Sponge v2 | 9×6×7, 378 blocks | The same build in the older layout — converting both must give byte-identical output |
| `medieval-house-v1.schem` | Sponge v1 | 9×6×7, 378 blocks | The same build again, with `TileEntities` and no `DataVersion`/`Entities`. Its output has an empty `entities` list, because v1 has nowhere to store the two entities |
| `castle.schem` | Sponge v3 | 48×24×48, 55 296 blocks | Scale with a small palette |
| `city-block.schem` | Sponge v3 | 48×16×48, 36 864 blocks | 200 block entities spread over a wide area |
| `empty.schem` | Sponge v3 | 0×0×0 | Degenerate input |
| `single-block.schem` | Sponge v3 | 1×1×1 | Smallest useful input |

Convert them all:

```bash
java -jar build/libs/SchemToCreate.jar "examples/*.schem" --overwrite --verbose
```

The house is worth looking at with `--verbose`: it deliberately contains one of every
block entity type mentioned in the project brief (chest, barrel, furnace, smoker, blast
furnace, shulker box, hopper, dropper, dispenser, sign, lectern, beehive, jukebox, respawn
anchor, spawner, banner, campfire), so a regression in block entity handling shows up as a
missing entry in the block entity count.
