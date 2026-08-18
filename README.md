# portal-immich-frame

Turns a **Meta Portal** into an Immich photo frame.

The fleet is mixed, and it matters for anything version-gated: the London
Portal+ (`aloha`) reports **Android 9 / API 28**, the Sofia Portal Mini
(`omni`) reports **Android 10 / API 29** (both read from
`ro.build.version.release`, 2026-08-16). `minSdk 28` covers both. Earlier docs
here said "Android 10" throughout — that was only ever true of the Mini.

A deliberately tiny native Android app: a full-screen `WebView` that displays the
**already-running** ImmichFrame web page (`https://highlights-immich.viktorbarzin.me`,
served from the cluster's `immich` namespace). It holds no photo logic of its own.

> **Access (since 2026-07-04):** the endpoint is LAN-only — the hostname
> resolves publicly to the internal ingress IP and a source-IP allowlist
> gates it to the home LANs / WireGuard sites. No app change was needed;
> the same baked-in URL keeps working from home networks.

**How it runs:** you leave the app **open** — it *is* the frame. It deliberately
does **not** force the screen on, so the Portal's camera presence keeps it lit
while someone's in the room and powers it off on idle. Exit anytime with
double-tap, long-press, or Back.

> A `DreamService` screensaver (`FrameDreamService`) is included, but **Meta
> Portal blocks third-party screensavers** — its SuperFrame presence manager
> stops any custom dream the instant it starts (`dream_stopped_before_timeout`).
> So the always-open-app model is the supported path on Portal; the Dream class is
> kept only for stock-Android devices. See [`docs/adr/`](docs/adr).

See [`CONTEXT.md`](CONTEXT.md) and [`docs/adr/`](docs/adr) for the why.

## Build

No host toolchain needed — the APK builds in a container (Dockerized Gradle; the
SDK, a Gradle dist, and a stable debug keystore are cached in docker volumes):

```bash
scripts/build-apk.sh          # runs the unit tests, then builds
# -> app/build/outputs/apk/debug/app-debug.apk
```

minSdk 28, targetSdk 29 (Meta's recommended Portal target), no third-party deps in
the APK (`junit` and `org.json` are test-only). This script is the local gate — it
runs `testDebugUnitTest` before `assembleDebug` and fails on a red test — and CI
runs the same tests again on a tag before publishing a release (see Updates).

> The debug keystore lives in the `portalframe-android-home` docker volume so the
> signature is **stable** across rebuilds — that's what lets `adb install -r`
> upgrade an installed frame instead of failing
> `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Don't delete that volume.

## Deploy (adb sideload)

The Portal is reached over USB from a machine running `adb` (here, a Mac on the
Portal's LAN; the build host is remote over a tunnel). A Portal can also be
reached **over the network** once `adb tcpip 5555` has been issued over USB —
convenient, but it lasts only until the device reboots (the persistent property
needs root, which these devices do not have). USB adb itself does come back on
its own after a reboot, ~60s, so nothing is lost permanently. Access paths and
reboot behaviour: `infra/docs/runbooks/provision-portal.md`.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n me.viktorbarzin.portalframe/.FrameActivity
```

> `adb screencap` **does** capture the frame on the London Portal+ — verified
> 2026-08-15, a full-colour photo with the clock overlay. This previously
> returned black for the hardware-accelerated WebView surface, so if you get a
> black image that is the known older behaviour rather than a broken frame;
> check the physical screen in that case.
>
> Since v0.1.8 a black capture is more informative than it was: the failure panel
> is a native view, so it *does* screenshot. A capture showing photos means the
> frame is working, a capture showing the panel says what is wrong, and a black
> one means neither — the screen is off, or you have hit the old WebView-surface
> behaviour.

## Configure the Portal to sleep on idle

The app doesn't keep the screen on, so tell the Portal to power off on idle rather
than dream:

```bash
adb shell settings put secure screensaver_enabled 0      # idle -> screen off (not a dream)
adb shell settings put system screen_off_timeout 180000  # 3 min
```

## Updates

The app checks for a newer build of itself **at launch and then every 6 hours**,
and if one is published it downloads it, verifies its SHA-256 and offers it to
the package installer (ADR-0006). Android then shows its own *"Do you want to
update this app?"* dialog and someone taps **Update** — an unprivileged app
cannot install silently, and the alternative (device-owner provisioning) needs a
factory reset with no accounts on the device, which the Portal path rules out.

After installing, the frame **brings itself back**: Android stops an app to
replace it and never restarts it, which on a wall display would otherwise leave
the Portal on its launcher. Declining an update backs that version off for 24h,
so a repeating check cannot turn the frame into a dialog that keeps returning.

Not covered: the frame does not start itself after a **device reboot** — someone
opens it once, and updating is automatic from then on (accepted 2026-08-16).

Point a build at a manifest with `-PupdateUrl`; with no URL the check is disabled
and the build never self-updates:

```bash
updateUrl=https://…/latest.json scripts/build-apk.sh   # see the script for the property
```

The manifest is small and its unknown fields are ignored, so it can grow later:

```json
{"versionCode": 9, "versionName": "0.1.8", "url": "https://…/frame-v0.1.8.apk", "sha256": "a0191e38…"}
```

Each device needs three one-time settings — all adb one-liners, no Portal UI
involved (`infra/scripts/provision-portal.sh` applies them):

```bash
# or the prompt never appears
adb shell appops set me.viktorbarzin.portalframe REQUEST_INSTALL_PACKAGES allow
# or the frame cannot bring itself back afterwards (background activity start)
adb shell appops set me.viktorbarzin.portalframe SYSTEM_ALERT_WINDOW allow
# or the install aborts with INSTALL_FAILED_VERIFICATION_FAILURE: the Portal
# ships no Play/GMS, so nothing on it can answer a verification request
adb shell settings put global package_verifier_enable 0
```

The published APK must be signed with the **same key** as the installed one, or
the update is rejected. That key is held in Vault; the path is in the private
re-provision runbook (see `docs/runbooks/`).

Builds are published as **GitHub releases** from this repo, by
`.github/workflows/build.yml` on a `vX.Y.Z` tag. Releases carry the APK and a
`latest.json`, produced by the same job so they cannot disagree, and CI refuses
to publish if the APK is not signed with the frames' key. Shipped builds point
at `releases/latest/download/latest.json` — a stable redirect to the newest
release, so a build published today keeps finding its successors.

That is why this repo is public: the frames need an unauthenticated HTTPS URL,
and a token embedded in a distributable APK is not an option. The operational
runbooks, which name LAN addresses and hosts, live in the private infra repo
instead (see `docs/runbooks/`).

## Configuration

### Which frame this device shows

Set at **runtime** over adb — no rebuild, and the same stock APK works on every
Portal (ADR-0005):

```bash
# point this device at a frame
adb shell am start -n me.viktorbarzin.portalframe/.FrameActivity \
  --es frameUrl https://highlights-immich-milka.viktorbarzin.me

# back to the built-in default
adb shell am start -n me.viktorbarzin.portalframe/.FrameActivity --es frameUrl default
```

The app confirms on screen which frame took effect. It persists across reboots and
app upgrades (but not a factory reset / data clear — see the reprovision runbook).
Only `http`/`https` URLs are accepted; anything else is refused out loud and leaves
the running frame alone.

Resolution order: **persisted override → the URL baked in at build time.** So a
device with no override behaves exactly as it did before this knob existed.

`-PframeUrl=<url>` still works and now sets the *default* a build ships with:

```bash
FRAME_URL=https://highlights-immich-emo.viktorbarzin.me scripts/build-apk.sh
```

Prefer the runtime knob for a new device; use the build-time default when you want
an APK that points somewhere specific the moment it is installed.

### What the frame shows

The frame's **content and look** (albums, interval, overlays, weather) are
configured **server-side** in ImmichFrame, not in this app — in the private infra
repo, which also holds the API keys. This app only points a WebView at the URL.

## When it can't show photos

A blank frame used to be a black rectangle: identical whether the server was down,
the Portal had joined a different Wi-Fi, or the screen was simply asleep. The app
now puts the deciding facts on the glass instead — the frame URL, this device's own
IPv4 address and default gateway, and a retry count so a stuck frame reads
differently from a retrying one.

Three things can go wrong, and each says something different:

| What happened | Headline |
|---|---|
| Nothing answered — no route, no DNS, refused, timed out | *Can't reach the frame*, with the WebView's own `net::ERR_…` |
| The server answered 403 — the endpoint is limited to the home LANs | *Not allowed from this network* |
| The page loaded, then stopped asking for photos | *The frame stopped showing photos*, with how long since the last one |

The first two are load failures, reported by WebView callbacks. The third needs
watching for, and is the one that kept getting missed: the frame page carries no
`Cache-Control`, so a Portal launched on a network with no route home can render a
**cached** shell, which is a perfectly successful navigation with nothing behind it.
Two things address that — the WebView no longer serves the frame from its HTTP cache
(`LOAD_NO_CACHE`, ~74 KB per load), and `FrameHealth` treats three minutes without a
`/api/` request as a stall. The first stall is answered with a quiet reload, since
most heal and a panel flashing on a wall is worse than the moment; a second stall in
a row puts the panel up and leaves it there until photos actually return.
