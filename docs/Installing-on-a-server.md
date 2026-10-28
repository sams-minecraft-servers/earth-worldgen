# Installing on a server

These steps make a **new** world. The mod cannot turn an existing world into the Earth: the datapack
has to be there before the world's first start, and chunks that already exist are never remade.

## What you need

- A Fabric server for Minecraft **26.3** with Fabric Loader **0.19.0 or newer**
  (https://fabricmc.net/use/server/). Fabric API is not needed.
- Java **25**.
- Memory: earth.tj runs with an 8 GB heap. The elevation and land-cover files are read straight
  from disk (memory-mapped), so free memory beyond the heap also helps.
- Disk: about 250 GB for the whole planet's packed data, plus room for the world itself. A single
  region needs far less (see [Getting the data](Getting-the-data.md)). An SSD makes exploring
  noticeably smoother than spinning disks.
- Python 3 and `curl` for the download scripts.

## Steps

### 1. Install the mod

Build the jar (see [Building from source](Building-from-source.md)) or take one from the
repository's releases, and put it in the server's `mods/` folder.

### 2. Add the datapack before the first start

The datapack makes the overworld 4,064 blocks tall (Y −2032 to 2031, the most the game allows) and
points its generator at the mod. It also switches off the game's own villages and spreads
strongholds across the whole map.

If `level-name` in `server.properties` is `world`:

```
mkdir -p world/datapacks
cp -r datapack world/datapacks/earth_worldgen
```

Do this **before** the server is started for the first time. If the world was already created
without it, delete the world folder and start again.

### 3. Write the config

The mod writes `config/earth_worldgen.json`, with every setting and its default, the first time the
server starts. To set it up before the world exists, copy `config.example.json` there instead.

Edit the three folder paths so they point at your data. Relative paths are read from the server's
folder. Decide now whether you want cities, monuments and the game's own structures (all explained
in [Configuration](Configuration.md)): **settings only affect land generated after they change.**

### 4. Get the data

Follow [Getting the data](Getting-the-data.md). At minimum you need packed elevation tiles for the
area players will visit. Anywhere a tile is missing is generated as open sea.

### 5. Start the server and set a spawn

Start the server once. Block 0, 0 is where the equator meets the prime meridian, in open sea in
the Gulf of Guinea, so pick a place on land and set the spawn there. For example, Nairobi:

```
/setworldspawn 819694 1700 28640
```

Players land on the ground when they first join, so the Y value doesn't need to be exact. Use
[Finding places](Finding-places.md) to work out coordinates for anywhere else.

## Things to know before opening to players

- **Fresh land is generated on demand.** Teleporting far away, or force-loading a large area,
  makes the server generate many new chunks at once and can stall it for tens of seconds. Avoid
  `/forceload` over big unexplored areas while players are on.
- **Credit the data** somewhere players can see it (see [ATTRIBUTION.md](../ATTRIBUTION.md)).
- **Back up** the world before changing the config or updating the mod.
