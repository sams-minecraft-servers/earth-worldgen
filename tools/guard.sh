#!/usr/bin/env bash
# Runs a command so that it cannot take the machine's memory: on a machine that also runs game
# servers, a process that uses up what is left freezes everything.
#
#   tools/guard.sh <memory limit> <command...>      e.g. tools/guard.sh 4G ./gradlew build --no-daemon
#
# - refuses to start unless the limit plus GUARD_RESERVE_GB (default 10) is available right now
# - the command and everything it starts are killed by the kernel beyond the limit (no swap)
# - the command is stopped after GUARD_TIMEOUT (default 30m), and at once if available memory
#   on the machine drops below GUARD_FLOOR_GB (default 6), whoever is using it
set -euo pipefail
LIMIT=${1:?usage: tools/guard.sh <memory limit, e.g. 4G> <command...>}; shift
[ $# -gt 0 ] || { echo "guard: no command given"; exit 2; }
RESERVE_GB=${GUARD_RESERVE_GB:-10}
FLOOR_GB=${GUARD_FLOOR_GB:-6}

limit_kb=$(( $(numfmt --from=iec "$LIMIT") / 1024 ))
available_kb() { awk '/^MemAvailable:/ {print $2}' /proc/meminfo; }
need_kb=$(( limit_kb + RESERVE_GB * 1048576 ))
if [ "$(available_kb)" -lt "$need_kb" ]; then
    echo "guard: not starting: $(( $(available_kb) / 1048576 )) GB available, need $LIMIT plus $RESERVE_GB GB for everything else on the machine"
    exit 75
fi

UNIT="earth-guard-$$"
systemd-run --user --scope -q --unit="$UNIT" -p MemoryMax="$LIMIT" -p MemorySwapMax=0 \
    timeout --kill-after=20 "${GUARD_TIMEOUT:-30m}" "$@" <&0 &
CHILD=$!
trap 'systemctl --user kill "$UNIT.scope" 2>/dev/null || true' TERM INT
(
    while kill -0 $CHILD 2>/dev/null; do
        if [ "$(available_kb)" -lt $(( FLOOR_GB * 1048576 )) ]; then
            echo "guard: available memory fell below $FLOOR_GB GB, stopping $UNIT" >&2
            systemctl --user kill --signal=KILL "$UNIT.scope" 2>/dev/null || kill -KILL $CHILD 2>/dev/null
            break
        fi
        sleep 2
    done
) &
WATCH=$!
STATUS=0
wait $CHILD || STATUS=$?
kill $WATCH 2>/dev/null || true
[ $STATUS -eq 124 ] && echo "guard: stopped after ${GUARD_TIMEOUT:-30m}" >&2
[ $STATUS -eq 137 ] && echo "guard: killed (over $LIMIT, or the machine ran low on memory)" >&2
exit $STATUS
