#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Makes the owner's flashset: the 36 files of the previous set's remote.sha256, taken from the build
# server's product directory; a file that did not change becomes a hardlink to the previous set, a
# changed one is copied. Needs BUILD_HOST='<user>@<host>'. Usage: make-flashset.sh PREVIOUS_SET NEW_SET
set -o errexit -o nounset -o pipefail
: "${BUILD_HOST:?set BUILD_HOST='<user>@<host>'}"
PREV="${1:?previous flashset directory}"
NEW="${2:?new flashset directory}"
PRODUCT=/srv/sovereignos/platform-primary/out-lynx/target/product/lynx
[ -d "$PREV" ] || { echo "no such directory: $PREV"; exit 1; }
[ -e "$NEW" ] && { echo "$NEW exists, refusing to touch it"; exit 1; }
mkdir -p "$NEW"
NAMES="$(awk '{print $2}' "$PREV/remote.sha256" | tr '\n' ' ')"
ssh -n -o BatchMode=yes "$BUILD_HOST" "cd $PRODUCT && sha256sum $NAMES" > "$NEW/remote.sha256"
while read -r sha name; do
  if [ -e "$PREV/$name" ] && [ "$(sha256sum "$PREV/$name" | cut -d' ' -f1)" = "$sha" ]; then
    ln "$PREV/$name" "$NEW/$name"
  else
    scp -q -o BatchMode=yes "$BUILD_HOST:$PRODUCT/$name" "$NEW/$name" < /dev/null
  fi
done < "$NEW/remote.sha256"
[ -e "$PREV/fastboot-info.txt" ] && [ ! -e "$NEW/fastboot-info.txt" ] && ln "$PREV/fastboot-info.txt" "$NEW/fastboot-info.txt"
(cd "$NEW" && sha256sum -c remote.sha256 | grep -c ': OK$')
