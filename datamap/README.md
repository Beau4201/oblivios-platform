# datamap

The Live Data Map (ADR-157): a collector in `system_server`, a classifier file in the system image and the privileged app `DataMapApp`.

| Directory | Holds |
| --- | --- |
| `patches/` | The AOSP changes as `git format-patch` files, per tree (`frameworks-base`, `system-sepolicy`, `vendor-adevtool`), applied in order with `git am`. A fix is a new patch on top, never an edit of an earlier one. |
| `service/tests/host/`, `service/tools/` | Host checks and mutation check of the pure server classes, and the tools that fetch baselines and type-check the Android classes on the build server (`typecheck-on-server.sh`). |
| `app/` | `DataMapApp` (`Android.bp`, manifest, `src/`, `res/`, `tools/`, `tests/`); `tools/typecheck-on-server.sh` compiles resources, AIDL and Java against the system SDK stubs. |
| `classifier/` | The classifier module, the builder, its tests and (later) the data with its sources. |
| `SOURCE.md` | Baselines, one row per patch, review and landing records. |

## Checks

```bash
W=$(mktemp -d)
datamap/service/tools/materialize-core.sh $W
datamap/service/tools/run-host-checks.sh $W | tail -2
python3 datamap/service/tools/run-mutations.py $W | tail -1
(cd datamap/app && LAND_BIN=res/raw/land.bin tools/run-host-checks.sh | tail -1 && python3 tools/run-mutations.py | tail -1 && python3 -I tests/logos_mutations.py | tail -1 && tools/check-resources.py && tools/check-static.sh)
datamap/classifier/tests/run-builder-checks.sh $W | tail -1
python3 datamap/classifier/tests/run-builder-mutations.py $W | tail -1
```

Expected: `all host checks passed`, `39 of 39 mutations killed`, `all app host checks passed` (this includes the logo tool checks), `96 of 96 mutations killed`, `44 of 44 mutations killed` (the logo tool), `static checks passed`, `all builder checks passed`, `11 of 11 mutations killed`.

## Type-check on the build server (changes nothing in the tree)

```bash
D=<a new directory under the scratch area of the build server>
tar -C <worktree> --exclude=.git -cf - frameworks | ssh <build server> "mkdir -p $D && tar -C $D -xf -"
ssh <build server> "bash -s -- $D" < datamap/service/tools/typecheck-on-server.sh     # frameworks/base classes
tar --exclude=tests --exclude=tools -cf - app | ssh <build server> "mkdir -p $D && tar -C $D -xf -"
ssh <build server> "bash -s -- $D" < datamap/app/tools/typecheck-on-server.sh         # the app
```

Neither replaces the build: the policy compile (neverallows) and the Soong rules are proven only by `m` (Task 9 of the plan).
