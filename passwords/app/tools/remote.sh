#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Runs one of the server-side tools of the Passwords app from the workstation: copies the app directory to a scratch
# directory on the build server (rsync, nothing deleted outside it) and runs the tool there, niced.
#   PWM_BUILD_SSH   user@host of the build server (kept out of the repository on purpose)
#   remote.sh host-checks [TEST_CLASS...]   |   remote.sh typecheck
set -o errexit -o nounset -o pipefail
: "${PWM_BUILD_SSH:?set PWM_BUILD_SSH=user@host}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${PWM_WORK:-/srv/freeholdos/scratch/pwapp}"
cmd="$1"; shift
ssh -o BatchMode=yes "$PWM_BUILD_SSH" "mkdir -p $WORK"
rsync -rlpt --delete --exclude 'tests/test-checks.sh' -e "ssh -o BatchMode=yes" "$HERE"/ "$PWM_BUILD_SSH:$WORK/app/"
case "$cmd" in
  host-checks) ssh -o BatchMode=yes "$PWM_BUILD_SSH" "nice -n 19 bash $WORK/app/tools/run-host-checks.sh $WORK/app $*" ;;
  typecheck)   ssh -o BatchMode=yes "$PWM_BUILD_SSH" "cd $WORK && nice -n 19 bash app/tools/typecheck-on-server.sh $WORK" ;;
  *) echo "usage: remote.sh host-checks|typecheck" >&2; exit 2 ;;
esac
