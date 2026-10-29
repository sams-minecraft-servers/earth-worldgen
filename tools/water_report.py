#!/usr/bin/env python3
"""How flat the water is in a test world: the height of the water's surface in each column, and
how often two side-by-side columns of water stand at different heights (steps a boat can't climb).

    tools/water_report.py <world>/dimensions/minecraft/overworld/region [--min-y -2032] [--sea 160] [--box x1,z1,x2,z2] [--json]

Prints how many columns of water there are, the surface heights found (with the most common
first), the share of side-by-side water columns with a step between them and the sizes of the
steps, and where the biggest steps are. Needs nothing beyond Python itself.
"""
import collections, json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from region_map import chunks, unpack, block_name

WATERY = ('minecraft:water', 'minecraft:ice', 'minecraft:seagrass', 'minecraft:tall_seagrass', 'minecraft:kelp', 'minecraft:kelp_plant', 'minecraft:bubble_column')

def main():
    region = sys.argv[1]
    min_y = int(sys.argv[sys.argv.index('--min-y') + 1]) if '--min-y' in sys.argv else -2032
    sea = int(sys.argv[sys.argv.index('--sea') + 1]) if '--sea' in sys.argv else 160
    box = [int(v) for v in sys.argv[sys.argv.index('--box') + 1].split(',')] if '--box' in sys.argv else None
    levels = {}  # (x, z) -> water surface Y
    for c in (c for c, _ in chunks(region)):
        if c.get('Status') != 'minecraft:full': continue
        hm = c.get('Heightmaps', {}).get('WORLD_SURFACE')
        if not hm: continue
        tops = unpack(hm, 12, 256)
        sections = {s['Y']: s for s in c['sections']}
        decoded = {}
        def block(x, y, z):
            sy = y >> 4
            if sy not in decoded:
                bs = (sections.get(sy) or {}).get('block_states') or {}
                pal = [block_name(p) for p in bs.get('palette', ['minecraft:air'])]
                decoded[sy] = (pal, unpack(bs['data'], max(4, (len(pal) - 1).bit_length()), 4096) if 'data' in bs else None)
            pal, data = decoded[sy]
            return pal[data[((y & 15) * 16 + z) * 16 + x]] if data else pal[0]
        for i in range(256):
            x, z = i & 15, i >> 4
            y = min_y + tops[i] - 1
            if box and not (box[0] <= c['xPos'] * 16 + x <= box[2] and box[1] <= c['zPos'] * 16 + z <= box[3]): continue
            if block(x, y, z) in WATERY:
                levels[(c['xPos'] * 16 + x, c['zPos'] * 16 + z)] = y
    pairs, steps, worst = 0, collections.Counter(), []
    for (x, z), y in levels.items():
        for nx, nz in ((x + 1, z), (x, z + 1)):
            n = levels.get((nx, nz))
            if n is None: continue
            pairs += 1
            if n != y:
                steps[abs(n - y)] += 1
                worst.append((abs(n - y), x, z, y, n))
    worst.sort(reverse=True)
    stepped = sum(steps.values())
    report = {'water_columns': len(levels), 'at_sea_level': sum(1 for y in levels.values() if y == sea - 1),
              'surface_heights': dict(collections.Counter(levels.values()).most_common(12)),
              'side_by_side_pairs': pairs, 'pairs_with_a_step': stepped,
              'step_share_percent': round(100.0 * stepped / pairs, 2) if pairs else 0,
              'step_sizes': dict(sorted(steps.items())), 'biggest_steps': [dict(zip(('size', 'x', 'z', 'y', 'next_y'), w)) for w in worst[:8]]}
    print(json.dumps(report, indent=None if '--json' in sys.argv else 1))

if __name__ == '__main__':
    main()
