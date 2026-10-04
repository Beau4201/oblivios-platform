#!/bin/bash
# Host test of check-page-conformance.py: a dump built from the metrics themselves must pass, and a
# moved, shifted or missing element must fail. No device needed.
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
METRICS="${1:-$HERE/../../../sovereignos-foundation/docs/design/airpods-settings-ui-2026-09-29.metrics-dark.json}"
WORK="$(mktemp -d)"
python3 - "$METRICS" "$WORK" <<'PY'
import json, sys
metrics, work = sys.argv[1], sys.argv[2]
density, width_px = 2.625, 1080
k = (width_px / density) / 350.2
els = [e for e in json.load(open(metrics))["elements"] if e["screen"] == "airpods-settings-page" and e["text"] and e["text"] != "9:41"]

def dump(path, edit=lambda e: e):
    nodes = ['<node text="" bounds="[0,0][1080,2400]" />']
    for e in els:
        e = edit(dict(e))
        if e is None:
            continue
        l, t = e["x"] * k * density, e["y"] * k * density
        r, b = l + e["w"] * k * density, t + e["h"] * k * density
        nodes.append('<node text="%s" bounds="[%d,%d][%d,%d]" />' % (e["text"].replace('"', "&quot;"), round(l), round(t), round(r), round(b)))
    open(path, "w").write('<?xml version="1.0"?><hierarchy>' + "".join(nodes) + "</hierarchy>")

dump(work + "/ok.xml")
dump(work + "/moved.xml", lambda e: {**e, "y": e["y"] + 10} if e["text"] == "Comfort" else e)
dump(work + "/shifted.xml", lambda e: {**e, "x": e["x"] + 9} if e["text"] == "Comfort" else e)
dump(work + "/taller.xml", lambda e: {**e, "h": e["h"] + 8} if e["text"] == "Comfort" else e)
dump(work + "/missing.xml", lambda e: e if e["text"] in ("Comfort", "Listening mode") else None)
PY
CHECK="python3 $HERE/check-page-conformance.py --metrics $METRICS --screen airpods-settings-page --density 2.625"
$CHECK --dump "$WORK/ok.xml" > /dev/null || { echo "FAIL: the dump built from the metrics must pass"; exit 1; }
for bad in moved shifted taller missing; do
  if $CHECK --dump "$WORK/$bad.xml" > /dev/null; then echo "FAIL: $bad must be rejected"; exit 1; fi
done
echo "page conformance check: 1 pass case and 4 reject cases behave"
