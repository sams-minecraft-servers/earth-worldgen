#!/usr/bin/env python3
"""Counts what a test world's chunks are made of: biomes, and ore blocks by name.

    tools/chunk_report.py <world>/dimensions/minecraft/overworld/region [--match ore,raw_] [--box x1,z1,x2,z2] [--json]

Biomes are counted in the game's 4 x 4 x 4 cells, from the surface section of each column down, so
the numbers are shares of the land. Blocks are counted wherever a section holds one whose name has
any of the --match words in it (default: "_ore"). With --box, only the chunks inside those block
coordinates are counted. Prints the totals; with --json, as JSON.
Needs nothing beyond Python itself.
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from region_map import chunks, unpack, block_name

def main():
    region = sys.argv[1]
    words = sys.argv[sys.argv.index('--match') + 1].split(',') if '--match' in sys.argv else ['_ore']
    box = [int(v) for v in sys.argv[sys.argv.index('--box') + 1].split(',')] if '--box' in sys.argv else None
    biomes, blocks, done = {}, {}, 0
    for chunk, _ in chunks(region):
        if not str(chunk.get('Status', '')).endswith('full'):
            continue
        if box and not (box[0] <= chunk.get('xPos', 0) * 16 + 8 <= box[2] and box[1] <= chunk.get('zPos', 0) * 16 + 8 <= box[3]):
            continue
        done += 1
        for section in chunk.get('sections', []):
            b = section.get('biomes')
            if b:
                palette = b.get('palette', [])
                if len(palette) == 1: biomes[palette[0]] = biomes.get(palette[0], 0) + 64
                elif palette:
                    bits = max(1, (len(palette) - 1).bit_length())
                    for i in unpack(b.get('data', []), bits, 64): biomes[palette[i]] = biomes.get(palette[i], 0) + 1
            states = section.get('block_states')
            if not states: continue
            palette = [block_name(p) for p in states.get('palette', [])]
            wanted = [i for i, name in enumerate(palette) if any(w in name for w in words)]
            if not wanted: continue
            if len(palette) == 1:
                blocks[palette[0]] = blocks.get(palette[0], 0) + 4096; continue
            bits = max(4, (len(palette) - 1).bit_length())
            for i in unpack(states.get('data', []), bits, 4096):
                if i in wanted: blocks[palette[i]] = blocks.get(palette[i], 0) + 1
    total = sum(biomes.values()) or 1
    report = {'chunks': done, 'biomes': {k: round(100.0 * v / total, 1) for k, v in sorted(biomes.items(), key=lambda kv: -kv[1])},
              'blocks': dict(sorted(blocks.items(), key=lambda kv: -kv[1]))}
    print(json.dumps(report, indent=None if '--json' in sys.argv else 1))

if __name__ == '__main__':
    main()
