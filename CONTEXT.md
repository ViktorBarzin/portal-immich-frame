# Context: portal-immich-frame

A glossary for this project. Definitions only — no implementation detail.

## Glossary

### Frame
The Immich photo-frame experience on the Portal: photos cycling on the screen.
The goal of the project. On the Portal it's realized by leaving the Portal app
open (see App mode) — a true screensaver isn't available (see Screensaver).

### Portal
A Meta Portal Plus — a wall/counter smart display running Android 10. The target
device, repurposed as a photo frame after Meta's end-of-life of the product. Its
camera **presence** detection governs when the screen is awake.

### ImmichFrame (server)
The existing, separately-deployed web service (in the cluster's `immich`
namespace) that renders an Immich library as a slideshow web page. It is the
**source of the visuals**; this project does not re-implement it.

### Highlights endpoint
The login-less URL where the ImmichFrame server serves the slideshow
(`highlights-immich.viktorbarzin.me`). The single thing the Portal app displays.
**LAN-only since 2026-07-04**: the hostname resolves publicly to the cluster's
*internal* ingress IP, and a source-IP allowlist rejects everything outside
the home LANs / WireGuard sites (infra repo,
`docs/plans/2026-07-04-immich-frame-lan-only-design.md`). The Portal needed no
change — its baked-in URL keeps resolving and routing from any home network.

### Portal app
This repository: a thin native Android wrapper whose only job is to show the
Highlights endpoint full-screen on the Portal. Contains no photo logic of its own.

### App mode
The supported way to run the Frame on the Portal: open the app and leave it open.
It does not force the screen on, so the Portal's presence detection keeps it lit
while someone's there and sleeps it on idle. Exits via double-tap, long-press, or
Back. Never takes over the home screen.

### Screensaver (unavailable on Portal)
The app ships a `DreamService` so it *could* be a stock Android screensaver, but
the Portal's SuperFrame presence manager forcibly stops any third-party dream the
instant it starts. So on Portal the Frame is App mode, not a screensaver. (The
Dream still works on stock-Android devices.)

> Earlier designs tried screensaver-first with a HOME-launcher fallback. Both
> were dropped once Portal's screensaver block was found: the Frame runs as an
> ordinary open app and never replaces the Portal's home screen.
