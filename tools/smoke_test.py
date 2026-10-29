#!/usr/bin/env python3
"""Starts a real server with the mod jar, makes an Earth world, checks it and stops. Needs only
Python and Java 25, so it runs the same on Linux, Windows and macOS (the GitHub build runs it on all three).

    tools/smoke_test.py <fabric|neoforge> <mod jar> <folder> [--world-type] [--data <earth-data>] [--max-mbps 4]

<folder> is made (or emptied) and the server is downloaded into it. Without --world-type the world
is made the way a server owner would: the jar on its own, the mod installing its datapack. With
--world-type it is made with the Earth world type (level-type=earth_worldgen:earth), the way
singleplayer does. Map data is downloaded as on a player's first start, unless --data names a folder
that already has it. Prints what it found and exits 1 if anything is wrong.
"""
import json, os, queue, re, shutil, subprocess, sys, threading, time, urllib.request

MINECRAFT = '26.3'


def gradle_property(name):
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(root, 'gradle.properties')) as f:
        for line in f:
            if line.startswith(name + '='): return line.split('=', 1)[1].strip()
    raise SystemExit(name + ' is not in gradle.properties')


def fetch(url, to):
    print('downloading', url, flush=True)
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'earth-worldgen smoke test'}), timeout=120) as r, open(to, 'wb') as f:
        shutil.copyfileobj(r, f)


def install(loader, folder):
    """The command that starts the server, after putting the server into folder."""
    java = shutil.which('java') or 'java'
    if loader == 'fabric':
        with urllib.request.urlopen('https://meta.fabricmc.net/v2/versions/installer', timeout=60) as r:
            installer = next(v['version'] for v in json.load(r) if v.get('stable'))
        fetch(f'https://meta.fabricmc.net/v2/versions/loader/{MINECRAFT}/{gradle_property("loader_version")}/{installer}/server/jar',
              os.path.join(folder, 'fabric-server.jar'))
        return [java, '-Xmx3G', '-jar', 'fabric-server.jar', 'nogui']
    version = gradle_property('neoforge_version')
    fetch(f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-installer.jar',
          os.path.join(folder, 'installer.jar'))
    subprocess.run([java, '-jar', 'installer.jar', '--installServer'], cwd=folder, check=True, stdout=subprocess.DEVNULL)
    args = 'win_args.txt' if os.name == 'nt' else 'unix_args.txt'
    return [java, '-Xmx3G', '@' + os.path.join('libraries', 'net', 'neoforged', 'neoforge', version, args), 'nogui']


def main():
    plain = [a for a in sys.argv[1:] if not a.startswith('--')]
    for flag in ('--data', '--max-mbps'):
        if flag in sys.argv: plain.remove(sys.argv[sys.argv.index(flag) + 1])
    loader, jar, folder = plain[0], os.path.abspath(plain[1]), os.path.abspath(plain[2])
    world_type = '--world-type' in sys.argv
    shutil.rmtree(folder, ignore_errors=True)
    os.makedirs(os.path.join(folder, 'mods'))
    os.makedirs(os.path.join(folder, 'config', 'earth-mod'))
    shutil.copy(jar, os.path.join(folder, 'mods'))
    command = install(loader, folder)

    with open(os.path.join(folder, 'eula.txt'), 'w') as f: f.write('eula=true\n')
    with open(os.path.join(folder, 'server.properties'), 'w') as f:
        f.write('server-ip=127.0.0.1\nserver-port=25598\nonline-mode=false\nview-distance=4\nsimulation-distance=4\n'
                'max-tick-time=-1\nsync-chunk-writes=false\n')
        if world_type: f.write('level-type=earth_worldgen\\:earth\n')
    config = {'download': {}}
    if world_type: config['installDatapack'] = False
    if '--max-mbps' in sys.argv: config['download']['maxMegabytesPerSecond'] = float(sys.argv[sys.argv.index('--max-mbps') + 1])
    if '--data' in sys.argv:
        data = os.path.abspath(sys.argv[sys.argv.index('--data') + 1])
        config.update({'dataDir': os.path.join(data, 'elevation'), 'landCoverDir': os.path.join(data, 'worldcover'), 'urbanDir': os.path.join(data, 'urban')})
        config['download'].update({'elevation': False, 'landCover': False})
    with open(os.path.join(folder, 'config', 'earth-mod', 'earth_worldgen.json'), 'w') as f: json.dump(config, f)

    print('starting:', ' '.join(command), flush=True)
    server = subprocess.Popen(command, cwd=folder, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              text=True, encoding='utf-8', errors='replace', bufsize=1)
    lines, log = queue.Queue(), []

    def read():
        for line in server.stdout: lines.put(line)
        lines.put(None)
    threading.Thread(target=read, daemon=True).start()

    def wait_for(pattern, seconds):
        end = time.time() + seconds
        while time.time() < end:
            try: line = lines.get(timeout=1)
            except queue.Empty: continue
            if line is None: return None
            log.append(line)
            sys.stdout.write(line)
            if re.search(pattern, line): return line
        return None

    def send(command):
        server.stdin.write(command + '\n')
        server.stdin.flush()

    ok = wait_for(r'Done \(', 900) is not None
    stronghold = None
    if ok:
        send('locate structure minecraft:stronghold')
        stronghold = wait_for(r'nearest minecraft:stronghold|Could not find', 300)
        send('datapack list')
        wait_for(r'data pack\(s\) enabled', 60)
    if server.poll() is None:
        send('stop')
        wait_for(r'\Z\A', 120)  # read until the server has gone
    try: server.wait(timeout=60)
    except subprocess.TimeoutExpired: server.kill()
    text = ''.join(log)

    checks = {
        'server started': ok,
        'the world is the Earth': 'Earth: borders of' in text and 'Earth: biomes by' in text,
        'datapack installed' if not world_type else 'made with the Earth world type':
            ('installed the mod\'s datapack' in text) if not world_type else ('installed the mod\'s datapack' not in text),
        'spawn on land near Nairobi': bool(re.search(r'Earth: downloaded elevation S0[12]E03[67]', text)) or '--data' in sys.argv,
        'strongholds spread over the map (not rings round 0, 0)': bool(stronghold and 'nearest minecraft:stronghold' in stronghold),
        'no errors from the mod': not re.search(r'(ERROR|Exception).*(Earth|earth_worldgen|tj\.earth)|tj\.earth\.worldgen\.[A-Za-z.]+\(', text),
        'stopped cleanly': server.returncode == 0,
    }
    print()
    for name, passed in checks.items(): print(('PASS  ' if passed else 'FAIL  ') + name)
    sys.exit(0 if all(checks.values()) else 1)


if __name__ == '__main__':
    main()
