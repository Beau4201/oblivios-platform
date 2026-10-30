# Keyboard autofill chips (FreeholdOS platform integration)

Two LatinIME patches that show the logins of the Passwords app as chips in the suggestion strip of the on-screen keyboard. The chip appears by itself when a login field gets focus, never takes focus, ends when the user types and returns when the field is empty again (ADR-207). The system draws the chip in another process; the keyboard receives a surface only and sets an exact size for it. Any other keyboard keeps the system list. Plan: the internal record "keyboard autofill chips plan" (not published); research: the internal record "inline autofill chip path" (not published); ADR-191 and ADR-192 in the decision log.

## Layout

- `patches/packages-inputmethods-latinime/` — two patches, `0001` and `0002`.
- `checks/` — the host checks: `AutofillChipPolicyCheck.java` (35 assertions on the policy), `run-mutations.py` (25 mutants), `check-guards.py` (static guards G1 to G6 with a self-test) and `run-host-checks.sh`, which runs all three on a LatinIME `java` directory (JDK 17 or newer, no device).
- `SOURCE.md` — base commit, what the patch changes and why it diverges from upstream, rebase notes and the verification.

## Apply

On the pinned base named in `SOURCE.md`, after `keyboard/restyle`:

    git -C packages/inputmethods/LatinIME am <path to this directory>/patches/packages-inputmethods-latinime/*.patch

Never `git am` onto a build-server branch while a build runs. The Passwords package (`passwords/`) must be in the image for the chips to have a source; without it the keyboard still builds and shows no chip.

## Scope

An `androidx.autofill` static library in the `LatinIME` module, one manifest-level opt-in (`supportsInlineSuggestions`, not the touch-exploration variant), one new view in the strip layout, and Java that reads no content of a suggestion. No new permission, exported component, network access, log call or telemetry; guards G1 to G3 fail the check on any of them.
