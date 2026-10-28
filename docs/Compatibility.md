# Compatibility

Earth Worldgen runs on the server only, adds no blocks or items, and changes world generation in
the Earth world alone. Other dimensions (the Nether, the End, other mods' worlds) are never
touched. Its hooks only add to the game's code; none replaces a game method outright. So most mods
work alongside it.

Each mod below was tested with `tools/compat_test.py`. The test starts a fresh server with Earth
Worldgen and that mod, then checks four things:
- the server starts and makes the Earth world;
- villages stay off and mineshafts are found;
- the land at the Eiffel Tower comes out with the monument;
- the log has no errors.

## Minecraft 26.3

Tested 2026-10-28 with Earth Worldgen 0.1.

| Mod | Fabric | NeoForge | Notes |
|---|---|---|---|
| Terralith 2.6.5 | ✅ | ✅ | Both replace overworld generation. In the Earth world, Earth's real land wins and Terralith's biomes don't appear; nothing breaks. An add-on that uses Terralith's biomes on the real land is planned. |
| Biomes O' Plenty 26.3.0.0.15 (with TerraBlender) | ✅ | ✅ | BOP's biomes don't appear in the Earth world, because Earth picks biomes from real land cover. An add-on using BOP biomes for real places is planned. Install TerraBlender too (BOP needs it). |
| Waystones 26.3.0.1 | ✅ | ✅ | Wild waystones generate on the real land. Village waystones only appear if you switch villages back on (`structures.disabled`). |
| Lithostitched 2.0.6 | ✅ | ✅ | |
| Chunky 1.5.3 / 1.5.4 | ✅ | ✅ | Pre-generating works: 729 chunks in 16 seconds with every mod on this page installed. Each new area downloads its map data the first time, so a large radius downloads a lot; see [Getting the data](Getting-the-data.md). |
| Lithium 0.26.2 | ✅ | ✅ | |
| C2ME 0.4.2 | ✅ | — | Fabric only. Makes chunks on many threads; Earth sets up its creatures one at a time so other mods' villager code isn't hit from two threads at once. C2ME logs a few warnings about optional mods that aren't installed; they're harmless. |
| Distant Horizons 3.3.4 | ✅ | ✅ | Works. Far-away land it generates also downloads map data. With Chunky as well, Distant Horizons logs "Chunky is not loaded" once at start-up; that's between those two mods and harmless. |
| Cloth Config, YACL, Architectury API, GeckoLib | ✅ | ✅ | Libraries that don't touch world generation. Earth Worldgen doesn't need any of them. |
| Fabric Language Kotlin | ✅ | — | Fabric only. |
| Sodium | — | — | Client-side only, so it never meets this server-side mod. No conflict is possible. |
| YUNG's API | — | — | No 26.3 version exists yet. Planned for 1.20.1. |
| Cobblemon | — | — | No 26.3 version exists yet (it stops at 1.21.1). Planned for 1.20.1. |
| Twilight Forest | — | — | No official 26.x version yet (only an unofficial port up to 26.2). Earth Worldgen recognises the Earth world by its generator, not its height, so the Twilight Forest dimension is left alone. Planned for 1.20.1. |

All of the mods above, installed together on Fabric, also passed.

## Other Minecraft versions

Earth Worldgen 0.1 is for Minecraft 26.3. Versions for **1.20.1** (Fabric and Forge), **1.19.2**
(Fabric and Forge) and **1.21.1** (Fabric and NeoForge) are planned, each with the same tests,
plus YUNG's API, Cobblemon and Twilight Forest where those exist.

## Testing a mod yourself

```
tools/compat_test.py fabric 26.3 <modrinth-slug> [<modrinth-slug> ...] [--data <earth-data folder>]
tools/compat_test.py neoforge 26.3 <modrinth-slug> ...
```

The script downloads the newest version of each mod (and what it needs) from Modrinth. It runs a
test server from `run-dnd/` (Fabric) or `run-neoforge/` (NeoForge) and prints a report. A path to a
jar works too, for mods that aren't on Modrinth.

Found a mod that doesn't get along? Please
[open an issue](https://github.com/sams-minecraft-servers/earth-worldgen/issues) with its name and version.
