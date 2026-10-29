# Compatibility

Earth Worldgen runs on the server only, adds no blocks or items, and changes world generation in
the Earth world alone. Other dimensions (the Nether, the End, other mods' worlds) are never
touched. Its hooks only add to the game's code; none replaces a game method outright. So most mods
work alongside it.

Each mod below was tested with `tools/compat_test.py`. The test starts a fresh server with Earth
Worldgen and that mod, then checks five things:
- the server starts and makes the Earth world;
- villages stay off and mineshafts are found;
- the land at the Eiffel Tower comes out with the monument;
- the log has no errors;
- which biomes and ores were made (with `--biomes`, also at seven places of very different
  climates).

## Minecraft 26.3

Tested 2026-10-28 with Earth Worldgen 0.1.

| Mod | Fabric | NeoForge | Notes |
|---|---|---|---|
| Terralith 2.6.5 | ✅ | ✅ | Terralith's biomes are used where they fit real places (Siberian taiga in Yakutia, shield in Canada, tropical jungle in the Amazon...), with Terralith's own ground blocks. See [Biomes](Biomes.md). |
| Biomes O' Plenty 26.3.0.0.15 (with TerraBlender) | ✅ | ✅ | BOP's biomes are used where they fit real places (bayou in Louisiana, Mediterranean forest in Provence, bog, tundra...), with BOP's own ground blocks. Install TerraBlender too (BOP needs it). With Terralith as well, the two are mixed: 93 biomes in use, 72 of them from the two mods. |
| Waystones 26.3.0.1 | ✅ | ✅ | Wild waystones generate on the real land. Village waystones only appear if you switch villages back on (`structures.disabled`). |
| Lithostitched 2.0.6 | ✅ | ✅ | |
| Chunky 1.5.3 / 1.5.4 | ✅ | ✅ | Pre-generating works: 729 chunks in 16 seconds with every mod on this page installed. Each new area downloads its map data the first time, so a large radius downloads a lot; see [Getting the data](Getting-the-data.md). |
| Lithium 0.26.2 | ✅ | ✅ | |
| C2ME 0.4.2 | ✅ | — | Fabric only. Makes chunks on many threads; Earth sets up its creatures one at a time so other mods' villager code isn't hit from two threads at once. C2ME logs a few warnings about optional mods that aren't installed; they're harmless. |
| Distant Horizons 3.3.4 | ✅ | ✅ | Works. Far-away land it generates also downloads map data. With Chunky as well, Distant Horizons logs "Chunky is not loaded" once at start-up; that's between those two mods and harmless. |
| Cloth Config, YACL, Architectury API, GeckoLib | ✅ | ✅ | Libraries that don't touch world generation. Earth Worldgen doesn't need any of them. |
| Fabric Language Kotlin | ✅ | — | Fabric only. |
| Tech Reborn 6.2.1 | ✅ | — | Fabric only. All its ores generate (tin, lead, silver, bauxite, galena, ruby, sapphire, uranium, iridium...). |
| SimpleOres 2.2.2 | ✅ | ✅ | Tin, mythril and adamantium ores. |
| Useful Ores 2.2.0.1 | ✅ | ✅ | All its ores generate. |
| Assorted Ores 2.0.0 | ✅ | ✅ | All its ores generate. |
| EXP Ore 0.1 | ✅ | ✅ | On NeoForge it logs that its own access transformer file is missing; that's in the mod and harmless. |
| Seamless Ores 5.1.0 | ✅ | ✅ | Its granite, diorite, andesite and tuff ore variants appear, for the game's ores and other mods' (Tech Reborn, Occultism). |
| Large Ore Deposits 26.3.0 | ✅ | ✅ | Loads and runs without problems. Its deposits are very rare, so none came up in the test area. |
| Occultism 1.261.1 | — | ✅ | NeoForge only. Silver ore generates. |
| Applied Energistics 2 26.3.1-alpha | — | ✅ | NeoForge only. Meteorites generate (`/locate structure ae2:meteorite` finds them). |
| Immersive Ores 26.3-0.1 | ❌ | — | This version doesn't start on any dedicated server, with or without Earth Worldgen: one of its mixins needs a client-only class. A problem in that mod. |
| Sodium | — | — | Client-side only, so it never meets this server-side mod. No conflict is possible. |
| YUNG's API | — | — | No 26.3 version exists yet. Planned for 1.20.1. |
| Cobblemon | — | — | No 26.3 version exists yet (it stops at 1.21.1). Planned for 1.20.1. |
| Twilight Forest | — | — | No official 26.x version yet (only an unofficial port up to 26.2). Earth Worldgen recognises the Earth world by its generator, not its height, so the Twilight Forest dimension is left alone. Planned for 1.20.1. |

All of the mods above, installed together on Fabric, also passed. So did the ore mods together:
seven on Fabric and eight on NeoForge.

**Modded ores sit where they would in an ordinary world.** Earth Worldgen measures ore heights
from the real ground, not from Y 0, for any mod that places its ores the usual way (a height range).
In the test, every mod's ores were between the surface and about 60 blocks down, and their deepslate
ores below that, the same as the game's own. None were above ground.

## Other Minecraft versions

Earth Worldgen 0.1 is for Minecraft 26.3. Versions for **1.20.1** (Fabric and Forge), **1.19.2**
(Fabric and Forge) and **1.21.1** (Fabric and NeoForge) are planned, each with the same tests,
plus YUNG's API, Cobblemon and Twilight Forest where those exist.

## Testing a mod yourself

```
tools/compat_test.py fabric 26.3 <modrinth-slug> [<modrinth-slug> ...] [--data <earth-data folder>]
tools/compat_test.py neoforge 26.3 <modrinth-slug> ...
tools/compat_test.py fabric 26.3 terralith --biomes          # biomes at seven test places
tools/compat_test.py fabric 26.3 techreborn --ores _ore,raw_  # count ore blocks by name
```

The script downloads the newest version of each mod (and what it needs) from Modrinth. It runs a
test server from `run-dnd/` (Fabric) or `run-neoforge/` (NeoForge) and prints a report. A path to a
jar works too, for mods that aren't on Modrinth.

Found a mod that doesn't get along? Please
[open an issue](https://github.com/sams-minecraft-servers/earth-worldgen/issues) with its name and version.
