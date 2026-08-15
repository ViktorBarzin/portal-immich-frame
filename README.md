# portal-immich-frame

Turns a **Meta Portal Plus** (Android 10) into an Immich photo frame.

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
the APK (`junit` is test-only). There is no CI on this repo, so the build script is
the gate: it runs `testDebugUnitTest` before `assembleDebug` and fails on a red test.

> The debug keystore lives in the `portalframe-android-home` docker volume so the
> signature is **stable** across rebuilds — that's what lets `adb install -r`
> upgrade an installed frame instead of failing
> `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Don't delete that volume.

## Deploy (adb sideload)

The Portal is reached over USB from a machine running `adb` (here, a Mac on the
Portal's LAN; the build host is remote over a tunnel):

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

The app checks for a newer build of itself **on startup** and, if one is
published, downloads it, verifies its SHA-256 and offers it to the package
installer (ADR-0006). Android then shows its own *"Do you want to update this
app?"* dialog and someone taps **Update** — an unprivileged app cannot install
silently, and the alternative (device-owner provisioning) needs a factory reset
with no accounts on the device, which the Portal path rules out.

Point a build at a manifest with `-PupdateUrl`; with no URL the check is disabled
and the build never self-updates:

```bash
updateUrl=https://…/latest.json scripts/build-apk.sh   # see the script for the property
```

The manifest is small and its unknown fields are ignored, so it can grow later:

```json
{"versionCode": 9, "versionName": "0.1.8", "url": "https://…/frame-v0.1.8.apk", "sha256": "a0191e38…"}
```

Each device needs the install app-op allowed once — an adb one-liner, no Portal
UI involved:

```bash
adb shell appops set me.viktorbarzin.portalframe REQUEST_INSTALL_PACKAGES allow
```

The published APK must be signed with the **same key** as the installed one, or
the update is rejected. That key is held in Vault; the path is in the private
re-provision runbook (see `docs/runbooks/`).

> **Where builds get published is not settled yet.** The app needs an
> unauthenticated HTTPS URL — a token embedded in a distributable APK is not an
> option — which is the whole of the decision. The candidates: a **public GitHub
> release** (works even when the homelab is down, but this repo's runbooks carry
> internal topology, so it would need those moved to the private monorepo first);
> a **LAN-only endpoint in the cluster** (nothing leaves the homelab, and the
> frames are on home LANs anyway, but it needs somewhere to serve from); or a
> **Nextcloud public link** (self-hosted, no new service, unguessable URL, but
> the link is managed by hand per release). `-PupdateUrl` exists so this can be
> decided without touching app code.

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
