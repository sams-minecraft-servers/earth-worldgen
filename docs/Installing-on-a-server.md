# Installing on a server

These steps make a **new** world. The mod cannot turn an existing world into the Earth: the datapack
has to be there before the world's first start, and chunks that already exist are never remade.

## What you need

- A server for Minecraft **26.3**: **Fabric** (Loader 0.19.0 or newer, https://fabricmc.net/use/server/;
  Fabric API is not needed) or **NeoForge** (26.3, https://neoforged.net). Use the jar for your loader.
- Java **25**.
- Memory: earth.tj runs with an 8 GB heap. The elevation and land-cover files are read straight
  from disk (memory-mapped), so free memory beyond the heap also helps.
- Disk: about 250 GB for the whole planet's packed data, plus room for the world itself. A single
  region needs far less (see [Getting the data](Getting-the-data.md)). An SSD makes exploring
  noticeably smoother than spinning disks.
- Python 3 and `curl` for the download scripts.

## The short way

Drop the jar into `mods/` of a Fabric server whose world does not exist yet, and start it. The mod
installs its datapack into the new world, puts the spawn on land and downloads map data as players
explore. The rest of this page is for setting things up by hand, or in advance.

## Steps

### 1. Install the mod

Build the jar (see [Building from source](Building-from-source.md)) or take one from the
repository's releases, and put it in the server's `mods/` folder.

### 2. Add the datapack before the first start

The mod does this by itself when the world does not exist yet (`installDatapack`). By hand:

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

The mod writes `config/earth-mod/earth_worldgen.json`, with every setting and its default, the first time the
server starts. To set it up before the world exists, copy `config.example.json` there instead.

Edit the three folder paths so they point at your data. Relative paths are read from the server's
folder. Decide now whether you want cities, monuments and the game's own structures (all explained
in [Configuration](Configuration.md)): **settings only affect land generated after they change.**

### 4. Get the data

By default the mod downloads each area's data the first time a player goes there. To download it in
advance instead, follow [Getting the data](Getting-the-data.md).

### 5. Start the server and set a spawn

A new world's spawn is put at `newWorldSpawn` (Nairobi by default). To move it later, use
`/setworldspawn`, for example:

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
