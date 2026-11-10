#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Static rules of the Data Map app (MAP-010 wording, MAP-011 no network or export path, MAP-013 no network
# permission and no background component). A match is a failure; the patterns are deliberately broad.
# usage: check-static.sh [APP_DIR]
set -o errexit -o nounset -o pipefail
APP="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
bad=0
fail() { echo "FAIL: $1"; bad=1; }

# MAP-013: the manifest holds no network permission and no component but the one activity.
grep -qE 'android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|CHANGE_NETWORK_STATE|ACCESS_WIFI_STATE)' "$APP/AndroidManifest.xml" \
  && fail "a network permission in the manifest"
grep -qE '<(service|receiver|provider)\b' "$APP/AndroidManifest.xml" && fail "a service, receiver or provider in the manifest"
[ "$(grep -c '<activity' "$APP/AndroidManifest.xml")" = "1" ] || fail "the manifest must declare exactly one activity"
grep -qE 'android:process=|android:allowBackup="true"' "$APP/AndroidManifest.xml" && fail "its own process or backup allowed"

# MAP-011: no socket, URL, share or file export in the code.
if grep -rnE 'java\.net\.|javax\.net|okhttp|HttpURLConnection|WebView|ACTION_SEND|ACTION_VIEW|createChooser|openFileOutput|FileOutputStream|getExternalFilesDir|MediaStore|Runtime\.getRuntime|ProcessBuilder|DownloadManager|ConnectivityManager|ClipboardManager' \
     "$APP/src" --include='*.java' --exclude-dir=datamap_generated | grep -v 'src/com/android/internal/datamap/'; then
  fail "a network, share, export or process API in the sources"
fi

# MAP-010: the wording rule. Two apps "could be linked"; nothing says they are.
if grep -rniE '(are|is|were|was) (linked|connected to each other|the same (person|user))|linked together|tracks you' \
     "$APP/res/values" "$APP/src" --include='*.xml' --include='*.java'; then
  fail "the wording rule is broken: nothing may say that two apps are linked"
fi
# ADR-160 / spec 7: nothing the user reads says that companies or apps exchange, share or send data to each other.
if grep -rniE 'exchang|(shar(e|es|ed|ing)|send(s|ing)?|sold|sell(s|ing)?) (your |their |the )?(data|information)' \
     "$APP/res/values" --include='*.xml'; then
  fail "the wording rule is broken: nothing may say that data is exchanged, shared or sent"
fi
grep -q 'Only connections Atlas could see are listed. There may be more.' "$APP/res/values/strings.xml" \
  || fail "the note that a connection which is not listed is not proof of none is gone"
# Review of 2026-10-13: a Switch restores its checked state by id, so two with one id swap their values on a theme change
# and the listeners then change Collect and Keep for without a touch. A Switch keeps no saved state and has no shared id.
grep -q 'setSaveEnabled(false)' "$APP/src/org/oblivios/datamap/Controls.java" || fail "the Switch must not save its state (C1)"
grep -q 'R.id.at_switch' "$APP/src/org/oblivios/datamap/Controls.java" && fail "the Switches share one id (C1)"
# Choosing a place or an app leaves full screen (spec 3): the flights must take the globe back to its normal size.
grep -E 'flyTo\(.*Motion\.FLY_(PLACE|APP)\)' "$APP/src/org/oblivios/datamap/GlobeView.java" | grep -qv '0f, Motion' \
  && fail "a flight to a place or an app must pass 0f as full screen (I1)"
# The land is prepared on its own thread; behind the snapshot read it kept the empty state on screen (I3).
grep -q 'mLand.execute' "$APP/src/org/oblivios/datamap/MainActivity.java" || fail "the land needs its own executor (I3)"
# Device round 2026-10-13: the full-screen button is a FrameLayout around its icon; casting the button itself to an
# ImageView killed the activity in onCreate. The icon is the child of the button.
grep -q '(ImageView) ((FrameLayout) ((LinearLayout) mFullControl).getChildAt(0)).getChildAt(0)' "$APP/src/org/oblivios/datamap/MapTab.java" \
  || fail "mFullIcon must be taken from inside the button FrameLayout (device crash in MapTab.<init>)"
# Owner, 2026-10-13: an app's icon is the system's own icon, with no theme tint, no full bleed and no clipping of ours.
if grep -rnE 'ThemedIcons|AppIcons|getMonochrome' "$APP/src" --include='*.java'; then
  fail "app icons must be the system's own icons (no themed or bled icons)"
fi
# ADR-161: a server place says where a server lies, never where a company is based, and the app has no way to open a link.
if grep -niE '<string name="(hosted_in|hosted_in_title|server_[a-z_]+|settings_server_history[a-z_]*|operators_more)">[^<]*(based|headquarter|located|home country)' "$APP/res/values/strings.xml"; then
  fail "a server text says where a company is based or located"
fi
grep -q 'name="hosted_in">Hosted in ' "$APP/res/values/strings.xml" || fail "the server place text must read 'Hosted in'"
grep -q 'IP Geolocation by DB-IP' "$APP/res/values/strings.xml" || fail "the attribution to DB-IP is gone (CC BY 4.0)"
# The Binder fields of a server place reach the model (SnapshotMapper is Android code, so no host check reads it), the
# marker of a server place is hollow, and the three kinds with no place keep their own line in the list.
for f in serverKind serverCountry serverCity serverLat serverLon serverOperator; do
  grep -q "^    [a-zA-Z]* $f;" "$APP/src/com/android/internal/datamap/DataMapEntry.aidl" || fail "DataMapEntry.aidl has no $f"
  grep -qE "m\.$f = (nz\()?e\.$f\)?;" "$APP/src/org/oblivios/datamap/SnapshotMapper.java" || fail "SnapshotMapper does not copy $f"
done
[ "$(grep -c 'if (!hollow) {' "$APP/src/org/oblivios/datamap/GlobeView.java")" -eq 2 ] || fail "a server place must be drawn as a ring with nothing in it"
grep -q 'mPlaces.get(i).lead.server);' "$APP/src/org/oblivios/datamap/GlobeView.java" || fail "the marker of a server place must be hollow"
for k in SRV_UNCERTAIN SRV_ANYCAST SRV_STEERED; do
  grep -q "case MapModel.$k:" "$APP/src/org/oblivios/datamap/Texts.java" || fail "Texts.serverLine has no line for $k"
done
[ "$bad" -eq 0 ] && echo "static checks passed"
exit "$bad"
