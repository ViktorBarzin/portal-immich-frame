# 6. Update the frames over the air, from the app itself

Date: 2026-08-15

## Status

Accepted

## Context

Getting a new build onto a Portal meant ADR-0002's adb sideload: a USB cable, a
host machine already paired with that device, and someone in the same room. That
is workable in London, where the Portal is wired to a Mac, and awkward in Sofia,
where two Portals hang on a wall and the adb host is a Raspberry Pi. In practice
it meant new builds did not get deployed.

The app is a thin WebView, so it changes rarely — but "rarely" turned out to
include changes worth shipping, like the failure panel that tells whoever walks
past why the screen is blank.

## What the device allows

An ordinary Android app cannot install a package silently. That requires either a
system-signed app or device-owner provisioning, and device owner needs a factory
reset with no accounts on the device — which the official Portal path in
`docs/runbooks/reprovision-after-factory-reset.md` rules out, since the device
stays signed in to its Meta account.

So the ceiling is: notice, download and verify automatically; the install itself
raises Android's own "Do you want to update this app?" dialog and someone taps
Update. Everything that used to need a cable and a trip is automated; one tap
remains, on a device that has a touchscreen and is normally being looked at when
the app is started.

Two preconditions per device, both one-time and both settable over adb with no
Portal UI:

- `adb shell appops set me.viktorbarzin.portalframe REQUEST_INSTALL_PACKAGES allow`
- the published build must be signed with the same key as the installed one,
  which is why the keystore is held in Vault rather than only in a docker volume
  on one machine (path in the private re-provision runbook).

## Decision

On startup, the app fetches a small JSON manifest, and if it advertises a higher
`versionCode` than the running build, downloads that APK, verifies it against the
manifest's SHA-256, and hands it to the package installer.

Startup is the trigger rather than a timer: the frame is left open for weeks, so
a start is normally a person walking up to the device — the same person who has
to tap the prompt.

The manifest URL is a build-time property (`-PupdateUrl`), empty by default. An
empty URL disables the check entirely, so a build that points nowhere simply
never self-updates.

Manifest shape:

```json
{
  "versionCode": 9,
  "versionName": "0.1.8",
  "url": "https://.../frame-v0.1.8.apk",
  "sha256": "a0191e38…"
}
```

Every check fails towards "keep showing photos": a malformed manifest, a scheme
outside http/https, a checksum mismatch, or a version that is not strictly newer
all mean do nothing. Unknown manifest fields are ignored so the format can grow
without breaking frames running older builds. The frames hang on walls in two
countries and are re-provisioned by hand, so a missed update is much cheaper
than a bad one.

## Consequences

- ADR-0002 still describes how a device is *first* provisioned and how to recover
  one; it is no longer the only way to get a new build onto a working frame.
- The signing key is now infrastructure, not a convenience. Losing it means every
  Portal needs an uninstall/reinstall by hand.
- Publishing a build means publishing two artifacts, the APK and the manifest,
  and they must agree — a manifest naming a checksum the APK does not have is a
  silently skipped update. The publishing pipeline should generate both.
- The app carries `REQUEST_INSTALL_PACKAGES`. It does not grant silent install,
  but it is the permission that lets this app ask, so it is worth stating in
  review that nothing else in the app uses it.

## Open question: where the artifacts are published

Not settled by this ADR. The app needs an unauthenticated HTTPS URL, since
embedding a token in a distributable APK is not acceptable. The candidates and
their trade-offs are recorded in the repo README; the manifest URL is a build
property precisely so this decision can be made independently of the app code.

## Notes from implementation

Two behaviours cost a round of device testing and are easy to reintroduce:

- `session.commit()` does not show the prompt. The installer replies
  asynchronously with `STATUS_PENDING_USER_ACTION` and an Intent the app must
  start. Commit against an activity PendingIntent and the download succeeds, the
  log says "offering", and nothing ever appears on screen.
- The status receiver must be a broadcast receiver, and the confirmation Intent
  needs `FLAG_ACTIVITY_NEW_TASK` — it arrives with no task of its own.

## Amendment 2026-08-16 — publishing settled, and two things the first real update taught us

**Where builds are published is no longer open.** They are GitHub releases from
this repo, built and signed by `.github/workflows/build.yml` on a `vX.Y.Z` tag,
carrying the APK and `latest.json` from the same job so the two cannot disagree.
Shipped builds point at `releases/latest/download/latest.json`, a stable redirect
to the newest release. The repo is public because the frames need an
unauthenticated URL and a token in a distributable APK is not acceptable; the
runbooks that named LAN addresses moved to the private infra repo first.

**Startup is no longer the only trigger.** The check now repeats every 6 hours
while the app runs. Startup-only was close to never in practice: a frame is
opened once and left up for weeks, so the only real trigger was a device reboot.
Repeating introduced a question the original design did not have — what if
someone declines — so a declined version backs off for 24 hours while a newer
build is still offered immediately.

**Two device prerequisites were missing from the original list**, both found by
updating a real Portal rather than an emulator:

- `settings put global package_verifier_enable 0`. The Portal ships no Play/GMS,
  so nothing on the device can answer a package-verification request; the check
  never completes and the installer aborts with
  `INSTALL_FAILED_VERIFICATION_FAILURE` *after* the download, the checksum and
  the user tapping Install. Sideloads hid this entirely, because
  `verifier_verify_adb_installs` is already `0`.
- `appops set <pkg> SYSTEM_ALERT_WINDOW allow`, so the frame can relaunch itself
  afterwards — Android stops an app to replace it and never restarts it, and
  starting an activity from `MY_PACKAGE_REPLACED` is a background activity start.

**Known and accepted (Viktor, 2026-08-16):** the frame does not start itself
after a device reboot. Someone opens it once; updates are automatic from then on.
A `BOOT_COMPLETED` receiver was considered and deliberately not built.
