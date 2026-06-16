# portal-immich-frame

Turns a **Meta Portal Plus** (Android 10) into an always-on Immich photo frame.

It's a deliberately tiny native Android app: a full-screen `WebView` that displays
the **already-running** ImmichFrame web page
(`https://highlights-immich.viktorbarzin.me`, served from the cluster's
`immich` namespace). The same view is exposed two ways so it can own the Portal's
idle screen:

- **Screensaver** (`FrameDreamService`, an Android `DreamService`) — *primary*.
- **Home launcher** (`FrameActivity` with a `HOME` intent-filter) — *fallback*,
  for when a device doesn't trigger stock Android screensavers.

See [`CONTEXT.md`](CONTEXT.md) for the glossary and [`docs/adr/`](docs/adr) for
why it's built this way.

## Build

No host toolchain needed — the APK builds in a container:

```bash
scripts/build-apk.sh
# -> app/build/outputs/apk/debug/app-debug.apk
```

minSdk 28, targetSdk 29 (Meta's recommended Portal target), no third-party deps.

## Deploy (adb sideload)

The Portal is reached over USB from a machine running `adb`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configure on the Portal

Set as the **screensaver** (non-invasive, reversible — replaces the Portal's
default dream, restore by setting the component back):

```bash
adb shell settings put secure screensaver_enabled 1
adb shell settings put secure screensaver_activate_on_sleep 1
adb shell settings put secure screensaver_activate_on_dock 1
adb shell settings put secure screensaver_components me.viktorbarzin.portalframe/.FrameDreamService
```

Restore the Portal's own screensaver:

```bash
adb shell settings put secure screensaver_components com.facebook.alohaapps.launcher/com.facebook.aloha.app.home.touch.HomeDreamService
```

**App mode**: launch `me.viktorbarzin.portalframe/.FrameActivity`; exit with
double-tap, long-press, or Back. It does **not** take over the home screen.

## Configuration

The frame's content and look (albums, interval, overlays, weather) are configured
**server-side** in ImmichFrame, not in this app. That lives in the infra repo at
`stacks/immich/frame.tf`. This app only points a WebView at the URL.
