# 3. Run as an always-open app — Portal blocks third-party screensavers

Date: 2026-06-16

## Status

Accepted (revises the screensaver-first/launcher-fallback approach).

**Note:** the power-management sub-decision below — *not* holding the screen on,
letting the Portal sleep it on idle — was reversed by **ADR-0004** (v0.1.5). The
Portal's power policy darkened the frame regardless; the screen is now held on in
the app. The always-open-app decision itself stands.

## Context

The goal is an always-on Immich frame on a Meta Portal Plus (Android 10). The
intended mechanism was an Android screensaver (`DreamService`) so photos appear
when the Portal is idle. On-device testing showed:

- The DreamService renders correctly when force-started, BUT the Portal's
  SuperFrame/presence system **stops any third-party dream immediately** (logcat:
  `aloha.UserPresenceManager` → `event_type: dream_stopped_before_timeout`); the
  Portal reclaims the screensaver slot for its own SuperFrame, which can't point
  at Immich.
- A HOME-launcher takeover (the original fallback) is invasive (Meta warns it may
  not cleanly revert) and Portal may still dream its SuperFrame over it.
- `adb screencap` returns black for Portal's hardware WebView surface, so
  rendering can only be verified by eye — this misled debugging for several
  iterations.

## Decision

Run the frame as an **always-open foreground app** (`FrameActivity`). Do **not**
hold the screen on (`FLAG_KEEP_SCREEN_ON` removed): the Portal's camera presence
keeps the screen lit while someone is present and powers it off on idle. Disable
the stock screensaver on the device (`screensaver_enabled=0`) so idle goes to
screen-off rather than a doomed dream.

## Consequences

- Reliable: nothing fights the app; it stays open showing photos.
- Power-friendly: the screen sleeps when the room is empty / overnight (presence +
  a 3-min idle timeout) and relights on approach.
- To use the Portal for something else, exit the frame (double-tap) and re-open
  after — acceptable for a device repurposed as a frame.
- `FrameDreamService` is retained (it works on non-Portal Android) but unused on
  Portal.
- Trade-off: the frame isn't auto-summoned on idle — it's the thing you leave
  running. Given Portal's block, that's the only reliable option short of
  disabling Portal's presence/SuperFrame, rejected as too invasive and risky to
  the Portal's own UX (camera-follow, wake-on-approach).
