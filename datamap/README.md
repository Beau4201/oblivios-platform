# datamap

The Live Data Map (ADR-157): a collector in `system_server`, a classifier file in the system image and the privileged app `DataMapApp`.

| Directory | Holds |
| --- | --- |
| `patches/` | The AOSP changes as `git format-patch` files, per tree (`frameworks-base`, `system-sepolicy`, `vendor-adevtool`), applied in order with `git am`. |
| `service/tests/host/`, `service/tools/` | Host checks and mutation check of the pure server classes, and the tools that fetch baselines and type-check on the build server. |
| `app/` | `DataMapApp` (`Android.bp`, manifest, `src/`, `res/`, `tools/`, `tests/`). |
| `classifier/` | The classifier module, the builder, its tests and (later) the data with its sources. |
| `SOURCE.md` | Baselines, one row per patch, review and landing records. |

## Checks

```bash
W=$(mktemp -d)
datamap/service/tools/materialize-core.sh $W
datamap/service/tools/run-host-checks.sh $W | tail -2
python3 datamap/service/tools/run-mutations.py $W | tail -1
(cd datamap/app && LAND_BIN=res/raw/land.bin tools/run-host-checks.sh | tail -1 && python3 tools/run-mutations.py | tail -1 && tools/check-resources.py && tools/check-static.sh)
datamap/classifier/tests/run-builder-checks.sh $W | tail -1
python3 datamap/classifier/tests/run-builder-mutations.py $W | tail -1
```

Expected: `all host checks passed`, `19 of 19 mutations killed`, `all app host checks passed`, `26 of 26 mutations killed`, `static checks passed`, `all builder checks passed`, `3 of 3 mutations killed`.
