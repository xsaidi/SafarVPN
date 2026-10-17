# Device admission error handling — included in 1.4.22

These changes are packaged in SafarVPN 1.4.22/code60. See RELEASE-1.4.22.md for build verification. The diagnostic implementation uses the existing production APIs. No phone installation/runtime test was performed.

- Before connecting an account-owned profile, read the existing authenticated status/device APIs. A full inventory rejects only a new identity; an already-bound device may reconnect. Pending unbinds remain fenced until node confirmation.
- Compare account ownership, saved profile and tunnel credentials. Skip manual profiles. Discard advice if the account session, credential generation or limit changes during the checks. Each diagnostic HTTP call has a 1.5-second timeout; API errors leave node admission authoritative.
- Repeat diagnostics after persistent WRAP handshake timeouts. If the cause cannot be established, display an inconclusive connection error instead of asserting an incorrect password.
- Preserve device refusals separately from wrong-password refusals in both WireGuard and raw configuration responses. Existing `device_mismatch` is ambiguous (binding/limit/revocation), so describe it as a device-access refusal; accept the explicit `device_limit` code as a limit error.
- Keep fatal errors visible and expose the existing «Устройства и лимит» management dialog from account-related errors. It supports unbinding and the existing quoted device-limit purchase flow.

This is diagnostic UI, not slot reservation: nodes and central authority still enforce admission. Inventory can change immediately after a read; retry after completing device management. Existing installed APKs retain their previous behavior until the next release is installed.

Validation commands (no APK/native binaries):

```powershell
# With the project JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME settings:
.\.build-tools\gradle-9.1.0\bin\gradle.bat :app:testDebugUnitTest -x buildNativeLibs -x buildServerAsset --offline --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process -PgoogleMavenUrl=https://dl-ssl.google.com/dl/android/maven2/
# In go_client, use the bundled Go and project GOCACHE:
go test protocol.go errhint.go protocol_denial_test.go -count=1
```

The full Go client cannot be tested as a Windows target because its existing TUN/socket code uses Linux APIs; the protocol tests run independently of that platform-specific code. Real phone/network behavior remains untested until a release is assembled.

Results: Gradle `testDebugUnitTest` passed on the final source, including all six new device-admission scenarios. The isolated Go protocol suite passed all eight WireGuard/raw denial cases. The first sandboxed Gradle run encountered a cache access denial; the permitted rerun passed. `git diff --check` passed for the changed application/Go source.
