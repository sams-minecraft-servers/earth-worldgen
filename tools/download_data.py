#!/usr/bin/env python3
"""Downloads the data for the whole planet: elevation tiles and the land cover map.

    tools/download_data.py list                 what there is to fetch and how big it is
    tools/download_data.py fetch [rate]         fetch what is missing, at most `rate` per second (default 20M)

Elevation: one-degree tiles from the public bucket elevation-tiles-prod (skadi), kept gzipped in
<data>/hgt-gz/N45/N45E006.hgt.gz. Land cover: ESA WorldCover 2021 from the public bucket
esa-worldcover, in <data>/worldcover, as the mod reads it. <data> is EARTH_DATA or ~/earth-data.

It can be stopped and started again at any time: a file already here with the right size is
skipped, and a file is only given its name once it is complete. One file at a time, through curl,
so the rate is the whole of what this takes from the line. Needs nothing beyond Python and curl.
"""
import os, subprocess, sys, time, urllib.parse, urllib.request
import xml.etree.ElementTree as ET

DATA = os.environ.get('EARTH_DATA', os.path.expanduser('~/earth-data'))
SOURCES = [
    # name, bucket address, prefix in the bucket, the files wanted, where they go
    ('land cover', 'https://esa-worldcover.s3.eu-central-1.amazonaws.com', 'v200/2021/map/', '_Map.tif', lambda key: 'worldcover/' + key.split('/')[-1]),
    ('elevation', 'https://s3.amazonaws.com/elevation-tiles-prod', 'skadi/', '.hgt.gz', lambda key: 'hgt-gz/' + key[len('skadi/'):]),
]
NS = '{http://s3.amazonaws.com/doc/2006-03-01/}'

def listing(name, base, prefix, suffix):
    """Every wanted file in a bucket: (key, size). Kept in <data>/<name>.list so it is asked for once."""
    cache = os.path.join(DATA, name.replace(' ', '-') + '.list')
    if os.path.exists(cache):
        return [(k, int(s)) for k, s in (line.rstrip('\n').split('\t') for line in open(cache))]
    found, token = [], None
    while True:
        query = {'list-type': '2', 'prefix': prefix, 'max-keys': '1000'}
        if token: query['continuation-token'] = token
        for attempt in range(6):
            try:
                with urllib.request.urlopen(base + '/?' + urllib.parse.urlencode(query), timeout=60) as r: page = ET.fromstring(r.read())
                break
            except Exception as e:
                if attempt == 5: raise
                time.sleep(5 * (attempt + 1))
        for item in page.iter(NS + 'Contents'):
            key = item.find(NS + 'Key').text
            if key.endswith(suffix): found.append((key, int(item.find(NS + 'Size').text)))
        token = page.findtext(NS + 'NextContinuationToken')
        if not token: break
    with open(cache + '.part', 'w') as f: f.writelines('%s\t%d\n' % item for item in found)
    os.replace(cache + '.part', cache)
    return found

def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else 'list'
    rate = sys.argv[2] if len(sys.argv) > 2 else '20M'
    os.makedirs(DATA, exist_ok=True)
    todo, total = [], 0
    for name, base, prefix, suffix, place in SOURCES:
        files = listing(name, base, prefix, suffix)
        missing = [(base + '/' + key, os.path.join(DATA, place(key)), size) for key, size in files
                   if not (os.path.exists(os.path.join(DATA, place(key))) and os.path.getsize(os.path.join(DATA, place(key))) == size)]
        print('%s: %d files, %.1f GB in all; %d files, %.1f GB still to fetch' % (
            name, len(files), sum(s for _, s in files) / 1e9, len(missing), sum(m[2] for m in missing) / 1e9), flush=True)
        todo += missing
    total = sum(m[2] for m in todo)
    if mode != 'fetch': return
    free = os.statvfs(DATA).f_bavail * os.statvfs(DATA).f_frsize
    if free < total + 200e9: sys.exit('not starting: %.0f GB free, and this needs %.0f GB with 200 GB left over' % (free / 1e9, total / 1e9))
    done, started, failed = 0, time.time(), 0
    for n, (url, path, size) in enumerate(todo):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        code = subprocess.call(['curl', '-sf', '--retry', '8', '--retry-delay', '10', '--retry-all-errors', '--connect-timeout', '30', '--limit-rate', rate, '-o', path + '.part', url])
        if code == 0 and os.path.getsize(path + '.part') == size: os.replace(path + '.part', path); done += size
        else:
            failed += 1; print('failed (curl %d): %s' % (code, url), flush=True)
            if os.path.exists(path + '.part'): os.remove(path + '.part')
        if n % 200 == 199 or n == len(todo) - 1:
            hours = (time.time() - started) / 3600
            print('%s  %d of %d files, %.1f of %.1f GB, %.1f h so far, about %.1f h to go, %d failed' % (
                time.strftime('%Y-%m-%d %H:%M'), n + 1, len(todo), done / 1e9, total / 1e9, hours, hours * (total - done) / max(done, 1), failed), flush=True)
    print('finished: %d files could not be fetched; run again to retry them' % failed if failed else 'finished: everything is here', flush=True)

if __name__ == '__main__':
    main()
