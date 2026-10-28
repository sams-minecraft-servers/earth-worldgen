# Earth Worldgen

A Fabric mod that turns a Minecraft world into the real Earth, made **as players explore it** from
real elevation and land-cover data, instead of from a pre-generated map download.

At the default 1:5 scale, one block is 5 metres. Mountains, coastlines, rivers, lakes, forests,
deserts and ice caps sit where they really are, and Everest and the Mariana Trench both fit in the
world. The map is about 8 million by 4 million blocks.

**Play it:** the mod runs the public server **earth.tj** (Minecraft Java Edition 26.3). Join with an
ordinary, unmodified game.

## What it does

- **Real terrain from real heights.** The ground at each block column comes from one-degree
  elevation tiles, including the sea bed. Nothing is pre-generated: a tile is only read when a chunk
  over it is first made.
- **Real biomes.** The biome at each place comes from its real land cover (forest, cropland, bare
  rock, snow, water, built-up land), read with its height and latitude.
- **Water where the maps put it.** The sea is at sea level, and lakes and rivers stand at their real
  heights. Land below sea level (the Dead Sea shore, the Netherlands) stays dry.
- **A normal game underneath.** Ores, caves, geodes, mineshafts, strongholds, trial chambers and
  ancient cities are at their usual depth *below the real ground*, wherever that is. Caves never
  break into a sea, lake or river. Ocean monuments stand on the real sea bed.
- **Animals where they live.** Pandas in Sichuan's forests, polar bears in the Arctic, llamas in the
  Andes, camels in deserts, salmon in northern waters.
- **People in the cities.** The game's random villages are off by default; villagers appear on real
  built-up land instead, and strongholds are spread over the whole planet.
- **About 250 monuments.** The Eiffel Tower, the Statue of Liberty, the pyramids, the Titanic on the
  sea bed and many more, built where they really stand as the land under them is generated.
- **Optional real streets and buildings.** Building outlines and roads from Overture Maps can be
  built into chunks as they are made. At 1:5 a house is a block or two across.
- **Country radio (optional).** One station per country, found by visiting it, heard clearly inside
  its borders and fading into static beyond them. You supply the audio; the mod ships none.
- **Everything can be switched.** Cities, buildings, roads, city villagers, each monument, each of
  the game's structures and the real animal ranges can be turned off in the config, and cities and
  monuments can be turned off country by country.
- **Server side only.** Players need no mod, no resource pack and no client download.

## Quick start

You need a Fabric server for Minecraft **26.3**, Java **25**, and disk space for the data: about
250 GB for the whole planet, or much less for one region.

1. Put the mod jar in the server's `mods/` folder. Fabric API is not needed.
2. **Before the world is first started**, copy `datapack/` into `<world>/datapacks/earth_worldgen/`.
3. Start the server once to write the settings (`config/earth-mod/earth_worldgen.json` and
   `config/earth-mod/radio.json`), then point them at your data folders.
4. Download and pack the data (`tools/download_data.py`, then `tools/PackElevation.java`).
5. Start the server, and set a spawn point on land (block 0, 0 is open sea off West Africa).

The full walk-through is in [docs/Installing-on-a-server.md](docs/Installing-on-a-server.md).

## Documentation

| Page | What it covers |
|---|---|
| [Installing on a server](docs/Installing-on-a-server.md) | Setting up a new Earth world, step by step |
| [Getting the data](docs/Getting-the-data.md) | Downloading and packing elevation, land cover and streets |
| [Configuration](docs/Configuration.md) | Every setting in `earth_worldgen.json` |
| [Monuments](docs/Monuments.md) | The monuments and their ids |
| [Radio](docs/Radio.md) | Country radio: setting it up and its settings |
| [Finding places](docs/Finding-places.md) | Converting latitude and longitude to block coordinates |
| [How it works](docs/How-it-works.md) | What each part of the mod does to world generation |
| [Building from source](docs/Building-from-source.md) | Compiling the mod and running the test server |
| [Updating Minecraft](docs/Updating-Minecraft.md) | Moving the mod to a new game version |
| [FAQ and limitations](docs/FAQ-and-limitations.md) | Known limits and what has not been tested |

## Status

Version 0.1. The mod runs earth.tj every day, but it has only been used on that one server
(Minecraft 26.3, Linux). The helper scripts in `tools/` assume Linux. Expect rough edges, and
please [open an issue](https://github.com/sams-minecraft-servers/earth-worldgen/issues) when you find one.

## Data and credits

The mod ships with no map data: you download it from public sources, listed with their licences
in [ATTRIBUTION.md](ATTRIBUTION.md). If you run a public world made with it, credit those sources
somewhere players can see, such as your website. That is required for ESA WorldCover (CC BY 4.0)
and for the streets and buildings (ODbL). Country borders come from Natural Earth (public domain).

## Licence

Copyright © 2026 Samuel Swauger. Licensed under the GNU Lesser General Public License v3.0 only
(LGPL-3.0-only): see [COPYING.LESSER](COPYING.LESSER) and [COPYING](COPYING). You may use the mod on
any server and include it in modpacks; changed versions of the mod itself must be shared under the
same licence.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
