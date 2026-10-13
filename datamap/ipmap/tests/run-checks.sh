#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# The checks of the ipmap package that need no network: the tool against its fixtures, the anchor rule, and that the
# committed fixture files are what the tool makes. The Java reader is checked by service/tools/run-host-checks.sh.
set -o errexit -o nounset -o pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
python3 -I "$HERE/ipmap_check.py"
python3 -I "$HERE/anchors_check.py"
