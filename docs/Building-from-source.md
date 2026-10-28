# Building from source

You need Java 25. The Gradle wrapper downloads everything else, including Minecraft itself.

```
./gradlew build
```

This builds one jar per loader: `fabric/build/libs/earth-worldgen-fabric-<version>+<minecraft>.jar`
and `neoforge/build/libs/earth-worldgen-neoforge-<version>+<minecraft>.jar`. The mod's code is shared,
in `common/`; `fabric/` and `neoforge/` only hold each loader's entry point and mod information. A build takes a
few GB of memory. On a machine that also runs servers, run it under a hard memory limit:

```
tools/guard.sh 4G ./gradlew build --no-daemon
```

Every push to `main` is also built on GitHub. The latest jar is attached to the repository's
**latest** release, and tagged versions (`v0.2.0`, …) get a release of their own.

## The test server

`tools/test-server.sh` runs a throwaway Fabric server in `run/` on 127.0.0.1:25599, feeds it console
commands from a file, then stops it:

```
tools/test-server.sh commands.txt --fresh     # --fresh deletes the test world first
```

In the commands file, a line `wait 20` pauses for 20 seconds. `run/` needs `fabric-server.jar`, the
Fabric server launcher for this Minecraft version (https://fabricmc.net/use/server/). The heap is
`TEST_HEAP` (default 4G); the server runs under `tools/guard.sh` and is stopped after
`TEST_TIMEOUT` (default 30m).

## Looking at what was generated

```
tools/region_map.py run/world/dimensions/minecraft/overworld/region out.png          # a top-down map
tools/region_map.py run/world/dimensions/minecraft/overworld/region out.png --blocks # with streets and buildings
tools/underground.py run/world/dimensions/minecraft/overworld/region                 # ores, caves and structures by depth
```

`region_map.py` refuses to draw a picture over 16 million pixels, so a world explored in far-apart
places doesn't use up the machine's memory.

## Layout

| Path | What it is |
|---|---|
| `common/src/main/java/tj/earth/worldgen/` | The mod, shared by every loader (see [How it works](How-it-works.md)) |
| `common/src/main/java/tj/earth/worldgen/mixin/` | Where it hooks into the game's world generation |
| `fabric/`, `neoforge/` | Each loader's entry point and mod information |
| `datapack/` | Dimension, noise settings and structure changes that go in the world |
| `tools/` | Data download and packing, test server, inspection tools |
| `config.example.json` | Starting point for `config/earth-mod/earth_worldgen.json` |
