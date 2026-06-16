# Context: portal-immich-frame

A glossary for this project. Definitions only — no implementation detail.

## Glossary

### Frame
The always-on idle display experience on the Portal: photos cycling whenever the
device isn't actively in use. The goal of the whole project. A *digital photo
frame*, not an app you open.

### Portal
A Meta Portal Plus — a wall/counter smart display running Android 10. The target
device. Repurposed here as a photo frame after Meta's end-of-life of the product.

### ImmichFrame (server)
The existing, separately-deployed web service (in the cluster's `immich`
namespace) that renders an Immich library as a slideshow web page. It is the
**source of the visuals**; this project does not re-implement it.

### Highlights endpoint
The public, login-less URL where the ImmichFrame server serves the slideshow
(`highlights-immich.viktorbarzin.me`). The single thing the Portal app displays.

### Portal app
This repository: a thin native Android wrapper whose only job is to show the
Highlights endpoint full-screen on the Portal and to own the device's idle
screen. Contains no photo logic of its own.

### Screensaver mode
How the Frame owns the **idle** screen: an Android screensaver ("Dream") the
Portal shows when it's idle. Confirmed working on the Portal; exits on touch.
This is the always-on photo-frame experience.

### App mode
Opening the Frame **on demand** as a normal full-screen app from the Portal's app
list. Exits via double-tap, long-press, or Back. Distinct from the idle
Screensaver — this is the "show me the photos now" path.

> An earlier design kept a launcher/HOME-takeover fallback in case the Portal
> didn't trigger screensavers. The Portal does trigger them, so that fallback was
> dropped: the Frame never replaces the Portal's home screen.
