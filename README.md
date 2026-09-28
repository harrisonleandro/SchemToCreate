# SchemToCreate

Converts WorldEdit **Sponge Schematic** files (`.schem`) into **Create Schematic** files
(`.nbt`) that open in Create's Schematic Table and print with the Schematicannon.

Fully offline. No Minecraft, no WorldEdit, no Forge, no Fabric, no NeoForge — just a JVM.

**Download:** grab the latest build from the
[Releases page](https://github.com/harrisonleandro/SchemToCreate/releases/latest).
`SchemToCreate-windows.zip` contains `SchemToCreate.exe` with Java bundled — unzip it and
run the `.exe`, nothing else to install. `SchemToCreate.jar` runs anywhere with Java 17+.

**As an app:** double-click `SchemToCreate.jar`, drag your `.schem` files onto the window,
press Convert. It writes straight into `.minecraft/schematics`, so the build shows up in the
Schematic Table with no further steps.

**As a command:**

```bash
java -jar SchemToCreate.jar medieval-house.schem
# medieval-house.schem -> medieval-house.nbt
```

| | |
|---|---|
| **Target** | Minecraft 1.20.1 · Create 6.0.8 |
| **Input** | Sponge Schematic v1, v2 and v3 (`.schem`, `.schematic`), GZIP or plain |
| **Output** | GZIP compressed vanilla structure NBT (`.nbt`) |
| **Runtime** | Java 17 or newer (tested on 17 and 21) |
| **Runtime dependencies** | picocli, SLF4J, Logback — all bundled in the jar |

---

## Contents

- [Building](#building)
- [The desktop app](#the-desktop-app)
- [Running from a command line](#running-from-a-command-line)
- [Command line reference](#command-line-reference)
- [How the Sponge format works](#how-the-sponge-format-works)
- [How the Create format works](#how-the-create-format-works)
- [What gets preserved](#what-gets-preserved)
- [Create's real size limits](#creates-real-size-limits)
- [Performance](#performance)
- [Project structure](#project-structure)
- [Library choices](#library-choices)
- [Adding a new format](#adding-a-new-format)
- [Testing](#testing)
- [Known limitations](#known-limitations)
- [License](#license)

---

## Building

Requires a JDK 17 or newer. The Gradle wrapper downloads everything else.

```bash
./gradlew build
```

```cmd
gradlew.bat build
```

That runs the test suite and writes the standalone jar to `build/libs/SchemToCreate.jar`.
Convenience wrappers are in `scripts/`:

```bash
scripts/build.sh
```

```cmd
scripts\build.bat
```

Other useful tasks:

```bash
./gradlew test
```

```bash
./gradlew generateExamples
```

The jar is self-contained: `Main-Class` is set and all dependencies are unpacked into it, so
`java -jar` works with nothing else installed. It is built by a plain `Jar` task rather than
the Shadow plugin — the dependency set is small and unshaded, so unpacking is enough, and the
build needs no third-party plugins.

### A `schemtocreate` command

```bash
./gradlew installDist
```

writes launchers to `build/install/schemtocreate/bin/` (`schemtocreate` and
`schemtocreate.bat`). Put that directory on your `PATH` and every example below works
verbatim:

```bash
schemtocreate medieval-house.schem
```

---

## The desktop app

Running the jar with no arguments opens a window:

```bash
java -jar build/libs/SchemToCreate.jar
```

On Windows, double-clicking `SchemToCreate.jar` does the same. `scripts/SchemToCreate.bat`
launches it through `javaw` so no console window sits behind it — copy that next to the jar
and make a desktop shortcut to it.

What the window does:

- **Drag and drop** `.schem` files onto it, or onto the queue below. Dropping a **folder**
  queues every schematic inside it, at any depth. Clicking the drop area opens a file dialog
  instead, for anyone who cannot drag.
- **Finds your Minecraft installation** and offers `.minecraft/schematics` as the
  destination, pre-selected when it exists — that is where a file has to be before Create's
  Schematic Table will list it. `%APPDATA%\.minecraft` on Windows,
  `~/Library/Application Support/minecraft` on macOS, `~/.minecraft` on Linux. You can point
  it anywhere else, or leave each `.nbt` beside its source.
- **Shows per-file results**: dimensions, block count, output size, and any Create
  compatibility warning — including the [parse limit](#creates-real-size-limits), which is
  the thing most likely to bite on a large build.
- **"Ignorar blocos de ar"** is the window's `--skip-air`: a much smaller file (handy for
  servers' 256 KiB upload limit), at the cost described [below](#command-line-reference).
- **Converts off the UI thread**, so the window stays responsive while a large schematic
  streams to disk.

The window and the command line share the same converter; nothing about the conversion
differs between them.

To open the app with files already queued:

```bash
java -jar SchemToCreate.jar --gui house.schem castle.schem
```

Without a display — a server, a CI job, an SSH session — the jar falls back to printing
usage rather than failing on a missing toolkit, and `--gui` reports why it cannot open.

## Running from a command line

Convert one file, writing `medieval-house.nbt` beside it:

```bash
java -jar SchemToCreate.jar medieval-house.schem
```

Convert to an explicit destination:

```bash
java -jar SchemToCreate.jar medieval-house.schem output.nbt
```

Convert every schematic in the current directory:

```bash
java -jar SchemToCreate.jar "*.schem"
```

Convert a whole tree with eight worker threads, replacing existing outputs:

```bash
java -jar SchemToCreate.jar --recursive builds/ --threads 8 --overwrite
```

Then drop the resulting `.nbt` files into `.minecraft/schematics/` and they appear in the
Schematic Table.

> **Quote your globs.** `bash` expands `*.schem` itself, which is fine, but `cmd.exe` and
> PowerShell do not — so the tool expands globs on its own. Quoting makes the behaviour the
> same on every platform.

### Two positional arguments

`schemtocreate a.schem b.nbt` means *input then output*. `schemtocreate a.schem b.schem`
means *two inputs*. The second argument is only treated as a destination when it ends in
`.nbt`; use `--output` if you want to be explicit.

---

## Command line reference

```
Usage: schemtocreate [-hqrvV] [--benchmark] [--debug] [--dry-run] [--overwrite]
                     [--skip-air] [--author=<name>] [--compression=<1-9>]
                     [-d=<dir>] [--data-version=<n>] [--entities=<true|false>]
                     [-o=<file>] [--stream-threshold=<MiB>]
                     [--structure-void=<air|keep>] [--threads=<n>] <input>...
```

| Option | Default | Effect |
|---|---|---|
| `<input>...` | — | Files, directories or glob patterns |
| `--gui` | — | Open the window instead of converting; any paths given are pre-queued |
| `-o`, `--output=<file>` | — | Explicit output file (single input only) |
| `-d`, `--output-dir=<dir>` | input's directory | Collect outputs in one directory |
| `-r`, `--recursive` | off | Descend into subdirectories |
| `--overwrite` | off | Replace existing outputs instead of skipping them |
| `--entities=<true\|false>` | `true` | Write the `entities` list |
| `--skip-air` | off | Omit air blocks (see note below) |
| `--structure-void=<air\|keep>` | `air` | Treatment of `minecraft:structure_void` |
| `--compression=<1-9>` | `6` | GZIP level |
| `--author=<name>` | omitted | Value for the optional `author` field |
| `--replace=<from=to>` | — | Substitute a block everywhere. Repeatable. Properties are kept |
| `--list-palette` | off | List every distinct block the build uses, commonest first |
| `--repair-signs=<true\|false>` | `true` | Rewrite 1.21.5+ sign text into the JSON components 1.20.1 needs |
| `--data-version=<n>` | source's, else `3465` | Force the `DataVersion` field |
| `--threads=<n>` | CPU count | Files converted in parallel |
| `--stream-threshold=<MiB>` | `32` | Block data above this is streamed, not buffered |
| `-v`, `--verbose` | off | Palette, entity and size details |
| `--debug` | off | Timestamped log of every step |
| `--benchmark` | off | Throughput and peak heap |
| `-q`, `--quiet` | off | Warnings and errors only |
| `--dry-run` | off | Resolve inputs and outputs, then stop |
| `-h`, `--help` / `-V`, `--version` | — | Usage / version |

Exit codes: `0` success, `1` at least one conversion failed, `2` bad usage.

With **no arguments at all** the jar opens [the window](#the-desktop-app) when a display is
available, and prints this usage text when one is not.

**About `--skip-air`.** Air is written by default because it is meaningful: in the
Schematicannon's *replace solid* and *replace any* modes, an air block tells the cannon to
clear that position. Omitting air makes the file smaller and the printed result look the
same on empty ground, but the cannon will no longer clear anything. It also costs an extra
pass, because an NBT list must declare its length before its elements.

**About `--structure-void`.** Create's own save path
(`SchematicAndQuillItem.replaceStructureVoidWithAir`) rewrites structure void to air, so
this tool does the same by default and produces the file Create would have produced for the
same region. Use `--structure-void keep` to leave the entries alone.

---

## How the Sponge format works

A `.schem` file is GZIP compressed NBT. All three specification versions store the same
thing; they disagree about where it lives.

**v1 and v2** put everything at the root, and name the root tag `Schematic`:

```
TAG_Compound "Schematic"
  Version        TAG_Int              1 or 2
  DataVersion    TAG_Int              Minecraft data version of the source world
  Metadata       TAG_Compound         Name, Author, Date, WEOffsetX/Y/Z
  Width          TAG_Short            X size, unsigned
  Height         TAG_Short            Y size, unsigned
  Length         TAG_Short            Z size, unsigned
  Offset         TAG_Int_Array[3]     where the origin sat in the source world
  PaletteMax     TAG_Int              palette entry count
  Palette        TAG_Compound         "minecraft:oak_stairs[facing=north,...]" -> TAG_Int
  BlockData      TAG_Byte_Array       varint palette indices
  BlockEntities  TAG_List<Compound>   { Id, Pos: int[3], ...payload spliced in }
  Entities       TAG_List<Compound>   { Id, Pos: double[3], ...payload spliced in }
```

**v3** nests the schematic inside an unnamed root, groups blocks into their own container,
and moves each entry's payload into a `Data` compound:

```
TAG_Compound ""
  Schematic    TAG_Compound
    Version    TAG_Int              3
    Width/Height/Length, Offset, Metadata, DataVersion   as above
    Blocks     TAG_Compound
      Palette        TAG_Compound         same mapping as v2
      Data           TAG_Byte_Array       varint palette indices
      BlockEntities  TAG_List<Compound>   { Id, Pos: int[3], Data: Compound }
    Biomes     TAG_Compound         Palette + Data, no counterpart in a structure file
    Entities   TAG_List<Compound>   { Id, Pos: double[3], Data: Compound }
```

Version 1 differs from version 2 mainly in calling the list `TileEntities` and having no
`Offset`.

Three details drive the implementation:

1. **Block order is YZX** — index `x + z*Width + y*Width*Length`. X varies fastest.
2. **Indices are unsigned varints** in a plain byte array. They have no fixed stride, so
   position *n* cannot be found without decoding everything before it. Every consumer in
   this project is therefore a single forward pass.
3. **`Width`/`Height`/`Length` are unsigned shorts.** Java has no unsigned short, so a
   40 000-wide schematic is stored as `-25536` and has to be masked back.

`SpongeLayout` resolves all of this. It reads `Version` when present but trusts the document
shape over it, because files in the wild carry stale version numbers.

---

## How the Create format works

**Create does not define a schematic format.** It reads vanilla structure files — the same
ones a structure block produces. From `SchematicItem.loadSchematic`:

```java
try (DataInputStream stream = new DataInputStream(new BufferedInputStream(
        new GZIPInputStream(Files.newInputStream(path, StandardOpenOption.READ))))) {
    CompoundTag nbt = NbtIo.read(stream, new NbtAccounter(0x20000000L));
    t.load(level.holderLookup(Registries.BLOCK), nbt);
}
```

So the target is: GZIP, then plain NBT, then vanilla `StructureTemplate.load`. Which means:

```
TAG_Compound ""                        root is unnamed
  size         TAG_List<TAG_Int>[3]    width, height, length
  palette      TAG_List<TAG_Compound>  { Name: TAG_String, Properties?: TAG_Compound of TAG_String }
  blocks       TAG_List<TAG_Compound>  { pos: TAG_List<TAG_Int>[3], state: TAG_Int, nbt?: TAG_Compound }
  entities     TAG_List<TAG_Compound>  { pos: TAG_List<TAG_Double>[3], blockPos: TAG_List<TAG_Int>[3], nbt: TAG_Compound }
  DataVersion  TAG_Int
  author       TAG_String              optional; Create does not write or read it
```

The subtle part is that `StructureTemplate.load` reads every field with a **typed**
accessor — `getList("size", 3)`, `getList("blocks", 10)`, `getInt("state")`. A type mismatch
is not an error: `CompoundTag.getList` returns an *empty list* when the type does not match.
A file with, say, `pos` as a `TAG_Int_Array` instead of a `TAG_List` loads without complaint
and contains nothing. That failure mode is why the test suite validates exact tag types
rather than just field presence (`StructureNbtValidator`).

Two conversions are required rather than optional:

- **Block entity id.** Sponge writes `Id`, vanilla writes `id`. Vanilla's own writer emits
  `id` because it comes from `BlockEntity.saveWithId()`, and `StructureTemplate` feeds the
  compound straight back to `BlockEntity.load` on placement.
- **Positional bookkeeping.** `Pos`, `x`, `y`, `z` are stripped from block entity payloads;
  the position lives in the enclosing block entry, and a stale copy would place the block
  entity at its original world coordinates.

Entities get `pos` (doubles), `blockPos` (`floor(pos)`, as vanilla computes it) and an
`nbt` payload carrying `id` and a refreshed `Pos`. `UUID` is dropped so that pasting the
same schematic twice does not create two entities claiming the same identity.

---

## What gets preserved

Block states are **never interpreted**. A palette entry is parsed into a name and an ordered
map of property strings, and written back out unchanged. That is what makes this list work
without the converter knowing anything about any of it:

> stairs · slabs · walls · fences · fence gates · doors · trapdoors · buttons · levers ·
> pressure plates · rails and powered rails · redstone wire, repeaters and comparators ·
> logs and stripped logs (`axis`) · signs and hanging signs · banners · campfires ·
> lanterns · chains · candles · beds · chests (`type=left/right`) · glass panes and iron bars ·
> waterlogging on every block that supports it · and every modded block, because unknown
> names and unknown properties are copied like any other

Block entities are the same story: an id plus an opaque compound, copied verbatim. Chests,
barrels, furnaces, smokers, blast furnaces, shulker boxes, hoppers, droppers, dispensers,
signs, lecterns, beehives, jukeboxes, respawn anchors, spawners, banners, campfires and
anything a mod defines all survive — including `Items`, `LootTable`, `Lock`, `CustomName`,
`SpawnData`, `RecordItem`, `Bees` and the rest. There is no whitelist of supported types,
deliberately: a whitelist silently drops whatever it does not recognise.

The `examples/medieval-house.schem` fixture contains one of every block entity named above,
so a regression shows up immediately as a lower block entity count.

---

## Create's real size limits

This is the part worth reading before converting a large build. Create enforces **two**
limits, and neither is the size of the file on disk.

**1. The parse budget — around 2.4 million blocks.**

`SchematicItem.loadSchematic` parses with `new NbtAccounter(0x20000000L)`: a 512 MiB budget.
The accounter does not count bytes in the file; it charges a fixed cost per *tag*. One
`blocks` entry is a compound holding a three-element list of ints and one more int, which
comes to roughly 220 accounted bytes regardless of how well the file compresses. So:

```
536 870 912 / 220  ≈  2 440 000 blocks
```

Past that, the Schematic Table fails to load the file no matter how small it is on disk. A
128×128×128 cube is about 2.1 million blocks and fits; 160³ is 4.1 million and does not.

**2. The server upload limit — 256 KiB by default.**

`maxTotalSchematicSize` in `create-server.toml` defaults to 256 KiB and applies to the file
itself. Single player and local previews are unaffected; uploading to a server is not.
Raising it is a server-side config change.

SchemToCreate estimates both after every conversion and warns:

```
WARN  Estimated NBT budget 21120.3 MiB exceeds Create's 512 MiB parse limit; the Schematic
      Table will fail to load this file. Split the build into smaller schematics (roughly
      2,440,322 blocks each).
WARN  File is 251632.4 KiB, above Create's default maxTotalSchematicSize of 256 KiB. Single
      player is unaffected; uploading to a server needs that config raised in
      create-server.toml.
```

The converter still writes the file — the limits belong to Create, not to the format, and
the output is a perfectly valid structure file that other tools will read.

---

## Performance

Measured on this machine (JDK 17, Windows 11), converting a Sponge v3 source with a
304-entry palette so that every index needs a multi-byte varint:

| Blocks | Time | Throughput | Peak heap | Uncompressed NBT | Output |
|---:|---:|---:|---:|---:|---:|
| 3 538 944 | 2.0 s | 1.78 M/s | 16 MiB | 122 MiB | 7.6 MiB |
| 33 554 432 | 15.9 s | 2.11 M/s | 15 MiB | 1.13 GiB | 82 MiB |
| **100 663 296** | **46.8 s** | **2.15 M/s** | **76 MiB** | **3.38 GiB** | **246 MiB** |

Every run used `-Xmx256m` and `--stream-threshold 1` to force the streaming path. Re-parsing
the 246 MiB output to validate it took 24 s, also within a 256 MiB heap. The smallest run is
the slowest per block because two seconds is not long enough to finish JIT warm-up.

Memory stays flat because nothing proportional to block count is ever resident:

- **Reading.** `NbtDocument` parses the file into a tag tree but *skips* byte arrays above
  `--stream-threshold`, recording only their offset and length. `ByteSource.open()` later
  re-inflates the file up to that offset and hands back a bounded stream. Small schematics
  keep their block data inline and never pay for a second pass.
- **Decoding.** `VarintBlockIndexSource` decodes indices through its own 64 KiB buffer with
  a single-byte fast path, which covers every palette under 128 entries.
- **Writing.** `NbtWriter` splits named writes from bare payload writes, so a list can
  declare its length up front and then stream its elements. `blocks` is emitted entry by
  entry straight into the GZIP sink.

`--threads` parallelises across *files*, not within one. A single conversion is sequential
by nature: varint data cannot be indexed without decoding what precedes it, and the output
list must be written in order. Splitting one file across threads would require buffering it,
which is exactly what this design avoids.

---

## Project structure

```
src/main/java/dev/schemtocreate/
├── cli/           Main, ConvertCommand (picocli), InputResolver, BatchRunner,
│                  ConsoleProgress, ConversionReport, LoggingConfigurator
├── gui/           SchemToCreateApp, DropZone, OptionsPanel, FileQueueModel,
│                  QueuedFile, ConversionTask, MinecraftPaths, GuiLauncher
├── io/            ByteSource
│   └── nbt/       Streaming NBT codec: NbtReader, NbtWriter, NbtDocument, NbtIo,
│                  NbtType, NbtLimits and the twelve tag types
├── reader/        SchematicReader, SchematicReaderRegistry, SchematicFormatException
│   └── sponge/    SpongeSchematicReader, SpongeLayout, SpongeVersion, SpongePayloads,
│                  VarintBlockIndexSource
├── writer/        StructureWriter, CreateStructureWriter, CreateWriterOptions,
│                  WriteResult, StructureVoidPolicy, CreateCompatibility
├── palette/       Palette, ArrayPalette, PaletteBuilder
├── blockstate/    BlockState, BlockStateParser, BlockStateFormatException
├── entities/      BlockEntityConverter, EntityConverter
├── converter/     SchematicConverter, ConversionOptions, ConversionResult
├── model/         Structure, Region, Block, BlockPos, Vec3d, BlockEntity, Entity,
│                  BlockIndexSource, BlockIndexCursor, SchematicMetadata
└── util/          Varints, CountingInputStream, CountingOutputStream,
                   BoundedInputStream, Formats, MemorySampler, ProgressListener
```

80 main classes, 14 test classes, ~8 800 lines.

The dependency direction is one-way: `{cli, gui} → converter → {reader, writer} → {model,
palette, blockstate, entities} → io → util`. Nothing outside `reader/sponge/` mentions
Sponge, and nothing outside `writer/` mentions Create. `cli` and `gui` are siblings that do
not know about each other beyond `Main` picking one — the CLI never loads a Swing class, so
it still starts on a headless machine.

`model.Structure` is the pivot. It holds a region, a palette, a block entity map, an entity
list, metadata — and a `BlockIndexSource`, which is a *factory* for cursors rather than a
collection. `Structure.forEachBlock` walks the source once and hands out one transient
`Block` at a time, so holding a structure costs the same whether it describes a hut or a
continent.

---

## Library choices

| Dependency | Why |
|---|---|
| **picocli** 4.7.6 | Declarative options, generated `--help`, sensible parsing of the awkward "two positionals may mean input+output" case. Single jar, no transitive dependencies. |
| **SLF4J** 2.0.13 | Required by the brief. Keeps the library layers backend-agnostic. |
| **Logback** 1.5.6 | A backend the standalone jar can actually log through, and one whose level and pattern can be reconfigured after the command line is parsed (`LoggingConfigurator`). |
| **JUnit 5** + **AssertJ** | Parameterized tests carry the v2/v3 matrix; AssertJ's collection assertions make palette and block entity checks readable. Test scope only. |

### Why the NBT codec is hand-written

The brief suggested Adventure NBT, the Sponge Schematic API, Mojang's DataFixerUpper, FastNBT
or JNBT. None of them was adopted, for reasons specific to this problem:

- **Every general-purpose NBT library is tree-based.** They parse a file into an object
  graph. The target format's `blocks` list has one compound *per block*: at 100 million
  blocks that is 100 million compounds, each with a map, a nested list and four boxed
  integers — tens of gigabytes of heap for a 246 MiB file. The 100-million-block requirement
  and a tree-based NBT library are mutually exclusive. What is needed is a codec that can
  declare a list's length and then stream its elements, and a reader that can *skip* a
  400 MiB array and come back to it later. That is what `NbtWriter`, `NbtReader` and
  `NbtDocument` provide, in about 1 500 lines with no dependencies.
- **The Sponge Schematic API** is part of the SpongeAPI platform and pulls in a server
  ecosystem; the brief requires the tool to stand alone.
- **Mojang's DataFixerUpper** is only the *framework* for data fixing. The actual migration
  rules live in Minecraft's own `DataFixers` class, which ships with the game. Depending on
  DFU without the game would add a large dependency that cannot fix anything — so instead
  the tool preserves the source `DataVersion` and warns when it is not 1.20.1. See
  [Known limitations](#known-limitations).
- **JNBT** is unmaintained and predates 1.13 block states.

The trade-off is real: a hand-written codec is code to own. It is covered by
`NbtCodecTest` — round-tripping every tag type, empty lists, truncated input, oversized
length prefixes, GZIP detection, and agreement between the buffered and streamed paths.

---

## Adding a new format

**A new input format** (Litematica, MCEdit `.schematic`, Create `.nbt` as *input*):

1. Implement `reader.SchematicReader`: `formatName()`, `supports(Path)` — content-based, not
   extension-based — and `read(Path)` returning a `Structure`.
2. Register it in `SchematicReaderRegistry.withDefaults()`.

Nothing downstream changes. If the format stores bulk block data as one large array, reuse
`NbtDocument` + `ByteSource` and implement `BlockIndexSource` so it streams like the Sponge
reader does; if it stores blocks some other way, implement `BlockIndexSource` however suits
it — `ArrayBlockIndexSource` is a working in-memory example.

**A new output format:** implement `writer.StructureWriter` and construct it in
`SchematicConverter.writeAtomically`. `CreateStructureWriter` is the reference, including how
to stream a list of unbounded length.

The one rule: `model` must stay free of format-specific concepts. `BlockEntity` and `Entity`
do reference `NbtCompound`, which is intentional — NBT is the common currency of both
formats, and an extra abstraction over it would be indirection with no second implementation
behind it.

---

## Testing

```bash
./gradlew test
```

105 tests. The suite runs with a **512 MiB heap on purpose**: `LargeSchematicTest` converts
3.5 million blocks, which cannot be held as a tag tree in that space, so a regression that
reintroduces buffering fails with an `OutOfMemoryError` rather than merely getting slower.

Coverage by area:

| Test | What it pins down |
|---|---|
| `NbtCodecTest` | Every tag type round-trips; empty lists use `TAG_End`; truncated and oversized input is rejected; GZIP is detected; buffered and deferred byte-array paths agree byte for byte |
| `BlockStateParserTest` | Property order preserved, implicit namespace, modded blocks, malformed input rejected |
| `RegionTest` | YZX indexing, `indexOf`/`positionOf` are inverses across a whole region, `long` volume arithmetic, negative-coordinate flooring |
| `PaletteBuilderTest` | Declared indices honoured, sparse gaps filled with air, 5 000-entry palettes, negative indices rejected |
| `SpongeSchematicReaderTest` | v2 and v3 produce identical models; block entities in both the inline and nested forms; unsigned dimensions; empty and single-block inputs; truncated block data detected before conversion; content-based detection |
| `SpongeV1Test` | The v1 layout: `TileEntities` instead of `BlockEntities`, no `DataVersion`, no `Entities`; v1/v2/v3 of the same build give identical output; a palette-less v1 file (pre-1.13 numeric IDs) is reported rather than guessed at |
| `ConversionEndToEndTest` | House, castle, city; every block state property preserved; all 17 block entity types intact; entity `pos`/`blockPos`/`nbt`; structure void and skip-air policies; `DataVersion` handling; overwrite protection; no partial files left after a failure |
| `LargeSchematicTest` | Millions of blocks within a small heap; streamed and buffered paths agree; Create's parse budget reported; progress strictly increasing; 64-bit byte accounting |
| `CommandLineTest` | Every documented flag; globs on Windows; recursive and non-recursive walks; parallel batches; failures do not stop the batch; exit codes |
| `GuiModelTest` | The queue model: extension filtering, folder expansion, deduplication, status rendering, clear and reset; and the launch decision, including the headless fallback |

The suite also runs headless (`java.awt.headless=true`), which makes the launch decision
deterministic and guarantees no test can pop a window open on a build machine.

`StructureNbtValidator` is the gate for every conversion test. It re-reads the output and
asserts the exact tag types `StructureTemplate.load` requires, catching the silent-empty
failure mode described above. It streams the `blocks` list rather than loading it, so it can
validate a 100-million-block file in a 256 MiB heap.

---

## Known limitations

**No data fixing, and version gaps hurt in both directions.** Block and block entity data is
copied as-is, and Create applies no data fixer either. The tool names the source version and
says what to expect:

```
WARN  This schematic was made in a version newer than Minecraft 1.21.5 (DataVersion 4440),
      which is newer than Minecraft 1.20.1. Any block that did not exist yet in the version
      you play becomes air when Create resolves the palette - silently, with nothing logged.
      Run with --list-palette to see every block the build uses.
WARN  No data fixer is applied: Minecraft's migration rules ship with the game, so an offline
      tool cannot run them.
```

*Older source:* blocks renamed since then, and block entities whose shape changed (pre-1.20
sign text) will not load. Re-save the schematic once in your target version with WorldEdit.

*Newer source* — the common case, since most published builds are made in the latest
Minecraft — is worse, because it fails **silently**. Vanilla's palette resolution substitutes
air for any block id it does not recognise, and nothing is logged. A tavern built in 1.21.8
using pale oak simply arrives with pale-oak-shaped holes in it.

The workflow for that:

```bash
schemtocreate tavern.schem --list-palette
```

Read the listing, spot the blocks your version does not have, then map them onto ones it
does:

```bash
schemtocreate tavern.schem --replace pale_oak_planks=spruce_planks \
                           --replace stripped_pale_oak_log=stripped_spruce_log \
                           --replace pale_moss_carpet=moss_carpet
```

Substitutions are applied to the palette, so they cost nothing per block and keep block
properties: a stripped log swapped for another stripped log keeps its `axis`, a slab keeps
its `type`. The `minecraft:` namespace is implicit.

### Sign text: the one incompatibility that is fixed automatically

Blocks resolving to air is annoying. Sign text is worse, and it is repaired by default.

In 1.20.1 every line of a sign is decoded by `ExtraCodecs.FLAT_COMPONENT_CODEC`, which
expects a **JSON chat component** — a blank line is the string `{"text":""}`. From Minecraft
1.21.5 components are stored as plain NBT, so a blank line is written as `""`. Feeding that
to 1.20.1 makes `Component.Serializer.fromJson` return null, `ImmutableList.Builder.add`
throws a `NullPointerException`, and that exception propagates out of `SignBlockEntity.load`
through `StructureTemplate.placeInWorld`:

```
java.lang.NullPointerException
    at com.google.common.collect.ImmutableList$Builder.add
    at com.mojang.serialization.codecs.ListCodec.decode
    at net.minecraft.world.level.block.entity.SignBlockEntity.load(SignBlockEntity.java:106)
    at ...StructureTemplate.placeInWorld(StructureTemplate.java:251)
    at com.simibubi.create.content.schematics.client.SchematicHandler.setupRenderer
```

Placement aborts for the **entire structure**. A single blank sign in a twenty-thousand
block build makes the whole schematic appear empty, and Create reports only
`Failed to load Schematic - Check the game logs`.

`--repair-signs` (on by default) wraps any line that is not already JSON into a `text`
component, escaping it properly. Lines that are already JSON are untouched. Pass
`--repair-signs false` to keep the source bytes verbatim.

**Biomes are dropped.** Sponge v3 can carry a biome palette. Vanilla structure files have no
biome field, so there is nowhere to put it. The tool logs when it discards biome data.

**Entities need materials to print.** They are written to the `entities` list exactly as
vanilla structure placement expects, and Create's Schematicannon does print them: it runs a
dedicated `PrintStage.ENTITIES` phase after blocks and deferred blocks, turning each entity
into an `ItemRequirement` it must consume from its inventory. So an armour stand needs an
armour stand item, an item frame needs an item frame, and so on — the cannon stalls if they
are missing. Use `--entities false` when converting a build whose entities are decoration
you do not intend to supply materials for.

**Unknown blocks degrade to air in game.** Vanilla's `NbtUtils.readBlockState` substitutes
air for a block id that is not registered. Converting a modded schematic for an instance
without those mods produces holes. The conversion itself is lossless — the palette entry is
written correctly — so the same file works once the mod is installed.

**Maximum size.** A dimension cannot exceed 65 535 (Sponge stores it as an unsigned short),
and a structure cannot exceed `Integer.MAX_VALUE` blocks (an NBT list length is a signed
int). Create's own limits bind long before either.

---

## License

MIT. See [LICENSE](LICENSE).

Not affiliated with the Create mod, EngineHub/WorldEdit, SpongePowered or Mojang. Format
details were taken from the Sponge Schematic Specification and from Create's and Minecraft's
published source.
