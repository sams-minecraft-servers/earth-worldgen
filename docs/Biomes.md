# Biomes

Every place on the Earth gets a biome from three things about the real place:

- its **land cover**: forest, shrubs, grass, crops, town, bare ground, snow and ice, wetland,
  mangroves, moss and lichen, or water (ESA WorldCover, 10 m);
- its **climate**: its Köppen-Geiger class, such as tropical rainforest, hot desert, cold steppe,
  Mediterranean or subarctic (Beck et al. 2023, 1991–2020 averages, packed into the mod);
- its **height**, against the tree line and snow line for its latitude.

These put each place into one of the **kinds** below. Each kind then gets one of its biomes. Most
kinds have a few biomes to choose from, and each one gets its own patches (`biomes.patchBlocks`
across), so a forest isn't one biome for a thousand kilometres.

So the Canadian Shield is boreal forest, the Kazakh steppe is cold steppe, Provence is Mediterranean
shrubland and forest, the Amazon is tropical rainforest, and the high Andes are cloud forest under
rocky and snowy peaks.

## Terralith and Biomes O' Plenty

No extra jar is needed. With [Terralith](https://modrinth.com/datapack/terralith),
[Biomes O' Plenty](https://modrinth.com/mod/biomes-o-plenty) or both installed, the mod uses their
biomes where they fit a real place, as in the table below. With both, their biomes are mixed. Turn
this off with `"modBiomes": false`.

Both mods' biomes come with their own ground as well as their own trees and plants: Terralith's
because Earth Worldgen uses the game's surface rules, which Terralith changes, and Biomes O'
Plenty's because TerraBlender adds them to the overworld's rules, which the Earth world uses. So
Biomes O' Plenty's cold desert is gravel and snow, and Terralith's Siberian taiga has coarse dirt and
tuff.

Biomes O' Plenty and Terralith also make their own overworld, but the Earth world is made by this mod
instead, so their biome maps don't apply there. The Nether and the End are left to them.

## Kinds of place

The left column is the name to use in `biomes.custom`. A biome listed twice is picked twice as often.

| Kind | Where | Minecraft | Terralith | Biomes O' Plenty |
|---|---|---|---|---|
| `tropical_rainforest` | Tropical rainforest and monsoon climates, forest | jungle, jungle, jungle, bamboo_jungle | tropical_jungle, tropical_jungle, rocky_jungle | rainforest, rainforest, tropics |
| `cloud_forest` | Tropical forest above 1,200 m | jungle | cloud_forest, jungle_mountains | rocky_rainforest |
| `tropical_dry_forest` | Tropical savanna climate, forest; hot arid forest | sparse_jungle, sparse_jungle, savanna | — | lush_savanna, subtropics |
| `savanna` | Tropical, grass or shrubs | savanna, savanna, savanna_plateau | savanna, savanna_slopes, fractured_savanna | savanna, lush_savanna |
| `hot_steppe` | Hot steppe (Sahel), grass | savanna | hot_shrubland | scrubland |
| `cold_steppe` | Cold steppe (Kazakhstan, Mongolia), grass | plains | steppe | dryland, wasteland_steppe |
| `hot_shrubland` | Hot steppe, shrubs | savanna | hot_shrubland | scrubland |
| `cold_shrubland` | Cold steppe or cold climates, shrubs | plains | cold_shrubland | shrubland |
| `desert_shrubland` | Desert climates, shrubs or grass | desert | desert, lush_desert | lush_desert |
| `hot_desert` | Hot desert, bare ground | desert | desert, desert, ancient_sands | desert |
| `cold_desert` | Cold desert (Gobi), bare ground | desert | gravel_desert | cold_desert |
| `arid_highlands` | Desert or steppe, bare ground above 1,200 m; desert shrubs above 1,200 m | badlands, badlands, eroded_badlands | arid_highlands, arid_highlands, painted_mountains, sandstone_valley | badlands |
| `mediterranean_forest` | Mediterranean climates, forest | forest | — | mediterranean_forest |
| `mediterranean_shrubland` | Mediterranean climates, shrubs or grass | plains, savanna | shrubland, rocky_shrubland | shrubland, rocky_shrubland |
| `subtropical_forest` | Humid subtropical (south-east USA, south China), forest | forest, forest, dark_forest | — | woodland, seasonal_forest |
| `temperate_forest` | Oceanic and temperate climates, forest | forest, forest, forest, birch_forest, flower_forest | — | woodland, seasonal_forest, old_growth_woodland |
| `temperate_rainforest` | Cool oceanic climates (Norway coast, south Chile), forest | old_growth_spruce_taiga, taiga | — | coniferous_forest, redwood_forest |
| `mixed_forest` | Humid continental (eastern Europe, New England), forest | forest, birch_forest, taiga | forest, birch_taiga, snowy_maple_forest | maple_woods, seasonal_forest |
| `boreal_forest` | Subarctic (Canada, Siberia), forest | taiga, taiga, old_growth_pine_taiga | siberian_taiga, shield, siberian_grove | coniferous_forest, muskeg |
| `snowy_boreal_forest` | Subarctic with very cold winters (Yakutia), forest | snowy_taiga | wintry_forest, snowy_shield | snowy_coniferous_forest |
| `mountain_forest` | Forest just under the tree line | taiga, grove | alpine_grove, forested_highlands | coniferous_forest, fir_clearing |
| `grassland` | Temperate grass | plains, plains, sunflower_plains | plains, valley_clearing, blooming_valley | prairie, field, forested_field |
| `cold_grassland` | Grass in subarctic climates | plains | steppe | field, moor |
| `temperate_shrubland` | Temperate shrubs | plains | shrubland | shrubland |
| `farmland` | Crops | plains, plains, sunflower_plains | — | pasture, field, orchard |
| `town` | Built-up land | plains | — | — |
| `tundra` | Polar tundra, moss and lichen, ground above the tree line in the cold | snowy_plains | wintry_lowlands, snowy_shield | tundra |
| `ice_sheet` | Snow and ice, ice-cap climate | snowy_plains, snowy_plains, snowy_plains, ice_spikes | — | — |
| `alpine_meadow` | Grass and shrubs above the tree line | meadow | alpine_highlands | highland |
| `rocky_peaks` | Bare ground above the tree line | stony_peaks | rocky_mountains | stony_peaks, crag |
| `snowy_peaks` | Above the snow line | frozen_peaks, jagged_peaks | jagged_peaks, frozen_cliffs | — |
| `barren_rock` | Bare ground in other climates | windswept_gravelly_hills | rocky_shrubland | — |
| `tropical_wetland` | Tropical wetland | swamp | orchid_swamp | floodplain |
| `subtropical_wetland` | Humid subtropical wetland (Louisiana) | swamp | — | bayou |
| `marsh` | Temperate wetland | swamp | — | marsh, wetland |
| `bog` | Cold wetland | swamp | ice_marsh | bog, muskeg |
| `mangroves` | Mangroves | mangrove_swamp | — | — |

The ocean kinds go by latitude: warm within the tropics, lukewarm to 40°, then ordinary, cold
from 55° and frozen from 66°. "Deep" is more than 1,000 m down. Rivers and lakes are `river`, or
`warm_river` in the tropics and `frozen_river` above 60°.

## Your own biomes

Any biome from any mod or datapack can be used. In `config/earth-mod/earth_worldgen.json`:

```json
"biomes": {
  "custom": {
    "boreal_forest": ["minecraft:taiga", "minecraft:taiga", "mymod:pine_forest"],
    "town": ["minecraft:plains", "minecraft:sunflower_plains"]
  }
}
```

A kind listed in `custom` uses only those biomes. A biome that isn't installed is left out, with a
warning in the log. The start-up log says how many biomes are in use and how many came from other
mods.

## Changing biomes in a world that already exists

Biomes are saved with each chunk, so land that already exists keeps its biomes. New land gets the
new ones, which can leave a visible edge where they differ. For a world explored with version 0.1,
set `"style": "classic"` to keep choosing them the old way.
