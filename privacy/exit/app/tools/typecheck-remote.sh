#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Copies the app directory to a scratch directory on the build server and type-checks it there.
#   EXIT_BUILD_SSH   user@host of the build server (kept out of the repository on purpose)
set -o errexit -o nounset -o pipefail
: "${EXIT_BUILD_SSH:?set EXIT_BUILD_SSH=user@host}"
APP="$(cd "$(dirname "$0")/.." && pwd)"
SCRATCH="${EXIT_APP_SCRATCH:-/srv/freeholdos/exit-app-typecheck}"
ssh -o BatchMode=yes "$EXIT_BUILD_SSH" "mkdir -p $SCRATCH/app"
rsync -rlpgoD --checksum --delete --exclude '.typecheck-*' -e "ssh -o BatchMode=yes" "$APP"/ "$EXIT_BUILD_SSH:$SCRATCH/app/"
ssh -o BatchMode=yes "$EXIT_BUILD_SSH" "bash $SCRATCH/app/tools/typecheck-on-server.sh $SCRATCH"
