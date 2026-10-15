# SafarVPN Android 1.4.20 (58)

Fixed account server selection after the node-id migration. Previously the one-shot profile reader discarded nodeId, so the account owner/identity guard recomputed a legacy peer-based id and rejected selection. Both list and one-shot reads now use one decoder preserving every connection field, including nodeId. The existing owner/session fence remains intact.

Tunnel server cards are compact rows (minimum 48dp touch area). Removed per-server ping buttons and the blocking ping progress dialog. The existing global latency action beside subscription refresh retains its progress indicator; individual rows show progress and then latency or an unavailable label without opening a window.

Validation: 39 Android unit tests including persisted two-node selection identities, cross-owner rejection and legacy profiles; signed universal release/R8, original signing certificate, package/version and packaged native clients for all three ABIs. Evidence: artifacts/SafarVPN-1.4.20-verification.json. Hardware UI/connection QA not performed. No ADB, production changes, commit or push.
