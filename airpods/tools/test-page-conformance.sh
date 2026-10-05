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

def dump(path, edit=lambda e: e, added=None):
    nodes = ['<node text="" bounds="[0,0][1080,2400]" />']
    for e in els + ([added] if added else []):
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
# A row the mock does not have, placed between "Comfort" and the next matched text, which is pushed down by 30:
# the spacing of that pair is not compared, everything else still is.
comfort = next(e for e in els if e["text"] == "Comfort")
below = [e["y"] for e in els if e["y"] > comfort["y"]]
dump(work + "/added-row.xml", lambda e: {**e, "y": e["y"] + 30} if e["y"] > comfort["y"] else e,
     {**comfort, "text": "Added row", "y": comfort["y"] + 1})
dump(work + "/added-row-moved.xml", lambda e: {**e, "y": e["y"] + 30} if e["y"] > comfort["y"] and e["text"] != "Automatic ear detection"
     else {**e, "y": e["y"] + 40} if e["text"] == "Automatic ear detection" else e, {**comfort, "text": "Added row", "y": comfort["y"] + 1})
# A mock row the page dropped: its text is gone and everything below moves up by 22, so the spacing of the pair
# around the gap differs by design and is not compared.
gone = next(e for e in els if e["text"] == "Pause when you take one out")
dump(work + "/dropped-row.xml", lambda e: None if e["text"] == gone["text"] else {**e, "y": e["y"] - 22} if e["y"] > gone["y"] else e)
dump(work + "/missing.xml", lambda e: e if e["text"] in ("Comfort", "Listening mode") else None)
PY
CHECK="python3 $HERE/check-page-conformance.py --metrics $METRICS --screen airpods-settings-page --density 2.625"
$CHECK --dump "$WORK/ok.xml" > /dev/null || { echo "FAIL: the dump built from the metrics must pass"; exit 1; }
$CHECK --dump "$WORK/added-row.xml" > /dev/null || { echo "FAIL: a row added since the mock must not be a violation"; exit 1; }
$CHECK --dump "$WORK/dropped-row.xml" > /dev/null || { echo "FAIL: a row dropped since the mock must not be a violation"; exit 1; }
for bad in moved shifted taller missing added-row-moved; do
  if $CHECK --dump "$WORK/$bad.xml" > /dev/null; then echo "FAIL: $bad must be rejected"; exit 1; fi
done
echo "page conformance check: 3 pass cases and 5 reject cases behave"
