# Runbook: re-provision a Portal after a factory reset / "Erasing" wipe

**When to use:** a Portal blinked **"Erasing"** and factory-reset itself (or you
have a fresh/second device) and you need the Immich photo frame back.

## Nothing irreplaceable is lost

The Portal only ever ran a **thin WebView** pointing at the already-running
ImmichFrame page. So a wipe destroys *no* durable data:

| Thing | Where it actually lives (survives a wipe) |
|---|---|
| The photos | Immich in-cluster (`immich` ns) → Synology `Viki/nfs/immich` offsite |
| Frame content/look (albums, interval, weather) | `infra/stacks/immich/frame.tf` + Vault (`frame_api_key`, `frame_weather_api_key`) |
| The frame APK | this repo — one-command rebuild (`scripts/build-apk.sh`) |
| Signing key (for in-place `install -r`) | Vault `secret/portal-immich-frame` (`debug_keystore_b64`) |
| Assistant device token | Vault `secret/portal-assistant` (`device_token`) |

"Restore" therefore means **re-provision from these sources**, not recover
device state.

> **One thing genuinely is device state: which frame this Portal shows.** Since
> ADR-0005 the frame URL is a persisted override, so a wipe (or a "clear data")
> drops it back to the URL baked into the APK. Re-set it in Step 2 — the value is
> whichever `highlights-immich*.viktorbarzin.me` host belongs to this device's
> `infra/stacks/immich/frame-*.tf`. Skipping it is not a hard failure: the device
> falls back to the build default, so the wrong household's photos appear.

## Step 1 — On the device (needs a human physically at the Portal)

1. Power on, run through minimal first-time setup, join the **home Wi-Fi**
   (the frame endpoint is LAN-gated by source-IP allowlist since 2026-07-04, so
   it must be on the home LAN / a WireGuard site).
2. **Take the latest OTA update.** Settings → System → Software update. This is
   the update (rolled out ~June 2026) that exposes Meta's **official** developer
   access. Reboot when prompted.
3. **Enable developer mode (official path — no exploit/toolkit):**
   Settings → About → tap **Build Number** 7× → back out → **Developer options**
   → turn on **USB debugging / ADB**.
4. Plug the Portal into its USB host and **accept the "Allow USB debugging"
   prompt on the Portal screen.**

> Meta's own docs warn: *"installing sideloaded apps may put your account and
> device at risk."* Keep the device signed in and minimally modified on the
> official path — a heavily-modified / no-account device can still be reset by a
> future OTA. Do **not** re-apply the old `portal-toolkit` / CVE-2024-31317
> exploit; it was only needed before Meta shipped the native ADB toggle and gets
> patched out from under you.

## Step 2 — From the devvm (Claude can run this once ADB is reachable)

USB-host map:

| Device | Model | USB host | Portal IP |
|---|---|---|---|
| London frame | Portal+ | Viktor's Mac `192.168.8.168` | — |
| Sofia "Emo" frame | Portal Mini | `rpi-sofia` `192.168.1.10` | `192.168.1.104` |
| Sofia office frame | Portal | `rpi-sofia` `192.168.1.10` | `192.168.1.149` |

```bash
# Build (Dockerized Gradle; SDK + stable debug keystore cached in docker volumes).
# The stock APK works on every Portal — the device is pointed at its frame below,
# so there is no need to build a per-device APK.
scripts/build-apk.sh
# -> app/build/outputs/apk/debug/app-debug.apk

# Install over the USB host (example = London Mac hop; adjust host/adb path)
scp app/build/outputs/apk/debug/app-debug.apk viktorbarzin@192.168.8.168:/tmp/frame.apk
ssh viktorbarzin@192.168.8.168 '
  ADB=/Users/viktorbarzin/Library/Android/sdk/platform-tools/adb
  "$ADB" install -r /tmp/frame.apk
  # Point this device at ITS frame (see the note above). Omit --es for the London
  # default; the app confirms the URL on screen.
  "$ADB" shell am start -n me.viktorbarzin.portalframe/.FrameActivity \
    --es frameUrl https://highlights-immich-emo.viktorbarzin.me
  # idle -> screen off (not a dream), 3-min timeout:
  "$ADB" shell settings put secure screensaver_enabled 0
  "$ADB" shell settings put system screen_off_timeout 180000
  # Let the app offer its own updates (ADR-0006). Without this the startup check
  # still runs and downloads, but the install prompt never appears. Needs no
  # Portal UI, and it is the only per-device step OTA adds.
  "$ADB" shell appops set me.viktorbarzin.portalframe REQUEST_INSTALL_PACKAGES allow
'
```

> After this, a new build reaches the device on its own: the app notices, downloads
> and verifies it on the next start, then Android asks whoever is there to confirm.
> This sideload path stays the way a *wiped* or *new* device is brought up.

`adb screencap` returns black for the Portal's WebView surface — verify by
looking at the physical screen, not a screenshot.

## Step 3 — Verify

- Frame shows the Immich highlights slideshow.
- **It is the right household's slideshow** — the on-screen toast names the URL the
  device took; check it matches this Portal's frame.
- Screen powers off on idle and relights on presence.

## Restore the signing key on a fresh devvm (if the docker volume was lost)

```bash
vault kv get -field=debug_keystore_b64 secret/portal-immich-frame \
  | base64 -d > /tmp/debug.keystore
docker run --rm -v portalframe-android-home:/v -v /tmp:/in alpine \
  sh -c 'mkdir -p /v/.android && cp /in/debug.keystore /v/.android/debug.keystore'
```
