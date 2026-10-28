# Updating Minecraft

World generation internals change in most Minecraft versions, so expect to fix a few things
each time.

1. Change `minecraft_version`, `loader_version` and `loom_version` together in `gradle.properties`,
   and the `minecraft` version in `src/main/resources/fabric.mod.json`.
2. Run `./gradlew build` and fix whatever no longer compiles. `./gradlew genSources` gives readable
   game sources to compare against.
3. Compare `datapack/` with the new version's own `dimension_type/overworld.json` and
   `worldgen/noise_settings/overworld.json` (unzip them from the server jar), and update the pack
   format in `datapack/pack.mcmeta`.
4. Run the test server (see [Building from source](Building-from-source.md)) and look at the result
   with `tools/region_map.py` and `tools/underground.py`.
5. Try it on a **copy** of your world before updating a live server. Chunks that already exist are
   kept as they are, so the place to look for problems is the border between old and new land.
