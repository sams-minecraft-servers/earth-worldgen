# How it works

The mod leaves the game's world generation running as normal and replaces only the parts that
assume a flat, random world around Y 64. Everything happens when a chunk is first made; nothing is
pre-generated, and data is read only for the places being explored.

## The ground

- **`ElevationFunction`** (`earth_worldgen:surface`) is a density function the datapack's noise
  settings use: for each block column it gives the Y of the real ground. The game turns that into
  solid below and open above, so everything it adds later (caves, ores, plants, structures) works as
  in any world.
- **`Elevation`** reads the packed elevation tiles. A tile is memory-mapped the first time a chunk
  needs it, and only the 256 × 256 squares under the area being explored are unpacked.
- **`Terrain`** combines height and land cover into one answer per column: where the surface is,
  whether it is water, and what kind of place it is.
- **`EarthFill`** turns the noise settings into blocks faster than the game's own step. It only works
  out the layers the surface and caves pass through, and stores the solid rock beneath in one go.
  This matters in a world 4,064 blocks tall.
- **`GroundRule`** (`earth_worldgen:ground`) decides what the ground is made of by depth below the
  real surface: soil, sand or snow on top, then stone, then deep rock.

## Water

**`EarthFluids`** replaces the game's one-sea-level rule: the sea is at sea level, every lake and
river stands at its own real height, land below sea level stays dry, and ocean deeps are water
all the way down. Under any water the ground is dry: **`EarthCarvingMask`** stops caves and ravines
cutting into or beside a sea, lake or river bed, so caves near coasts don't flood.

## Biomes

**`EarthBiomeSource`** (`earth_worldgen:earth`) sorts each place into a kind of real place
("boreal_forest", "cold_steppe", "mediterranean_shrubland"...) from three maps: its land cover
(**`LandCover`**, ESA WorldCover at 10 m), its climate (**`RealClimate`**, the Köppen-Geiger map at
0.05°, packed into the jar by `tools/PackClimate.java`) and its height against the tree and snow
lines. `earth_worldgen/biomes.json` lists each kind's biomes: the game's own, Terralith's and Biomes
O' Plenty's. Those that are installed are used, and a kind with several biomes gives each one patches
around scattered points, so the edges are uneven. The borders between climates are moved about a
little so they don't follow the grid. See [Biomes](Biomes.md).

## Underground

The game places ores, caves, geodes, dungeons and structures at fixed heights ("diamonds below Y 16")
that assume ground near Y 64. Here the ground runs from Y −2027 to Y 1930, so **`Underground`** works
out each height as it would be in an ordinary world and moves it by how far the real ground at that
place is from Y 64. The mixins apply this to:

- ore and feature placement (`HeightRangePlacementMixin`)
- noise caves and ore veins (`BelowGround`, `earth_worldgen:below_ground`)
- cave and ravine carvers (`CarverMixin`)
- trial chambers and ancient cities (`JigsawStructureMixin`)
- mineshafts and strongholds (`StructurePiecesBuilderMixin`)
- buried ruined portals (`RuinedPortalStructureMixin`)
- ocean monuments, which stand on the real sea bed (`OceanMonumentMixin`)

The datapack spreads strongholds across the whole map, about one every 8,000 blocks, instead of
ringing block 0, 0, which is open sea. `ChunkGeneratorStructureStateMixin` takes the structure sets
switched off in the config (villages, by default) out of the Earth world's list, so they are neither
made nor found by eyes of ender or `/locate`.

## Monuments

**`Monuments`** builds about 250 famous buildings and wrecks where they really stand. Each chunk
builds its own part of any monument that reaches into it, as it is first made, so a monument comes
out whole whichever chunk is generated first. A monument stands on a stone footing on the first free
block above the ground or water at its centre, with the trees and buildings in its way cleared;
wrecks lie on the sea bed. **`Countries`** (Natural Earth borders, shipped with the mod) answers which
country a place is in, for the settings that switch cities and monuments off country by country.

## Life

- **`Fauna`** adds animals where their real range is and, for most of them, removes them
  everywhere else: pandas in Sichuan's forests, polar bears in the Arctic and Hudson Bay, llamas
  high in the Andes, parrots and ocelots in tropical forests, salmon in northern waters, tropical
  fish near the equator. Axolotls live only in Mexico City's lakes and canals. Animals it doesn't
  list (cows, sheep, pigs…) behave as in any world.
- **`CityVillagers`** puts villagers on built-up land as it is made (`villagersPerCityChunk`).

## Streets and buildings (optional)

Each part can be switched off in the config, everywhere or country by country.
**`Urban`** reads the packed streets files, one per one-degree tile, sorted into small squares so
only the squares under new chunks are read. **`UrbanBuilder`** builds them into each chunk after its
caves are cut and before its trees go in, so trees keep off roads and roofs. Every building is a block
of its real outline and height, roads cut through, and bridge decks run level over what is beneath
them. Each column is decided on its own, so a building that crosses a chunk border comes out the same
whichever chunk is made first.

## What it does not change

Other dimensions (the Nether, the End) are untouched. Players need no mod: everything runs on the
server, and the client receives ordinary chunks.
