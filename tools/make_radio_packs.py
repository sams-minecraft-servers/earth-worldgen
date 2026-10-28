#!/usr/bin/env python3
"""Builds the country radio's resource packs and manifest from audio files you supply.

    tools/make_radio_packs.py <audio folder> <output folder> [--pack-format 97]

The audio folder holds one folder per country, named by its two-letter code, with that station's
recordings as Ogg Vorbis files (.ogg, mono or stereo). They are played in name order, round and round:

    audio/
      fr/
        station.txt        the station's name on the first line (optional)
        01-morning.ogg
        02-afternoon.ogg
      de/
        ...
      static/              optional: short crackles played as the signal fades near a border
        1.ogg
        2.ogg

The output folder gets one pack per country (fr.zip...), static.zip, and manifest.json. Put the
packs where players' games can download them (any web server), and point the mod at them in
config/earth-mod/radio.json: "manifest" (the manifest.json, as a file path or a link) and
"packBaseUrl" (the folder's address). Rebuild and upload whenever you change the audio; with
"autoUpdate" on, the server picks the new packs up by itself.

Only use audio you have the right to play to your players. Needs nothing but Python 3.
"""
import hashlib, json, os, struct, sys, zipfile

def ogg_seconds(path):
    """The length of an Ogg Vorbis file: the last page's granule position over the sample rate."""
    with open(path, 'rb') as f:
        data = f.read()
    if data[:4] != b'OggS':
        raise ValueError('%s is not an Ogg file' % path)
    # The Vorbis identification header follows the first page header: "\x01vorbis", version, channels, rate.
    at = data.find(b'\x01vorbis')
    if at < 0:
        raise ValueError('%s is not Ogg Vorbis' % path)
    rate = struct.unpack('<I', data[at + 12:at + 16])[0]
    last = data.rfind(b'OggS')
    granule = struct.unpack('<q', data[last + 6:last + 14])[0]
    return round(granule / rate, 2)

def zip_pack(path, description, pack_format, sounds, files):
    meta = {'pack': {'description': description, 'pack_format': pack_format, 'min_format': pack_format, 'max_format': pack_format + 2}}
    with zipfile.ZipFile(path + '.part', 'w', zipfile.ZIP_STORED) as z:
        z.writestr('pack.mcmeta', json.dumps(meta, indent=1))
        z.writestr('assets/earthradio/sounds.json', json.dumps(sounds, indent=1))
        for name, source in sorted(files.items()):
            z.write(source, name)
    os.replace(path + '.part', path)
    with open(path, 'rb') as f:
        return hashlib.sha1(f.read()).hexdigest(), os.path.getsize(path)

def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if len(args) != 2:
        sys.exit(__doc__)
    audio, out = args
    pack_format = int(sys.argv[sys.argv.index('--pack-format') + 1]) if '--pack-format' in sys.argv else 97
    os.makedirs(out, exist_ok=True)
    manifest = {'packs': {}}

    static_dir = os.path.join(audio, 'static')
    if os.path.isdir(static_dir):
        oggs = sorted(f for f in os.listdir(static_dir) if f.lower().endswith('.ogg'))
        if oggs:
            sounds, files = {}, {}
            for n, f in enumerate(oggs):
                sounds['static.%d' % n] = {'sounds': [{'name': 'earthradio:static/%d' % n}]}
                files['assets/earthradio/sounds/static/%d.ogg' % n] = os.path.join(static_dir, f)
            sha1, size = zip_pack(os.path.join(out, 'static.zip'), 'Radio static', pack_format, sounds, files)
            seconds = max(1, round(max(ogg_seconds(os.path.join(static_dir, f)) for f in oggs)))
            manifest['static'] = {'file': 'static.zip', 'sha1': sha1, 'bytes': size, 'sounds': sorted(sounds), 'seconds': seconds}
            print('static: %d sounds' % len(oggs))

    for code in sorted(os.listdir(audio)):
        folder = os.path.join(audio, code)
        if code == 'static' or not os.path.isdir(folder):
            continue
        if len(code) != 2 or not code.isalpha():
            print('skipping %s: folders are named by two-letter country code' % code)
            continue
        code = code.lower()
        oggs = sorted(f for f in os.listdir(folder) if f.lower().endswith('.ogg'))
        if not oggs:
            print('skipping %s: no .ogg files' % code)
            continue
        station = code.upper() + ' radio'
        if os.path.exists(os.path.join(folder, 'station.txt')):
            with open(os.path.join(folder, 'station.txt'), encoding='utf-8') as f:
                station = (f.readline().strip() or station)
        sounds, files, parts = {}, {}, []
        for n, f in enumerate(oggs):
            name = '%s.%02d' % (code, n)
            sounds[name] = {'sounds': [{'name': 'earthradio:%s/%02d' % (code, n), 'stream': True}]}
            files['assets/earthradio/sounds/%s/%02d.ogg' % (code, n)] = os.path.join(folder, f)
            parts.append({'sound': 'earthradio:' + name, 'seconds': ogg_seconds(os.path.join(folder, f))})
        sha1, size = zip_pack(os.path.join(out, code + '.zip'), station, pack_format, sounds, files)
        manifest['packs'][code] = {'file': code + '.zip', 'sha1': sha1, 'bytes': size, 'station': station, 'parts': parts}
        print('%s: %s, %d parts, %.1f MB' % (code, station, len(parts), size / 1048576))

    with open(os.path.join(out, 'manifest.json.part'), 'w', encoding='utf-8') as f:
        json.dump(manifest, f, indent=1)
    os.replace(os.path.join(out, 'manifest.json.part'), os.path.join(out, 'manifest.json'))
    print('%d stations: %s' % (len(manifest['packs']), os.path.join(out, 'manifest.json')))

if __name__ == '__main__':
    main()
