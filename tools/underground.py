#!/usr/bin/env python3
"""Reports what the test server put under the ground: ores, caves, water and buried structures,
each by how far below the surface it lies.

    tools/underground.py <world>/dimensions/minecraft/overworld/region

The ground of a column is taken as its highest block of soil, sand or rock. Needs nothing beyond
Python itself; reads one chunk at a time and (through region_map) cannot have more than 2 GB.
"""
import sys
from region_map import chunks, unpack

GROUND = {'stone', 'deepslate', 'dirt', 'grass_block', 'sand', 'gravel', 'sandstone', 'clay', 'coarse_dirt', 'podzol', 'mud',
          'snow_block', 'granite', 'diorite', 'andesite', 'tuff', 'calcite', 'red_sand', 'packed_ice', 'terracotta', 'rooted_dirt'}
REACH = 176   # how far below the ground a chunk is looked at

def block(entry):
    """The name of a palette entry: a plain name, or a compound with an id and properties (a name may come wrapped under an empty key)."""
    while isinstance(entry, dict): entry = entry['id'] if 'id' in entry else entry['']
    return entry.split(':')[-1]

def main():
    ores, caves, water, lava, starts, done, leaks = {}, {}, {}, 0, [], 0, 0
    for c, _ in chunks(sys.argv[1]):
        if c.get('Status') != 'minecraft:full': continue
        done += 1
        for name, start in ((c.get('structures') or {}).get('starts') or {}).items():
            boxes = [p['BB'] for p in start.get('Children', []) if 'BB' in p]
            if boxes: starts.append((name, c['xPos'] * 16 + 8, c['zPos'] * 16 + 8, min(b[1] for b in boxes), max(b[4] for b in boxes)))
        # the blocks of every section that is not all one thing, highest first
        columns = {}
        for s in sorted(c['sections'], key=lambda s: -s['Y']):
            states = s.get('block_states') or {}
            palette = [block(p) for p in states.get('palette', [])]
            if len(palette) < 2: continue
            cells = unpack(states['data'], max(4, (len(palette) - 1).bit_length()), 4096)
            for i, cell in enumerate(cells):
                name = palette[cell]
                if name == 'air' and (i & 255) not in columns: continue   # open sky
                columns.setdefault(i & 255, []).append((s['Y'] * 16 + (i >> 8), name))
        for cells in columns.values():
            cells.sort(reverse=True)
            ground = next((y for y, name in cells if name in GROUND), None)
            if ground is None: continue
            under_water = any(name == 'water' and y > ground for y, name in cells)
            for y, name in cells:
                depth = ground - y
                if depth <= 0 or depth > REACH: continue
                if name.endswith('_ore'): ores.setdefault(name, []).append(depth)
                elif name in ('air', 'cave_air'):
                    caves[depth // 16] = caves.get(depth // 16, 0) + 1
                    if under_water and depth < 8: leaks += 1
                elif name == 'water': water[depth // 16] = water.get(depth // 16, 0) + 1
                elif name == 'lava': lava += 1
    print('finished chunks looked at: %d' % done)
    print('ores, by blocks below the ground (count, shallowest, middle, deepest):')
    for name, depths in sorted(ores.items()):
        depths.sort(); print('  %-28s %6d  %4d %4d %4d' % (name, len(depths), depths[0], depths[len(depths) // 2], depths[-1]))
    band = lambda d: ', '.join('%d-%d: %d' % (k * 16, k * 16 + 15, v) for k, v in sorted(d.items()))
    print('open cave blocks below the ground, by depth:', band(caves) or 'none')
    print('open cave blocks in the 7 blocks under a sea, lake or river bed (should be none): %d' % leaks)
    print('water blocks below the ground, by depth:', band(water) or 'none')
    print('lava blocks below the ground: %d' % lava)
    print('structures (name, x, z, lowest y, highest y):')
    for s in sorted(starts): print('  %-32s %8d %8d %6d %6d' % s)

if __name__ == '__main__':
    main()
