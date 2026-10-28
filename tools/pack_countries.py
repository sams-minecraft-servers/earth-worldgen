#!/usr/bin/env python3
"""Packs the world's country borders into the file the mod reads (see Countries.java).

    tools/pack_countries.py ne_10m_admin_0_countries.geojson src/main/resources/earth_worldgen/countries.bin.gz

The input is Natural Earth's 1:10m "Admin 0 - Countries" (public domain), as GeoJSON:
https://www.naturalearthdata.com/downloads/10m-cultural-vectors/10m-admin-0-countries/

Format (big-endian, gzipped): "ECTY", int version 1, int countries, then per country UTF code and
UTF name; int polygons, then per polygon: short country index, int west south east north (millionths
of a degree), int rings, then per ring int points and the points as int lon, int lat (millionths of
a degree). The first ring of a polygon is its outline, any others are holes; a point is inside when
an odd number of the polygon's rings surround it.
"""
import gzip, json, struct, sys

def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    features = json.load(open(sys.argv[1], encoding='utf-8'))['features']
    countries, index, polygons = [], {}, []
    for f in features:
        p = f['properties']
        code = p.get('ISO_A2_EH') if p.get('ISO_A2') in (None, '-99') else p.get('ISO_A2')
        if not code or code == '-99':
            continue
        if code not in index:
            index[code] = len(countries)
            countries.append((code, p.get('NAME_EN') or p.get('NAME') or code))
        g = f['geometry']
        shapes = [g['coordinates']] if g['type'] == 'Polygon' else g['coordinates'] if g['type'] == 'MultiPolygon' else []
        for rings in shapes:
            rings = [[(round(x * 1e6), round(y * 1e6)) for x, y in ring] for ring in rings]
            xs = [x for x, _ in rings[0]]; ys = [y for _, y in rings[0]]
            polygons.append((index[code], min(xs), min(ys), max(xs), max(ys), rings))

    out = bytearray(b'ECTY') + struct.pack('>ii', 1, len(countries))
    utf = lambda s: struct.pack('>H', len(s.encode('utf-8'))) + s.encode('utf-8')
    for code, name in countries:
        out += utf(code) + utf(name)
    out += struct.pack('>i', len(polygons))
    points = 0
    for c, w, s, e, n, rings in polygons:
        out += struct.pack('>hiiiii', c, w, s, e, n, len(rings))
        for ring in rings:
            out += struct.pack('>i', len(ring))
            for x, y in ring:
                out += struct.pack('>ii', x, y)
            points += len(ring)
    with gzip.open(sys.argv[2], 'wb', 9) as f:
        f.write(out)
    print('%d countries, %d polygons, %d points' % (len(countries), len(polygons), points))

if __name__ == '__main__':
    main()
