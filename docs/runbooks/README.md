# Runbooks live outside this repo

The operational runbooks for these frames — re-provisioning a wiped Portal,
which device is wired to which host, and which frame each device shows — name
LAN addresses, an SSH account and Vault paths. This repository is public so the
frames can fetch their own updates from it (ADR-0006), so those live in the
private monorepo instead:

    ~/code/docs/runbooks/portal-immich-frame-reprovision.md

Nothing here depends on them: the build (`scripts/build-apk.sh`), the runtime
frame URL (ADR-0005) and the update mechanism (ADR-0006) are all documented in
the README and the ADRs alongside it.
