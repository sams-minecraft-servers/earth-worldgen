#!/usr/bin/env python3
"""Packs the streets and buildings of every one-degree tile with a town in it (see pack_urban.py),
read straight from Overture's bucket, the most populous tiles first, skipping tiles already packed.

    <data>/urban-venv/bin/python tools/pack_planet.py

Each tile is packed by its own pack_urban.py process, one at a time, at the lowest disk and CPU
priority and under the memory limit of the service that runs it (deploy/earth-urban-build.service). Restartable. Progress
goes to stdout; the list of tiles is kept in <data>/urban/tiles.txt.
"""
import math, os, subprocess, sys, time

DATA = os.environ.get('EARTH_DATA', os.path.expanduser('~/earth-data'))
# Straight from Overture's bucket: a local copy meant 350 GB of writes, which the shared disk could not take
# alongside the live servers (2026-10-16). URBAN_SOURCE still points it at a local copy if there is one.
SOURCE = os.environ.get('URBAN_SOURCE', 's3://overturemaps-us-west-2/release/2026-09-23.1')
HERE = os.path.dirname(os.path.abspath(__file__))
LIST = os.path.join(DATA, 'urban', 'tiles.txt')

def name(lat, lon):
    return '%s%02d%s%03d' % ('N' if lat >= 0 else 'S', abs(lat), 'E' if lon >= 0 else 'W', abs(lon))

def tiles():
    """Every one-degree tile with a town in it (GeoNames, already on this machine), the most people first."""
    if os.path.exists(LIST): return [l.split()[0] for l in open(LIST) if l.strip()]
    import json
    people = {}
    for t in json.load(open(os.path.join(DATA, 'places', 'places.json')))['towns']:
        key = name(math.floor(t[1]), math.floor(t[2]))
        people[key] = people.get(key, 0) + t[3]
    order = sorted(people, key=lambda k: -people[k])
    os.makedirs(os.path.dirname(LIST), exist_ok=True)
    with open(LIST + '.part', 'w') as f:
        for k in order: f.write('%s %d\n' % (k, people[k]))
    os.replace(LIST + '.part', LIST)
    return order

def main():
    todo = tiles()
    print('%d tiles have buildings' % len(todo), flush=True)
    env = dict(os.environ, URBAN_SOURCE=SOURCE)
    for n, tile in enumerate(todo, 1):
        out = os.path.join(DATA, 'urban', tile[:3], tile + '.urb')
        if os.path.exists(out): continue
        r = subprocess.run(['timeout', '3600', sys.executable, os.path.join(HERE, 'pack_urban.py'), tile], env=env, capture_output=True, text=True)
        line = (r.stdout.strip().splitlines() or [''])[-1]
        print('%s  %d of %d  %s' % (time.strftime('%Y-%m-%d %H:%M'), n, len(todo), line if r.returncode == 0 else 'FAILED %s: %s' % (tile, (r.stderr.strip().splitlines() or ['?'])[-1])), flush=True)
    print('finished: every tile is packed', flush=True)

if __name__ == '__main__':
    main()
