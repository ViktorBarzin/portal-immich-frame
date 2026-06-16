# 1. Wrap the existing ImmichFrame web page rather than build a native client

Date: 2026-06-16

## Status

Accepted

## Context

The goal is an always-on Immich photo frame on a Meta Portal Plus (Android 10).
An ImmichFrame **server** is already deployed in the cluster and renders the
slideshow as a login-less web page (the Highlights endpoint). Three ways to get
that onto the Portal were considered:

1. Build a native Android Immich client (talk to the Immich REST API directly,
   render the slideshow natively).
2. Sideload ImmichFrame's own prebuilt Android client and point it at our server.
3. Build a thin native app that displays the existing Highlights web page in a
   WebView.

Portal constraints: Android 10 (no Android-12+ screensaver APIs; ships without
Google Play Services), an unusual form factor, and end-of-life status.

## Decision

Build a thin native WebView wrapper (option 3) around the existing Highlights
endpoint.

## Consequences

- **Maximum reuse**: all photo logic (sources, transitions, clock, weather,
  layout) stays in the one place it already exists — the ImmichFrame server.
  The app has zero photo code and near-zero dependencies.
- **Guaranteed Portal compatibility**: we target API 29 ourselves and rely only
  on the platform WebView + DreamService, so no GMS or newer-Android assumptions.
- **One config surface**: changing the frame's content/look is a server-side
  change, never an app rebuild + redeploy.
- **Trade-off**: we depend on the Highlights endpoint being reachable from the
  Portal (network/WAN). Mitigated by self-healing reload-on-error in the WebView.
- Rejected option 1 (native client) as needless re-implementation; rejected
  option 2 (prebuilt client) because its screensaver path needs Android 12+, it's
  a black box of unverified Portal compatibility, and it isn't ours to shape.
