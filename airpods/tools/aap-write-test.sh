#!/bin/bash
# Writes one control of the owner's AirPods and reads it back through a new session.
#   aap-write-test.sh ID DATA     ID two hex digits (2E), DATA one to four bytes as hex without spaces (32)
#   aap-write-test.sh --read ID   print the value a new session reports
# Needs the probe installed, the exemption set with the owner's yes, the phone unlocked, the AirPods connected.
# The pair is found by PAIR_NAME_PART (default "AirPods"), so no address is typed or logged.
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PART="${PAIR_NAME_PART:-AirPods}"

session() { # seconds [cmds]: one probe session, prints its log
  local args=(--es name "$PART" --ei seconds "$1" --es order hcn)
  [ -n "${2:-}" ] && args+=(--es cmds "$2")
  adb shell am force-stop org.sovereignos.airpods
  adb logcat -c
  adb shell am start -n org.sovereignos.airpods.probe/.ProbeActivity "${args[@]}" > /dev/null
  sleep $(($1 + 3))
  adb logcat -d -s AirPodsProbe
}

read_id() { session 12 | python3 "$HERE/aap-capture-table.py" --get "$1"; }

if [ "${1:-}" = "--read" ]; then
  read_id "${2:?control id}"
  exit 0
fi
ID="${1:?control id, two hex digits}"
DATA="${2:?one to four data bytes as hex without spaces}"
[[ "$ID" =~ ^[0-9A-Fa-f]{2}$ ]] || { echo "bad id: $ID"; exit 1; }
[[ "$DATA" =~ ^([0-9A-Fa-f]{2}){1,4}$ ]] || { echo "bad data: $DATA"; exit 1; }
while [ ${#DATA} -lt 8 ]; do DATA="${DATA}00"; done
FRAME="040004000900${ID^^}${DATA^^}"
BEFORE="$(read_id "$ID")"
session 14 "raw:$FRAME" > /dev/null
AFTER="$(read_id "$ID")"
echo "id=${ID^^} before=$BEFORE sent=$FRAME after=$AFTER"
