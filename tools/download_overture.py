#!/usr/bin/env python3
"""Downloads Overture Maps' buildings and road segments (about 350 GB) to <data>/overture, so that
tools/pack_urban.py can read them from disk (URBAN_SOURCE=<data>/overture/release/<release>).

    tools/download_overture.py [MB per second, default 12]

Restartable: a file already there at its full size is skipped. One file at a time, at the given
rate, so the live servers keep their disk (on 2026-10-14 a saturated disk froze skyblock.tj): run it
with ionice -c3 and nice, as deploy/overture-download.service does. ODbL / CDLA data: the website
must credit "OpenStreetMap contributors, Overture Maps Foundation".
"""
import os, re, subprocess, sys, time, urllib.parse, urllib.request

RELEASE = '2026-09-23.1'
BUCKET = 'https://overturemaps-us-west-2.s3.us-west-2.amazonaws.com'
PREFIXES = ['theme=buildings/type=building/', 'theme=transportation/type=segment/']
OUT = os.path.join(os.environ.get('EARTH_DATA', os.path.expanduser('~/earth-data')), 'overture')

def listing(prefix):
    token, files = None, []
    while True:
        query = {'list-type': '2', 'prefix': 'release/%s/%s' % (RELEASE, prefix)}
        if token: query['continuation-token'] = token
        xml = urllib.request.urlopen('%s/?%s' % (BUCKET, urllib.parse.urlencode(query)), timeout=120).read().decode()
        files += [(k, int(s)) for k, s in re.findall(r'<Key>([^<]+)</Key>.*?<Size>(\d+)</Size>', xml, re.S)]
        m = re.search(r'<NextContinuationToken>([^<]+)</NextContinuationToken>', xml)
        if not m: return files
        token = m.group(1)

def main():
    rate = int(sys.argv[1]) if len(sys.argv) > 1 else 12
    files = [f for p in PREFIXES for f in listing(p)]
    total = sum(s for _, s in files)
    print('%d files, %.0f GB, at %d MB/s' % (len(files), total / 1e9, rate), flush=True)
    done = 0
    for n, (key, size) in enumerate(files, 1):
        path = os.path.join(OUT, key)
        if os.path.exists(path) and os.path.getsize(path) == size:
            done += size; continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        for attempt in range(5):
            r = subprocess.run(['curl', '-sS', '--fail', '--limit-rate', '%dM' % rate, '--retry', '3', '-o', path + '.part', '%s/%s' % (BUCKET, urllib.parse.quote(key))])
            if r.returncode == 0 and os.path.getsize(path + '.part') == size:
                os.replace(path + '.part', path); break
            time.sleep(30)
        else:
            print('FAILED %s' % key, flush=True); continue
        done += size
        print('%s  %d of %d  %.0f of %.0f GB' % (time.strftime('%Y-%m-%d %H:%M'), n, len(files), done / 1e9, total / 1e9), flush=True)
    print('finished: everything is here', flush=True)

if __name__ == '__main__':
    main()
