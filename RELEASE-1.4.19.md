# SafarVPN Android 1.4.19 (57)

Removed the separate Devices card and its dialog entry from the Tunnel tab. Device management, unbinding and paid personal limits remain in Account. Shared foreground operation tracking and pending-unbind connection protection are retained.

UI-only change; no server files, configuration, database or payment behavior changed. Existing 0108 / ledger 2 deployment remains the last known backend.

Validation: existing 38 Android unit tests, signed universal release build/R8, original certificate, manifest version/package and packaged native clients for all three ABIs. Evidence and final APK hash: `artifacts/SafarVPN-1.4.19-verification.json`. Hardware UI QA/ADB not performed. No commit/push.
