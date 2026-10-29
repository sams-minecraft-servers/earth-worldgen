# Changelog

## 1.0.0 (not released yet)

The first full release, for Minecraft 26.3 on Fabric and NeoForge.

### The world
- The real Earth at 1:5 (5 m per block, both across and up), made from real elevation data as
  players explore. The world is 4,064 blocks tall (Y −2032 to 2031), so Everest and the Mariana
  Trench both fit. The sea is at Y 160.
- Real land cover: forests, cropland, grassland, bare rock, snow and ice, wetlands and built-up land
  where the maps put them.
- Biomes from the real climate (a Köppen-Geiger map), land cover and height: 50 kinds of place, from
  boreal forest and steppe to Mediterranean scrub and tropical rainforest. Terralith and Biomes O'
  Plenty biomes are used where they fit when those mods are installed. A `classic` style and custom
  biome lists are in the config.
- A level sea, lakes and rivers that are flat at their real heights, and dry land below sea level
  (the Dead Sea shore, the Netherlands).
- Ores, caves, ore veins, geodes, mineshafts, strongholds, trial chambers and ancient cities at their
  usual depths below the real ground. Caves never break into the sea, lakes or rivers. Ocean
  monuments stand on the real sea bed. Strongholds are spread over the whole planet.
- Animals where they really live: pandas in Sichuan, polar bears in the Arctic, llamas in the Andes,
  camels in deserts, and more.
- The game's random villages are off; villagers live on real built-up land instead.
- About 250 monuments built where they really stand, from the Eiffel Tower to the Titanic on the sea
  bed. Each can be switched off.
- Optional real streets and buildings from Overture Maps, for areas you pack in advance.
- Optional country radio: one station per country, heard inside its borders and fading into static
  beyond them. You supply the audio.

### Playing it
- Singleplayer: choose **World Type: Earth** on the Create World screen. Other worlds are left alone.
- Servers: drop the jar into `mods/` and start; the mod sets up a new world as the Earth by itself.
  Or use `level-type=earth_worldgen:earth`. Players join with an unmodified game.
- New worlds start in Nairobi (`newWorldSpawn` in the config).
- Map data is downloaded per area the first time it is needed (or in advance with the tools in
  `tools/`).
- Every feature can be switched in `config/earth-mod/earth_worldgen.json`, everywhere or country by
  country.
- No Fabric API needed on Fabric.

### Tested with
Terralith, Biomes O' Plenty, Lithostitched, Waystones, Chunky, Lithium, C2ME, Distant Horizons and
several ore mods. See [Compatibility](docs/Compatibility.md).
