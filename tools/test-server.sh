#!/usr/bin/env bash
# Runs the throwaway test server in run/ (never one of the live servers), feeds it the console
# commands in the given file once it has started, then stops it.
#
#   tools/test-server.sh commands.txt [--fresh]     --fresh deletes the test world first
#
# In the commands file a line "wait 20" pauses for that many seconds. TEST_HEAP (default 4G) and
# TEST_JAVA_OPTS (extra java options, e.g. a flight recording) can be set in the environment.
#
# The server runs under tools/guard.sh: it is refused when the machine is short of memory, cannot
# use more than its heap plus 2 GB, and is stopped after TEST_TIMEOUT (default 30m). Only one test
# server runs at a time.
set -euo pipefail
cd "$(dirname "$0")/.."
COMMANDS=${1:?usage: tools/test-server.sh commands.txt [--fresh]}
RUN=${TEST_RUN:-run}
ROOT=$PWD
HEAP=${TEST_HEAP:-4G}
LIMIT=$(( $(numfmt --from=iec "$HEAP") / 1048576 + 2048 ))M
# Only this user's: a live server run by root may also be a Fabric server, and is not ours to stop.
if pgrep -u "$(id -u)" -f "[f]abric-server.jar --nogui|[u]nix_args.txt --nogui" > /dev/null; then echo "a test server is already running; stop it first"; exit 1; fi
[ -s "$RUN/fabric-server.jar" ] || [ -s "$RUN/run.sh" ] || { echo "$RUN has no server (fabric-server.jar, or a NeoForge run.sh)"; exit 1; }
if [ "${2:-}" = "--fresh" ]; then find "$RUN/world" -mindepth 1 -maxdepth 1 ! -name datapacks -exec rm -r {} +; fi

mkdir -p "$RUN/mods" "$RUN/config" "$RUN/world/datapacks"
find "$RUN/mods" -name 'earth-worldgen-*.jar' -delete
cp ${TEST_LOADER:-fabric}/build/libs/earth-worldgen-*.jar "$RUN/mods/"
# TEST_JAR_ONLY=1: nothing but the jar, as someone dropping it into mods/ would have (the mod installs its own datapack)
if [ -z "${TEST_JAR_ONLY:-}" ]; then
rm -rf "$RUN/world/datapacks/earth" && cp -r datapack "$RUN/world/datapacks/earth"
mkdir -p "$RUN/config/earth-mod"
[ -f "$RUN/config/earth-mod/earth_worldgen.json" ] || [ -f "$RUN/config/earth_worldgen.json" ] || cp config.example.json "$RUN/config/earth-mod/earth_worldgen.json"
fi
echo "eula=true" > "$RUN/eula.txt"
[ -f "$RUN/server.properties" ] || cat > "$RUN/server.properties" <<'PROPS'
server-ip=127.0.0.1
server-port=25599
level-name=world
motd=Earth worldgen test
online-mode=true
view-distance=6
simulation-distance=4
spawn-protection=0
enable-rcon=false
sync-chunk-writes=false
max-tick-time=-1
PROPS
# Never wait for the disk after each chunk: the disk is shared, and syncing every write loads it heavily.
if grep -q '^sync-chunk-writes=' "$RUN/server.properties"; then sed -i 's/^sync-chunk-writes=.*/sync-chunk-writes=false/' "$RUN/server.properties"; else echo sync-chunk-writes=false >> "$RUN/server.properties"; fi

FIFO=$(mktemp -u) && mkfifo "$FIFO"
trap 'rm -f "$FIFO"' EXIT
# Fabric: fabric-server.jar. NeoForge: the argument file its installer writes (what its run.sh uses).
if [ -s "$RUN/fabric-server.jar" ]; then LAUNCH="-jar fabric-server.jar"; else LAUNCH="@$(cd "$RUN" && ls libraries/net/neoforged/neoforge/*/unix_args.txt | head -1)"; fi
( cd "$RUN" && GUARD_TIMEOUT=${TEST_TIMEOUT:-30m} exec ionice -c2 -n7 "$ROOT/tools/guard.sh" "$LIMIT" java -Xmx$HEAP -Xms1G ${TEST_JAVA_OPTS:-} $LAUNCH --nogui < "$FIFO" > server.out 2>&1 ) &
SERVER=$!
exec 3> "$FIFO"
for i in $(seq 1 180); do
    grep -q 'Done (' "$RUN/server.out" && break
    kill -0 $SERVER 2>/dev/null || { echo "the server stopped while starting:"; tail -40 "$RUN/server.out"; exit 1; }
    sleep 1
done
grep -q 'Done (' "$RUN/server.out" || { echo "the server did not finish starting:"; tail -40 "$RUN/server.out"; kill $SERVER; exit 1; }
while IFS= read -r line; do
    case "$line" in
        ''|'#'*) ;;
        wait\ *) sleep "${line#wait }" ;;
        *) echo "$line" >&3 ;;
    esac
done < "$COMMANDS"
echo "stop" >&3
exec 3>&-
wait $SERVER || true
