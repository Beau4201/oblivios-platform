#!/bin/bash
# Fails when the DeviceSettings contract vendored into AirPodsApp differs from the build tree's own
# files. The Parcel layout has no versioning (field order only), so a stale copy would break the
# binder exchange with Settings silently. Plan 6B, ADR-121.
# Usage: BUILD_HOST=<user@host> check-devicesettings-vendoring.sh   (read-only over ssh)
set -o errexit -o nounset -o pipefail
: "${BUILD_HOST:?set BUILD_HOST to <user>@<host> (from the untracked local server memory)}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
VENDORED="$HERE/app/src/com/android/settingslib/bluetooth/devicesettings"
TREE=/srv/sovereignos/platform-primary/frameworks/base/packages/SettingsLib/src/com/android/settingslib/bluetooth/devicesettings

local_sums="$(cd "$VENDORED" && sha256sum -- * | sort -k2)"
tree_sums="$(ssh -n -o BatchMode=yes "$BUILD_HOST" "cd $TREE && sha256sum -- \$(ls -p | grep -v '/\$') | sort -k2")"

if [[ -z "$local_sums" || -z "$tree_sums" ]]; then
  echo "empty file list (vendored or tree side)" >&2; exit 1
fi
if diff <(echo "$local_sums") <(echo "$tree_sums") > /dev/null; then
  echo "vendored contract identical to the build tree ($(echo "$tree_sums" | wc -l) files)"
else
  echo "DRIFT between the vendored contract and the build tree:" >&2
  diff <(echo "$local_sums") <(echo "$tree_sums") >&2 || true
  exit 1
fi
