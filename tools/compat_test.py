#!/usr/bin/env python3
"""Starts a throwaway server with Earth Worldgen and other mods, and reports how they get on.

    tools/compat_test.py <loader> <minecraft version> <mod> [<mod> ...] [--data <earth-data folder>] [--keep]

    tools/compat_test.py fabric 26.3 lithium
    tools/compat_test.py neoforge 26.3 terralith biomes-o-plenty --data ~/earth-data

<mod> is a Modrinth project (its slug, as in modrinth.com/mod/<slug>); the newest version for the
loader and Minecraft version is used, with every mod it requires. A jar file path works too, for
mods that are not on Modrinth.

The server is a copy of run-dnd/ (Fabric) or run-neoforge/ (NeoForge) with this repository's
newly built jar for that loader. It makes a new world, looks for a village and a mineshaft, and
makes the land at the Eiffel Tower. The report says whether it started, what went wrong in the log,
whether the Earth's land and the monument came out, which biomes and ores were made, and which
mods were in. With --data, the map data is read from that folder and nothing is downloaded. With
--biomes, land is also made at seven places of very different climates and each one's biomes are
listed. --ores a,b counts blocks whose names contain a or b (default "_ore").
"""
import json, os, re, shutil, subprocess, sys, time, urllib.parse, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UA = {'User-Agent': 'earth-worldgen compatibility test (github.com/sams-minecraft-servers/earth-worldgen)'}
BASE = {'fabric': 'run-dnd', 'neoforge': 'run-neoforge'}
EIFFEL_CHUNK = (51072, -1087792, 51087, -1087777)
BLOCKS_PER_DEGREE = 40_075_016.686 / 5 / 360
# --biomes: places of very different climates, to see which biomes each gets.
BIOME_PLACES = [('Quebec, Canada', 49.5, -73.5), ('Provence, France', 43.7, 5.5), ('Kazakh steppe', 50.5, 70.0),
                ('Amazon rainforest', -3.5, -62.0), ('Grand Canyon floor', 36.1, -112.1), ('Louisiana', 29.9, -90.5),
                ('Yakutia, Siberia', 61.5, 129.5)]

def place_box(lat, lon, half=48):
    x, z = round(lon * BLOCKS_PER_DEGREE), round(-lat * BLOCKS_PER_DEGREE)
    return x - half, z - half, x + half, z + half

def api(path):
    with urllib.request.urlopen(urllib.request.Request('https://api.modrinth.com/v2/' + path, headers=UA), timeout=30) as r:
        return json.load(r)

def newest(project, loader, mc):
    q = urllib.parse.urlencode({'game_versions': json.dumps([mc]), 'loaders': json.dumps([loader])})
    versions = api('project/%s/version?%s' % (project, q))
    return versions[0] if versions else None

def resolve(projects, loader, mc):
    """Every mod to install: the ones asked for and what they require, newest version of each."""
    wanted, seen, files = list(projects), set(), []
    while wanted:
        p = wanted.pop(0)
        if p in seen:
            continue
        seen.add(p)
        v = newest(p, loader, mc)
        if v is None:
            raise SystemExit('%s has no %s version for Minecraft %s on Modrinth' % (p, loader, mc))
        f = next((f for f in v['files'] if f.get('primary')), v['files'][0])
        files.append((p, v['version_number'], f['url'], f['filename']))
        for d in v.get('dependencies', []):
            if d.get('dependency_type') != 'required':
                continue
            if d.get('project_id'):
                wanted.append(d['project_id'])
            elif d.get('version_id'):
                wanted.append(api('version/' + d['version_id'])['project_id'])
    return files

def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if '--ores' in sys.argv: args.remove(sys.argv[sys.argv.index('--ores') + 1])
    if '--config' in sys.argv: args.remove(sys.argv[sys.argv.index('--config') + 1])
    if len(args) < 2:
        sys.exit(__doc__)
    loader, mc, mods = args[0], args[1], args[2:]
    data = sys.argv[sys.argv.index('--data') + 1] if '--data' in sys.argv else None
    if data in mods: mods.remove(data)
    # --extra "cmd;cmd;wait 60": more console commands, run after the standard ones (e.g. a Chunky job)
    extra = sys.argv[sys.argv.index('--extra') + 1] if '--extra' in sys.argv else ''
    if extra in mods: mods.remove(extra)
    name = 'run-compat-%s-%s' % (loader, '-'.join(os.path.basename(m).split('.')[0] for m in mods) or 'alone')[:80]
    run = os.path.join(ROOT, name)
    base = os.path.join(ROOT, BASE[loader])
    if not os.path.isdir(base):
        raise SystemExit('%s is missing: set up a %s test server there first' % (base, loader))
    shutil.rmtree(run, ignore_errors=True)
    os.makedirs(os.path.join(run, 'mods'))
    for item in ('libraries', 'versions', 'fabric-server.jar', 'run.sh', 'user_jvm_args.txt'):
        src = os.path.join(base, item)
        if os.path.isdir(src): os.symlink(src, os.path.join(run, item))
        elif os.path.exists(src): shutil.copy(src, run)
    with open(os.path.join(run, 'server.properties'), 'w') as f:
        f.write('server-ip=127.0.0.1\nserver-port=25599\nlevel-name=world\nonline-mode=true\nview-distance=4\nsimulation-distance=4\nmax-tick-time=-1\nsync-chunk-writes=false\n')
    os.makedirs(os.path.join(run, 'config', 'earth-mod'))
    config = {'download': {'maxMegabytesPerSecond': 4}}
    if data:
        data = os.path.expanduser(data)
        config.update({'dataDir': os.path.join(data, 'elevation'), 'landCoverDir': os.path.join(data, 'worldcover'), 'urbanDir': os.path.join(data, 'urban'),
                       'download': {'elevation': False, 'landCover': False}})
    # --config '{"monuments": {"disabled": [...]}}': more settings, merged in.
    if '--config' in sys.argv:
        for k, v in json.loads(sys.argv[sys.argv.index('--config') + 1]).items():
            config[k] = {**config.get(k, {}), **v} if isinstance(v, dict) else v
    with open(os.path.join(run, 'config', 'earth-mod', 'earth_worldgen.json'), 'w') as f:
        json.dump(config, f)

    installed = []
    paths = [m for m in mods if m.endswith('.jar')]
    for m in paths:
        shutil.copy(m, os.path.join(run, 'mods'))
        installed.append((os.path.basename(m), 'file'))
    for project, version, url, filename in resolve([m for m in mods if not m.endswith('.jar')], loader, mc):
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r, open(os.path.join(run, 'mods', filename), 'wb') as out:
            shutil.copyfileobj(r, out)
        installed.append((project, version))

    x1, z1, x2, z2 = EIFFEL_CHUNK
    commands = os.path.join(run, 'commands.txt')
    with open(commands, 'w') as f:
        f.write('wait 5\nlocate structure minecraft:village_plains\nlocate structure minecraft:mineshaft\n'
                'forceload add %d %d %d %d\nwait 75\nforceload remove all\nwait 5\n' % (x1, z1, x2, z2))
        if '--biomes' in sys.argv:
            for _, lat, lon in BIOME_PLACES:
                f.write('forceload add %d %d %d %d\n' % place_box(lat, lon))
            f.write('wait 120\nforceload remove all\nwait 5\n')
        for c in [c.strip() for c in extra.split(';') if c.strip()]:
            f.write(c + '\n')
    env = dict(os.environ, TEST_RUN=name, TEST_LOADER=loader, TEST_JAR_ONLY='1', TEST_TIMEOUT='15m')
    started = time.time()
    subprocess.run([os.path.join(ROOT, 'tools', 'test-server.sh'), commands], cwd=ROOT, env=env, timeout=1100)
    log = open(os.path.join(run, 'server.out'), errors='replace').read() if os.path.exists(os.path.join(run, 'server.out')) else ''

    # What a person looking at the log would want to know.
    report = {'loader': loader, 'minecraft': mc, 'mods': installed, 'seconds': round(time.time() - started)}
    report['started'] = 'Done (' in log
    report['earth_world'] = 'installed the mod\'s datapack' in log
    report['villages_off'] = 'Could not find a structure of type "minecraft:village_plains"' in log
    report['mineshaft_found'] = 'nearest minecraft:mineshaft' in log
    problems = []
    for line in log.splitlines():
        if re.search(r'(ERROR|FATAL|Exception|Mixin.*(conflict|failed|could not)|InjectionError|InvalidInjection)', line) and 'kqueue' not in line and 'Appender' not in line and 'Only supported on OSX/BSD' not in line:
            problems.append(line.strip()[:220])
    report['problems'] = problems[:25]
    report['chunky'] = re.findall(r'\[Chunky\][^\n]{0,160}', log)[-3:]
    report['problem_count'] = len(problems)
    # The monument: the Eiffel Tower's grey concrete base on top of the land at its centre.
    top = subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'region_map.py'), os.path.join(run, 'world', 'dimensions', 'minecraft', 'overworld', 'region'),
                          os.path.join(run, 'map.png'), '--blocks'], capture_output=True, text=True, timeout=300).stdout
    m = re.search(r"top blocks: (\{.*\})", top)
    report['top_blocks'] = m.group(1)[:300] if m else 'no finished chunks'
    report['monument_built'] = bool(m and 'gray_concrete' in m.group(1))
    # Biomes and ores, over everything made and (with --biomes) at each test place.
    region = os.path.join(run, 'world', 'dimensions', 'minecraft', 'overworld', 'region')
    ores = sys.argv[sys.argv.index('--ores') + 1] if '--ores' in sys.argv else '_ore'
    def chunk_report(*more):
        out = subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'chunk_report.py'), region, '--match', ores, '--json', *more],
                             capture_output=True, text=True, timeout=600).stdout
        try: return json.loads(out)
        except ValueError: return {}
    whole = chunk_report()
    report['biomes'] = whole.get('biomes', {})
    report['ores'] = whole.get('blocks', {})
    if '--biomes' in sys.argv:
        report['places'] = {name: chunk_report('--box', ','.join(map(str, place_box(lat, lon)))).get('biomes', {}) for name, lat, lon in BIOME_PLACES}
    print(json.dumps(report, indent=1))
    with open(os.path.join(run, 'report.json'), 'w') as f:
        json.dump(report, f, indent=1)
    if '--keep' not in sys.argv:
        shutil.rmtree(os.path.join(run, 'world'), ignore_errors=True)

if __name__ == '__main__':
    main()
