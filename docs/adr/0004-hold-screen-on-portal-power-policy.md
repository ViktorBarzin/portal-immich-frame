# 4. Hold the screen on — Portal's power policy darkens the frame

Date: 2026-06-21

## Status

Accepted (supersedes the power-management decision in ADR-0003)

## Context

ADR-0003 chose to **not** hold the screen on (`FLAG_KEEP_SCREEN_ON` removed in
v0.1.4), expecting the Portal's camera presence to keep the screen lit while
someone is near and power it off on idle. In use this defeated the frame's
purpose:

- The Portal's own power policy cycles the screen dark on a ~3-minute timer even
  with `screen_off_timeout` maxed (`2147483647`) and `screensaver_enabled=0`.
  `stay_on_while_plugged_in=7` does not stick (reads back `0`). The device-level
  knobs do not keep a foreground app lit.
- Camera presence relights on approach, but a photo frame is meant to be
  glanceable from across the room, not a follow-me display — it kept going dark
  whenever no one stood right in front of it.
- v0.1.0–v0.1.3, which held `FLAG_KEEP_SCREEN_ON`, stayed lit reliably; v0.1.4,
  which removed it, went dark. The flag was the only thing that worked.

The user explicitly wants the frame to stay lit, accepting the power cost — it's
a plugged-in appliance.

## Decision

Hold the screen on at the app level (v0.1.5):

- `FrameActivity.onCreate` re-adds `window.addFlags(FLAG_KEEP_SCREEN_ON)`.
- `FrameView` sets `webView.keepScreenOn = true` — belt-and-suspenders: a visible
  view's keep-screen-on funnels into the same PowerManager display hold and
  survives the WebView rebuild path (renderer-crash self-heal).

This in-app wakelock is the reliable keep-on for this device; the Portal's power
policy and the global device settings are not.

## Consequences

- The frame stays lit continuously while open — its intended behaviour.
- Higher power draw (always-on panel). Acceptable for a plugged-in frame.
- Verification is by eye: `adb screencap` is black for Portal's hardware WebView
  surface, and Portal's customised `dumpsys window` omits `mHoldScreenWindow`.
  The closest CLI proxy is PowerManager reporting
  `mHoldingDisplaySuspendBlocker=true` + `mHoldingWakeLockSuspendBlocker=true`
  with the display `ON` while the frame is foreground.
- If a night-off window is wanted later, gate the flag on a local time schedule
  inside the app rather than reverting to device power management (which does not
  hold the frame lit).
