# 5. The frame URL is runtime configuration, not a compile-time constant

Date: 2026-08-06

## Status

Accepted (relaxes, but does not remove, the build-time `-PframeUrl` knob from
commit `14e82a0`)

## Context

Each Portal points at its own ImmichFrame deployment — Viktor's London frame,
Emo's Sofia frame, and now a third in Вълчедръм. Until now the URL was a
**compile-time constant**: `buildConfigField("String", "FRAME_URL", …)` baked at
build time and read as `BuildConfig.FRAME_URL` in three places in `FrameView`.

That made "add a portal" mean "recompile the app", which has cost real time:

- Onboarding the third portal stalled outright, because the person doing it could
  build nothing — they had no access to this repository. The fallback attempted was
  to patch the URL string inside the *already-compiled* `classes.dex` and re-sign
  the APK. That work was correctly abandoned: byte-patching a compiled binary and
  re-signing it with a different key is indistinguishable from malicious app
  repackaging, and it produces an APK that can no longer upgrade the installed one
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- A per-device APK is a per-device artifact to build, name, track, and not mix up.
  Nothing about a wall-mounted photo frame justifies that.

The alternative considered was routing: keep one baked URL and have Traefik pick
the frame by client source IP. Rejected — it moves per-device configuration into
shared cluster routing, needs a DHCP reservation per Portal, and makes "which
frame is this device on?" unanswerable from the device itself.

## Decision

The URL is resolved at **runtime**, with the baked value as the default:

    persisted override (SharedPreferences)  ??  BuildConfig.FRAME_URL

Set it over adb, on the launch intent:

    adb shell am start -n me.viktorbarzin.portalframe/.FrameActivity \
      --es frameUrl https://highlights-immich-milka.viktorbarzin.me

    # back to the built-in default
    adb shell am start -n me.viktorbarzin.portalframe/.FrameActivity --es frameUrl default

Structure: `FrameUrl` holds the validation as a pure, Android-free object so it is
unit-testable on the JVM; `FrameUrlStore` is a thin read/write over
SharedPreferences with no judgement in it. `FrameView` resolves per load rather
than caching, so a re-point takes effect on the next reload without a restart.

`sanitize()` **allow-lists** `http`/`https` rather than blocking known-bad
schemes. The output is loaded into a JavaScript-enabled kiosk WebView and
persisted across reboots, so `javascript:` would execute and `file:`/`content:`
would read local storage. A rejected value leaves the running frame untouched and
says so on screen — silently ignoring a typo looks identical to a broken frame.

`-PframeUrl` is **kept**. It now sets the default a build ships with, which is
still the right thing for a device that should work the moment it is installed.

## Consequences

- Adding a portal is a one-line adb command against the stock APK. No rebuild, no
  per-device artifact, and no repository access needed to commission a device.
- Existing devices are unaffected: with no override stored, resolution falls
  through to the same `BuildConfig.FRAME_URL` they already used. APKs built from
  before this change keep working as-is.
- The URL is now device state, so it survives app upgrades but not a factory reset
  or a data clear — re-run the adb command (see the reprovision runbook).
  `allowBackup="false"` means it is deliberately not restored from a cloud backup.
- `FrameUrlStore` itself is not unit-tested: it needs a `Context`, and Robolectric
  would be the repo's first heavyweight test dependency. It is kept trivial enough
  to read instead — all the behaviour worth testing lives in `FrameUrl`.
- Writes use `commit()`, not `apply()`: this is a one-shot config action that may
  be followed immediately by a power-cycle, so it must have landed before the
  on-screen confirmation appears.
