# Passwords app — Source Provenance

ADR-185. The local password manager of FreeholdOS: the app `PasswordsApp` (`app/`, Kotlin and Compose), the prebuilt Rust core
`libvault_core` (`core/`, built from `freeholdos-services/vault-core`, see `core/SOURCE.md`), the autofill service and the credential
provider service of the app, and two patches that make it part of the image and its default provider.

State: **authored and exported; not landed on the build server, not built as a module, not on a device.**

## Patches

Authored in a disposable worktree (`.worktrees/passwords-authored/`, gitignored) that holds only the fetched files, read-only from the
build-server trees, as the repository rules require; exported with `git format-patch`.

| Tree | Baseline HEAD | Blob of the modified file at that HEAD | Patch | Why it diverges from upstream |
| --- | --- | --- | --- | --- |
| `vendor/adevtool` | `e4aca7568bc7` | `config/mk/google_devices/common/device-common.mk` `fe456150e5c6` | `vendor-adevtool/0001` | `PRODUCT_PACKAGES += PasswordsApp` on every Pixel target, in the common file like Guardian, AirPods and the Data Map. The module is defined in `vendor/freeholdos/passwords`. |
| `frameworks/base` | `3a3e6af27a53` | `core/res/res/values/config.xml` `9124ee97ec80` | `frameworks-base/0001` | Three resource values name the app as default: `config_defaultAutofillService`, `config_enabledCredentialProviderService` and `config_primaryCredentialProviderService` (read in `SettingsProvider.java` and `AutofillManagerServiceImpl.java` of the same tree). |

Both patches were checked with `git apply --check` against the live trees on 2026-10-22; nothing was changed there. The `vendor/adevtool` patch was authored on `fe4c53c7b8c8` first and re-authored on 2026-10-22 on `e4aca7568bc7` (the Compartments commit that added `exitd` and `ExitApp` to the same file moved the hunk); a later commit in that tree needs the same again.

## What landing needs, in this order

1. No build running and the trees clean (`git status` in `vendor/adevtool`, `frameworks/base`, `vendor/freeholdos`).
2. `vendor/freeholdos/passwords` made as its own repository from `app/` and `core/` of this package (not `tests/` and `tools/`), the
   `sha256sum` of every file equal to the package, like `vendor/freeholdos/datamap`.
3. `git am` of the two patches. A commit in `vendor/adevtool` fails every build in one second until the pin in
   `vendor/google_devices/{lynx,stallion}/adevtool-version-check.mk` names the new HEAD; update both pins.
4. The module build (`m PasswordsApp`, not an image) with `OFFICIAL_BUILD=true` and the ccache variables in the same command, and the
   owner's yes. The first Soong parse of `core/Android.bp` and `app/Android.bp` happens there; neither has been parsed by Soong yet
   (`bpfmt` accepts both).
5. `check-required-packages.sh` gets `PasswordsApp.apk`, and then the next image check requires it.

Until step 3, the next image of the build server is the same as without this package. The defaults the patch sets apply when the secure
setting is still empty (a new user or a new install): on an existing device the owner picks the app once under Settings, Passwords and
accounts.

## Not shipped

`app/tests/` (host checks, guard tests) and `app/tools/` (the checks, the type check, the mutation run) stay in this repository.
