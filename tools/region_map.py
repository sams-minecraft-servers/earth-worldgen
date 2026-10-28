#!/usr/bin/env python3
"""Draws what the test server generated: reads region files and writes a picture of the surface.

    tools/region_map.py <world>/dimensions/minecraft/overworld/region out.png [--min-y -2032] [--blocks]

Each pixel is one block column, coloured by its biome and shaded by height, so the result can be
compared with a real map. With --blocks a column whose top block is a road, wall or roof is that
block's colour instead, so streets and buildings show. Also prints the range of heights, the
biomes found and the size on disk.
Needs nothing beyond Python itself.
"""
import os, struct, sys, zlib

def read_nbt(buf):
    pos = 0
    def u(fmt, n):
        nonlocal pos
        v = struct.unpack_from(fmt, buf, pos)[0]; pos += n; return v
    def string():
        nonlocal pos
        n = u('>H', 2); s = buf[pos:pos + n].decode('utf-8', 'replace'); pos += n; return s
    def payload(t):
        nonlocal pos
        if t == 1: return u('>b', 1)
        if t == 2: return u('>h', 2)
        if t == 3: return u('>i', 4)
        if t == 4: return u('>q', 8)
        if t == 5: return u('>f', 4)
        if t == 6: return u('>d', 8)
        if t == 7: n = u('>i', 4); v = buf[pos:pos + n]; pos += n; return v
        if t == 8: return string()
        if t == 9:
            it = u('>b', 1); n = u('>i', 4); return [payload(it) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                it = u('>b', 1)
                if it == 0: return out
                name = string(); out[name] = payload(it)
        if t == 11: n = u('>i', 4); v = struct.unpack_from('>%di' % n, buf, pos); pos += 4 * n; return v
        if t == 12: n = u('>i', 4); v = struct.unpack_from('>%dq' % n, buf, pos); pos += 8 * n; return v
        raise ValueError('tag %d' % t)
    t = u('>b', 1); string(); return payload(t)

def chunks(region_dir):
    for name in sorted(os.listdir(region_dir)):
        if not name.endswith('.mca'): continue
        with open(os.path.join(region_dir, name), 'rb') as f: data = f.read()
        if len(data) < 8192: continue
        for i in range(1024):
            off = struct.unpack_from('>I', data, i * 4)[0]
            if off == 0: continue
            start = (off >> 8) * 4096
            length, kind = struct.unpack_from('>IB', data, start)
            raw = data[start + 5:start + 4 + length]
            yield read_nbt(zlib.decompress(raw) if kind == 2 else raw), length + 4

def unpack(longs, bits, count):
    per, mask, out = 64 // bits, (1 << bits) - 1, []
    for value in longs:
        value &= (1 << 64) - 1
        for _ in range(per):
            out.append(value & mask); value >>= bits
            if len(out) == count: return out
    return out

COLOURS = {'ocean': (40, 80, 170), 'deep_ocean': (25, 50, 130), 'river': (60, 110, 200), 'beach': (225, 215, 150), 'plains': (140, 185, 90),
           'forest': (60, 130, 60), 'taiga': (50, 100, 80), 'meadow': (150, 200, 130), 'stony_peaks': (150, 150, 150), 'frozen_peaks': (240, 245, 250),
           'mangrove_swamp': (70, 120, 70), 'windswept_gravelly_hills': (135, 130, 120), 'jungle': (40, 150, 50), 'desert': (220, 200, 120), 'savanna': (190, 180, 90), 'jungle': (40, 150, 50), 'swamp': (90, 110, 80), 'snowy_plains': (235, 240, 240)}

# The top blocks that --blocks draws in their own colour: what UrbanBuilder makes roads, walls and roofs of.
BLOCK_COLOURS = {'gray_concrete': (55, 58, 62), 'light_gray_concrete': (125, 125, 115), 'stone_bricks': (112, 112, 112), 'dirt_path': (148, 121, 65),
                 'coarse_dirt': (119, 85, 59), 'cobbled_deepslate': (60, 60, 66), 'oak_planks': (162, 130, 78), 'white_terracotta': (210, 178, 161),
                 'yellow_terracotta': (186, 133, 35), 'orange_terracotta': (161, 83, 37), 'pink_terracotta': (161, 78, 78), 'smooth_sandstone': (223, 214, 170),
                 'terracotta': (152, 94, 67), 'light_gray_terracotta': (135, 107, 98), 'white_concrete': (207, 213, 214), 'smooth_stone': (165, 165, 165),
                 'andesite': (136, 136, 136), 'polished_andesite': (132, 135, 134), 'iron_block': (220, 220, 220), 'bricks': (150, 97, 83),
                 'quartz_block': (236, 230, 223), 'spruce_planks': (115, 85, 49), 'cobblestone': (128, 127, 128), 'brick_slab': (170, 90, 70),
                 'granite_slab': (165, 110, 90), 'stone_brick_slab': (118, 118, 124), 'deepslate_tile_slab': (54, 54, 58)}

def png(path, width, height, rows):
    def chunk(tag, body): return struct.pack('>I', len(body)) + tag + body + struct.pack('>I', zlib.crc32(tag + body) & 0xffffffff)
    raw = b''.join(b'\x00' + row for row in rows)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b''))

# This tool once took all the memory of the machine it runs on; it now cannot have more than 2 GB.
import resource
resource.setrlimit(resource.RLIMIT_AS, (2 << 30, 2 << 30))
MAX_PIXELS = 16_000_000

def largest_group(cols):
    """The columns of the biggest connected patch of generated land, by 512-block cell.

    The test world also has chunks around its spawn point at 0,0, hundreds of thousands of blocks
    from the area under test. One picture spanning both would be billions of pixels: building it
    once used all of the machine's memory and froze every server (2026-10-12)."""
    cells = {}
    for (x, z) in cols: cells.setdefault((x >> 9, z >> 9), []).append((x, z))
    best, seen = [], set()
    for start in cells:
        if start in seen: continue
        group, todo = [], [start]; seen.add(start)
        while todo:
            cx, cz = todo.pop(); group.append((cx, cz))
            for n in ((cx + 1, cz), (cx - 1, cz), (cx, cz + 1), (cx, cz - 1)):
                if n in cells and n not in seen: seen.add(n); todo.append(n)
        if sum(len(cells[c]) for c in group) > sum(len(cells[c]) for c in best): best = group
    return {k: cols[k] for c in best for k in cells[c]}

def main():
    region_dir, out = sys.argv[1], sys.argv[2]
    min_y = int(sys.argv[sys.argv.index('--min-y') + 1]) if '--min-y' in sys.argv else -2032
    by_block = '--blocks' in sys.argv
    cols, stored, statuses, biomes, tops = {}, 0, {}, {}, {}
    for c, size in chunks(region_dir):
        statuses[c.get('Status')] = statuses.get(c.get('Status'), 0) + 1
        hm = (c.get('Heightmaps') or {}).get('WORLD_SURFACE')
        if not hm or c.get('Status') != 'minecraft:full': continue
        stored += size
        heights = unpack(hm, 12, 256)
        # the biome of the section the surface is in, sampled per 4x4 column
        sections = {s['Y']: s for s in c['sections']}
        cx, cz = c['xPos'], c['zPos']
        unpacked = {}
        for i, h in enumerate(heights):
            x, z, y = i % 16, i // 16, h + min_y - 1
            sec = sections.get(y >> 4) or {}
            if by_block:
                states = sec.get('block_states') or {}
                names = [(p if isinstance(p, str) else p.get('Name') or p.get('', '?')).split(':')[-1] for p in states.get('palette') or []]
                if len(names) > 1:
                    if y >> 4 not in unpacked: unpacked[y >> 4] = unpack(states['data'], max(4, (len(names) - 1).bit_length()), 4096)
                    top = names[unpacked[y >> 4][(y & 15) * 256 + z * 16 + x]]
                else: top = names[0] if names else '?'
                tops[top] = tops.get(top, 0) + 1
                if top in BLOCK_COLOURS: cols[(cx * 16 + x, cz * 16 + z)] = (y, top); continue
            b = sec.get('biomes') or {}
            pal = b.get('palette') or ['?']
            if len(pal) == 1: name = pal[0]
            else:
                bits = max(1, (len(pal) - 1).bit_length())
                idx = unpack(b['data'], bits, 64)
                name = pal[idx[((y & 15) >> 2) * 16 + (z >> 2) * 4 + (x >> 2)]]
            name = name.split(':')[-1]; biomes[name] = biomes.get(name, 0) + 1
            cols[(cx * 16 + x, cz * 16 + z)] = (y, name)
    print('chunk statuses:', statuses)
    if not cols: return print('no finished chunks')
    xs = [k[0] for k in cols]; zs = [k[1] for k in cols]
    if (max(xs) - min(xs) + 1) * (max(zs) - min(zs) + 1) > MAX_PIXELS:
        total = len(cols); cols = largest_group(cols)
        print('the generated chunks are far apart: drawing only the largest patch (%d of %d columns)' % (len(cols), total))
        xs = [k[0] for k in cols]; zs = [k[1] for k in cols]
    ys = [v[0] for v in cols.values()]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    if (x1 - x0 + 1) * (z1 - z0 + 1) > MAX_PIXELS:
        return print('not drawing: the picture would be %d x %d pixels (limit %d in all)' % (x1 - x0 + 1, z1 - z0 + 1, MAX_PIXELS))
    print('blocks x %d..%d  z %d..%d   surface y %d..%d' % (x0, x1, z0, z1, min(ys), max(ys)))
    print('biomes:', dict(sorted(biomes.items(), key=lambda kv: -kv[1])))
    if by_block: print('top blocks:', dict(sorted(tops.items(), key=lambda kv: -kv[1])[:40]))
    n = sum(v for k, v in statuses.items() if k == 'minecraft:full')
    print('finished chunks: %d, %.1f KB each as stored' % (n, stored / n / 1024))
    lo, hi = min(ys), max(ys)
    rows = []
    for z in range(z0, z1 + 1):
        row = []
        for x in range(x0, x1 + 1):
            v = cols.get((x, z))
            if not v: row += (0, 0, 0); continue
            y, name = v
            base = BLOCK_COLOURS.get(name) or COLOURS.get(name, (200, 60, 200))
            west = cols.get((x - 1, z - 1), v)[0]
            shade = max(0.55, min(1.35, 1.0 + (y - west) * 0.12)) * (0.8 + 0.2 * (y - lo) / max(1, hi - lo))
            row += tuple(max(0, min(255, int(ch * shade))) for ch in base)
        rows.append(bytes(row))
    png(out, x1 - x0 + 1, z1 - z0 + 1, rows)
    print('wrote', out, '%dx%d' % (x1 - x0 + 1, z1 - z0 + 1))

if __name__ == '__main__':
    main()
