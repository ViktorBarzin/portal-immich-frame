# 2. Deploy by adb sideload, outside the cluster CI/CD pipeline

Date: 2026-06-16

## Status

Accepted

## Context

Every other first-party app in this monorepo is a container deployed to
Kubernetes via the GitHub Actions → ghcr → Woodpecker pipeline. This project's
artifact is fundamentally different: an **APK** that runs on a single personal
device (the Portal), not a container that runs in the cluster. The Portal is
Android 10, reached over USB by a machine running `adb` (here, a Mac on the same
LAN as the device; the build host — the devvm — is remote over a tunnel).

## Decision

Build the APK in a container on the devvm, then deploy it by **adb sideload**
over USB. Do **not** wire this repo into the cluster's image-build/deploy
pipeline.

## Consequences

- No ghcr image, no Woodpecker deploy step, no in-cluster build — those concepts
  don't apply to a device APK.
- Releases are versioned with semver (git tags); the build artifact is the APK
  attached to a release, not a pushed image.
- Deployment is a manual (or scripted) `adb install` step against the device,
  documented in the README.
- This is a deliberate, one-off exception to the "everything is GitOps-deployed"
  norm of the monorepo, recorded here so it isn't mistaken for an omission.
