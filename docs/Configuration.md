# Configuration

The mod's settings are in `config/earth_worldgen.json` in the server's folder. The mod writes this
file with every setting and its default the first time the server starts. After an update, settings
added in the new version are added to the file with their defaults; nothing you changed is touched.
The file is read once, at start-up, so restart the server after editing it.

```json
{
  "dataDir": "earth-data/elevation",
  "landCoverDir": "earth-data/worldcover",
  "urbanDir": "earth-data/urban",
  "horizontalScale": 5,
  "metresPerBlockY": 5,
  "seaLevel": 160,
  "unknownSeaDepth": 6,
  "villagersPerCityChunk": 0.25,
  "cities": {
    "enabled": true,
    "buildings": true,
    "roads": true,
    "villagers": true,
    "disabledCountries": []
  },
  "monuments": {
    "enabled": true,
    "disabled": [],
    "disabledCountries": [],
    "extraFiles": []
  },
  "structures": {
    "disabled": ["minecraft:villages"]
  },
  "animals": {
    "realRanges": true,
    "axolotls": true
  }
}
```

> **Changes only affect new land.** Chunks that already exist are never remade. Switching
> buildings or a monument off does not remove what is already built, and changing the scale,
> heights or sea level after players have explored leaves visible seams between old and new land.

## Data folders

Relative paths are read from the server's folder.

| Setting | Default | Meaning |
|---|---|---|
| `dataDir` | `earth-data/elevation` | Packed elevation tiles (`N44/N44E008.ehg`, …). A missing tile is open sea. |
| `landCoverDir` | `earth-data/worldcover` | ESA WorldCover GeoTIFFs. Without them, biomes go by height and latitude only, there are no inland lakes or rivers, and land below sea level floods. |
| `urbanDir` | `earth-data/urban` | Packed streets and buildings (`N44/N44E008.urb`, …). Nothing is built where a tile is missing. |

## Map

| Setting | Default | Meaning |
|---|---|---|
| `horizontalScale` | `5` | Real metres per block on the ground, at the equator. `5` is a 1:5 map. |
| `metresPerBlockY` | `5` | Real metres of height per block. Lower values exaggerate the relief. |
| `seaLevel` | `160` | The Y of the sea. **Must match `sea_level` in the datapack's noise settings** (`datapack/data/earth_worldgen/worldgen/noise_settings/earth.json`). |
| `unknownSeaDepth` | `6` | How many blocks below sea level the sea bed lies where the data gives no depth. |

## Cities

| Setting | Default | Meaning |
|---|---|---|
| `cities.enabled` | `true` | `false`: no streets, buildings or city villagers anywhere. |
| `cities.buildings` | `true` | Building outlines. |
| `cities.roads` | `true` | Roads, paths, railways and bridges. |
| `cities.villagers` | `true` | Villagers on built-up land. |
| `villagersPerCityChunk` | `0.25` | How many villagers each chunk of built-up land gets when it is first made (a half built-up chunk gets half). |
| `cities.disabledCountries` | `[]` | Two-letter country codes (ISO 3166-1, e.g. `"FR"`, `"US"`) where no streets, buildings or city villagers are made. |

## Monuments

About 250 famous buildings and wrecks, built where they really stand. The full list with ids is in
[Monuments](Monuments.md).

| Setting | Default | Meaning |
|---|---|---|
| `monuments.enabled` | `true` | `false`: no monuments anywhere. |
| `monuments.disabled` | `[]` | Ids of monuments not to build, e.g. `["eiffel_tower", "rms_titanic"]`. |
| `monuments.disabledCountries` | `[]` | Two-letter country codes whose monuments are not built. |
| `monuments.extraFiles` | `[]` | Paths to monument files of your own (see [Monuments](Monuments.md)). |

## Structures

| Setting | Default | Meaning |
|---|---|---|
| `structures.disabled` | `["minecraft:villages"]` | Structure sets the game does not make. Eyes of ender, `/locate` and explorer maps don't find them either. |

The game's structure sets are: `villages`, `pillager_outposts`, `woodland_mansions`,
`desert_pyramids`, `jungle_temples`, `swamp_huts`, `igloos`, `ocean_monuments`, `ocean_ruins`,
`shipwrecks`, `buried_treasures`, `ruined_portals`, `mineshafts`, `strongholds`, `ancient_cities`,
`trail_ruins`, `trial_chambers`. The `minecraft:` in front can be left out. Structure sets added by
other mods or datapacks can be listed by their full id.

Villages are off by default because real towns take their place, with villagers on built-up land.
Remove `"minecraft:villages"` from the list to get the game's villages as well.

## Animals

| Setting | Default | Meaning |
|---|---|---|
| `animals.realRanges` | `true` | Animals in their real ranges: pandas in Sichuan, polar bears in the Arctic and so on. `false`: they spawn by biome, as in any world. |
| `animals.axolotls` | `true` | Axolotls in Mexico City's lakes and canals. |

## Choosing a scale

The map is a plain grid: one degree of longitude and one degree of latitude are the same number of
blocks, `40,075,016.686 / horizontalScale / 360`. At 1:5 that is about 22,264 blocks per degree,
so the world runs from X −4,007,502 to 4,007,502 and Z −2,003,751 to 2,003,751.

- **Smaller numbers make a bigger world.** 1:1 is about 20 million blocks either side of block 0,
  inside the game's 30 million limit. Bigger worlds take longer to cross and need more disk for
  the world. Above 5, houses and streets become too small to show as more than a block.
- **Height has a floor.** The world is 4,064 blocks tall and the Earth's relief, trench to peak, is
  19,784 m, so `metresPerBlockY` below about 4.9 can't fit both Everest and the deepest trenches.
  With a small horizontal scale, keep this at 5: the land then looks flatter than in reality,
  because heights are shrunk more than distances.
- Monuments are designed for 1:5. At other scales they stand at the right latitude and longitude,
  but they keep their size in blocks.
- With the defaults, Everest's summit is at Y 1930 and the Challenger Deep at Y −2027.
