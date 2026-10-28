# FAQ and limitations

**Do players need to install anything?**
No. The mod runs on the server only; players join with the ordinary game.

**Can I add it to an existing world?**
No. The datapack must be in place before the world's first start, and existing chunks are never
remade. Start a new world.

**Does it work in single player?**
It is built and tested as a server mod. A single-player world should work in principle (put the
datapack in the world's folder when creating it, the config in the game folder's `config/`), but
nobody has tried it.

**Why is it so slow to generate new land?**
Every new chunk reads real data and the world is 4,064 blocks tall. Exploring at walking or flying
speed is fine; teleporting thousands of kilometres, or `/forceload` over a big unexplored area, makes
the server generate many chunks at once and can stall it for tens of seconds. Fast disks help a lot.

**Can I pre-generate the world?**
At 1:5 the whole planet is about 32 trillion block columns, far too much. Pre-generating a small area
around spawn with a tool like Chunky works, but do it while the server is empty.

**Why does Greenland look so big?**
The map is a plain latitude/longitude grid, so land near the poles is stretched east to west. See
[Finding places](Finding-places.md).

**My city has no buildings.**
Streets and buildings are optional and only appear where you have packed that tile with
`tools/pack_urban.py`, and only in chunks made after it was packed. Check that `cities.enabled`,
`cities.buildings` and `cities.roads` are on and the country isn't in `cities.disabledCountries`.

**Can I change the scale later?**
Not without seams: existing chunks keep the old scale. Decide before players explore.

## Known limitations

- Used on one server only so far (earth.tj, Minecraft 26.3, Linux). Other setups are untested.
- The scripts in `tools/` assume Linux; `guard.sh` and `test-server.sh` need systemd.
- Elevation data has gaps and artefacts in places (cliffs, steep valleys, some coasts). Water the
  data reads as high, such as ships or cranes, is still sea when the sea is close by.
- At 1:5 buildings are blocks of the right outline and height, with no doors or interiors.
- The Nether and the End are the game's own.

Found something else? [Open an issue](https://github.com/sams-minecraft-servers/earth-worldgen/issues).
