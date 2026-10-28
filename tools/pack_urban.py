#!/usr/bin/env python3
"""Packs the real streets and building outlines into the form the mod reads (see Urban.java).

    <data>/urban-venv/bin/python tools/pack_urban.py N44E008 [N44E009 ...]

One file per one-degree tile, <data>/urban/N44/N44E008.urb, holding every building outline (with
its height, floors and kind where known) and every road and railway on the surface, sorted into
small squares so the mod only reads the squares under the chunks it is making. <data> is EARTH_DATA
or ~/earth-data.

The outlines come from Overture Maps (OpenStreetMap plus machine-traced buildings; ODbL, so the
website must credit "OpenStreetMap contributors, Overture Maps Foundation"). They are read
straight from Overture's public bucket, only the parts covering the tile; set URBAN_SOURCE to a
folder holding a downloaded copy of the release to read that instead.

Needs the duckdb Python package (python3 -m venv <data>/urban-venv; urban-venv/bin/pip install
duckdb). Run it under tools/guard.sh; it keeps one tile in memory at a time.
"""
import math, os, struct, sys, time, zlib

DATA = os.environ.get('EARTH_DATA', os.path.expanduser('~/earth-data'))
SOURCE = os.environ.get('URBAN_SOURCE', 's3://overturemaps-us-west-2/release/2026-09-23.1').rstrip('/')

CELLS = 128              # squares along one side of a tile
UNIT = 1_000_000         # coordinates are kept in millionths of a degree from the tile's south-west corner
MARGIN_METRES = 40.0     # a road is also put in every square it passes this close to (its own width, and rounding)
MAX_FEATURES_PER_TILE = 20_000_000   # refuse rather than run out of memory

# What the mod knows each number as (Urban.java).
BUILDING_KINDS = {None: 0, 'residential': 1, 'commercial': 2, 'industrial': 3, 'agricultural': 4, 'civic': 5, 'education': 6,
                  'entertainment': 7, 'medical': 8, 'military': 9, 'outbuilding': 10, 'religious': 11, 'service': 12, 'transportation': 13}
ROAD_KINDS = {'motorway': 1, 'trunk': 2, 'primary': 3, 'secondary': 4, 'tertiary': 5, 'residential': 6, 'unclassified': 6,
              'living_street': 6, 'service': 7, 'pedestrian': 8, 'footway': 9, 'path': 9, 'steps': 9, 'cycleway': 9,
              'bridleway': 9, 'track': 10}
RAIL = 11
# Drawn one block wide like any street, these would bury the streets: drives, car park lanes, and
# pavements that are mapped separately from their road.
SKIPPED_SUBCLASSES = {'driveway', 'parking_aisle', 'sidewalk', 'crosswalk'}
SKIPPED_RAIL = {'subway', 'tram'}
BRIDGE = 1

def varint(out, n):
    while n >= 0x80:
        out.append(n & 0x7f | 0x80); n >>= 7
    out.append(n)

def points(out, pts):
    """A run of points, each as the difference from the one before."""
    varint(out, len(pts))
    px = py = 0
    for x, y in pts:
        for d in (x - px, y - py): varint(out, d << 1 if d >= 0 else (-d << 1) - 1)
        px, py = x, y

def wkb(buf):
    """The polygons or lines of a geometry: a list of parts, each a list of rings (or one line) of (lon, lat)."""
    pos = 0
    def read():
        nonlocal pos
        order = '<' if buf[pos] == 1 else '>'
        kind = struct.unpack_from(order + 'I', buf, pos + 1)[0]; pos += 5
        dims = 2 + (kind // 1000 in (1, 2)) + (kind // 1000 == 3) * 2 + bool(kind & 0x80000000) + bool(kind & 0x40000000)
        if kind & 0x20000000: pos += 4
        kind = (kind & 0xffff) % 1000
        def count():
            nonlocal pos
            n = struct.unpack_from(order + 'I', buf, pos)[0]; pos += 4; return n
        def line():
            nonlocal pos
            n = count()
            flat = struct.unpack_from('%s%dd' % (order, n * dims), buf, pos); pos += 8 * n * dims
            return list(zip(flat[0::dims], flat[1::dims]))
        if kind == 2: return [[line()]]
        if kind == 3: return [[line() for _ in range(count())]]
        if kind in (5, 6, 7): return [part for _ in range(count()) for part in read()]
        if kind == 1: pos += 8 * dims
        return []
    return read()

def pieces(line, flags):
    """A road cut where it enters and leaves bridges and tunnels: (points, is a bridge), tunnels left out."""
    spans = {'is_bridge': [], 'is_tunnel': []}
    for flag in flags or []:
        between = flag.get('between') or [0.0, 1.0]
        for value in flag.get('values') or []:
            if value in spans: spans[value].append((between[0] or 0.0, 1.0 if between[1] is None else between[1]))
    if not spans['is_bridge'] and not spans['is_tunnel']: return [(line, False)]
    k = math.cos(math.radians(line[0][1]))
    along = [0.0]
    for (ax, ay), (bx, by) in zip(line, line[1:]): along.append(along[-1] + math.hypot((bx - ax) * k, by - ay))
    if along[-1] == 0: return []
    def at(f):
        d = f * along[-1]
        for i in range(1, len(line)):
            if along[i] >= d:
                t = (d - along[i - 1]) / ((along[i] - along[i - 1]) or 1)
                return (line[i - 1][0] + (line[i][0] - line[i - 1][0]) * t, line[i - 1][1] + (line[i][1] - line[i - 1][1]) * t)
        return line[-1]
    cuts = sorted({0.0, 1.0} | {min(1.0, max(0.0, f)) for span in spans.values() for pair in span for f in pair})
    found = []
    for a, b in zip(cuts, cuts[1:]):
        middle = (a + b) / 2
        if any(lo <= middle <= hi for lo, hi in spans['is_tunnel']): continue
        inside = [line[i] for i in range(len(line)) if a * along[-1] < along[i] < b * along[-1]]
        found.append(([at(a)] + inside + [at(b)], any(lo <= middle <= hi for lo, hi in spans['is_bridge'])))
    return found

def touches(ax, ay, bx, by, x0, y0, x1, y1):
    """Whether the line from a to b passes through the rectangle."""
    t0, t1 = 0.0, 1.0
    for p, q in ((ax - bx, ax - x0), (bx - ax, x1 - ax), (ay - by, ay - y0), (by - ay, y1 - ay)):
        if p == 0:
            if q < 0: return False
        else:
            t = q / p
            if p < 0: t0 = max(t0, t)
            else: t1 = min(t1, t)
            if t0 > t1: return False
    return True

def pack(name, db):
    south = int(name[1:3]) * (1 if name[0] == 'N' else -1)
    west = int(name[4:7]) * (1 if name[3] == 'E' else -1)
    margin_y = MARGIN_METRES / 111_320
    margin_x = margin_y / max(0.05, math.cos(math.radians(abs(south) + 1)))
    box = "bbox.xmin < %r AND bbox.xmax > %r AND bbox.ymin < %r AND bbox.ymax > %r" % (
        west + 1 + margin_x, west - margin_x, south + 1 + margin_y, south - margin_y)
    buildings = [bytearray() for _ in range(CELLS * CELLS)]
    roads = [bytearray() for _ in range(CELLS * CELLS)]
    building_count, road_count = [0] * (CELLS * CELLS), [0] * (CELLS * CELLS)
    seen = {'buildings': 0, 'with a height': 0, 'with floors': 0, 'roads': 0, 'bridges': 0}

    def units(lon, lat):
        return round((lon - west) * UNIT), round((lat - south) * UNIT)

    def cells(x0, y0, x1, y1, mx, my):
        """The squares of this tile that a box in units, widened by a margin, lies over."""
        c0, c1 = max(0, (x0 - mx) * CELLS // UNIT), min(CELLS - 1, (x1 + mx) * CELLS // UNIT)
        r0, r1 = max(0, (y0 - my) * CELLS // UNIT), min(CELLS - 1, (y1 + my) * CELLS // UNIT)
        return [(r, c) for r in range(r0, r1 + 1) for c in range(c0, c1 + 1)]

    mx, my = round(margin_x * UNIT), round(margin_y * UNIT)
    cur = db.execute("SELECT ST_AsWKB(geometry), height, num_floors, subtype, roof_shape FROM read_parquet('%s/theme=buildings/type=building/*')"
                     " WHERE %s AND NOT coalesce(is_underground, false)" % (SOURCE, box))
    while rows := cur.fetchmany(4096):
        for shape, height, floors, kind, roof in rows:
            for rings in wkb(bytes(shape)):
                outline = []
                for ring in rings:
                    ring = [units(*p) for p in ring]
                    ring = [p for i, p in enumerate(ring) if i == 0 or p != ring[i - 1]]
                    if len(ring) > 1 and ring[0] == ring[-1]: ring.pop()
                    if len(ring) >= 3: outline.append(ring)
                    elif not outline: break   # the outer edge is too small to be anything
                if not outline: continue
                record = bytearray([BUILDING_KINDS.get(kind, 0), 0 if roof is None else 1 if roof == 'flat' else 2])
                varint(record, min(65535, max(0, round((height or 0) * 10))))
                varint(record, min(255, max(0, floors or 0)))
                varint(record, len(outline))
                for ring in outline: points(record, ring)
                xs, ys = [p[0] for p in outline[0]], [p[1] for p in outline[0]]
                for r, c in cells(min(xs), min(ys), max(xs), max(ys), mx // 4, my // 4):
                    buildings[r * CELLS + c] += record; building_count[r * CELLS + c] += 1
                seen['buildings'] += 1; seen['with a height'] += bool(height); seen['with floors'] += bool(floors)
        if seen['buildings'] > MAX_FEATURES_PER_TILE: raise SystemExit('%s: more buildings than this tool will hold' % name)

    cur = db.execute("SELECT ST_AsWKB(geometry), subtype, class, subclass, road_flags, rail_flags FROM read_parquet('%s/theme=transportation/type=segment/*')"
                     " WHERE %s AND subtype IN ('road', 'rail')" % (SOURCE, box))
    while rows := cur.fetchmany(4096):
        for shape, subtype, cls, subclass, road_flags, rail_flags in rows:
            if subtype == 'rail':
                if cls in SKIPPED_RAIL: continue
                kind, flags = RAIL, rail_flags
            else:
                if subclass in SKIPPED_SUBCLASSES or cls not in ROAD_KINDS: continue
                kind, flags = ROAD_KINDS[cls], road_flags
            for part in wkb(bytes(shape)):
                if len(part[0]) < 2: continue
                for line, bridge in pieces(part[0], flags):
                    line = [units(*p) for p in line]
                    line = [p for i, p in enumerate(line) if i == 0 or p != line[i - 1]]
                    # A bridge is kept whole (its height runs from one end to the other); the rest in short runs.
                    for start in range(0, len(line) - 1, len(line) if bridge else 8):
                        run = line[start:] if bridge else line[start:start + 9]
                        if len(run) < 2: continue
                        record = bytearray([kind, BRIDGE if bridge else 0])
                        points(record, run)
                        xs, ys = [p[0] for p in run], [p[1] for p in run]
                        for r, c in cells(min(xs), min(ys), max(xs), max(ys), mx, my):
                            x0, y0 = c * UNIT // CELLS - mx, r * UNIT // CELLS - my
                            x1, y1 = (c + 1) * UNIT // CELLS + mx, (r + 1) * UNIT // CELLS + my
                            if any(touches(ax, ay, bx, by, x0, y0, x1, y1) for (ax, ay), (bx, by) in zip(run, run[1:])):
                                roads[r * CELLS + c] += record; road_count[r * CELLS + c] += 1
                        seen['roads'] += 1; seen['bridges'] += bridge
        if seen['roads'] > MAX_FEATURES_PER_TILE: raise SystemExit('%s: more roads than this tool will hold' % name)

    # The file: "EURB", version, squares per side, where each square starts (and the end of the last), then the squares.
    head = 8 + 4 * (CELLS * CELLS + 1)
    offsets, body = [], bytearray()
    for i in range(CELLS * CELLS):
        offsets.append(head + len(body))
        if building_count[i] or road_count[i]:
            square = bytearray()
            varint(square, building_count[i]); square += buildings[i]
            varint(square, road_count[i]); square += roads[i]
            body += zlib.compress(bytes(square), 6)
    offsets.append(head + len(body))
    if offsets[-1] >= 1 << 32: raise SystemExit('%s: too big for one file' % name)
    path = os.path.join(DATA, 'urban', name[:3], name + '.urb')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path + '.part', 'wb') as f:
        f.write(b'EURB' + struct.pack('>BBH', 1, 0, CELLS) + struct.pack('>%dI' % len(offsets), *offsets) + body)
    os.replace(path + '.part', path)
    return seen, os.path.getsize(path)

def main():
    names = sys.argv[1:]
    if not names or any(len(n) != 7 or n[0] not in 'NS' or n[3] not in 'EW' or not (n[1:3] + n[4:7]).isdigit() for n in names):
        raise SystemExit(__doc__)
    import duckdb
    db = duckdb.connect()
    db.execute("SET memory_limit='1GB'; SET threads=4; SET preserve_insertion_order=false")
    if SOURCE.startswith('s3://'): db.execute("INSTALL httpfs; LOAD httpfs; SET s3_region='us-west-2'")
    for name in names:
        started = time.time()
        seen, size = pack(name, db)
        print('%s  %s: %s; %.1f MB, %.0f s' % (time.strftime('%Y-%m-%d %H:%M'), name,
              ', '.join('%d %s' % (n, what) for what, n in seen.items()), size / 1e6, time.time() - started), flush=True)

if __name__ == '__main__':
    main()
