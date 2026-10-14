$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path $PSScriptRoot -Parent
$taskApk = Join-Path $taskRepo 'artifacts/SafarVPN-1.4.9-universal-release.apk'
Copy-Item -LiteralPath (Join-Path $taskRepo 'app/build/outputs/apk/release/app-universal-release.apk') -Destination $taskApk
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$taskTools = Join-Path $taskRepo '.build-tools/android-sdk/build-tools/35.0.0'
$taskCert = & (Join-Path $taskTools 'apksigner.bat') verify --print-certs $taskApk 2>&1
if ($LASTEXITCODE -ne 0 -or ($taskCert -join "`n") -notmatch 'certificate SHA-256 digest: 8d16ae71e2423d5d599060e1d32c18ce52bb233c29732940313aee4be4f2ce68') { throw 'Original APK certificate verification failed' }
$taskManifest = & (Join-Path $taskTools 'aapt.exe') dump badging $taskApk
if ($LASTEXITCODE -ne 0 -or ($taskManifest -join "`n") -notmatch "package: name='ru.safarvpn.com' versionCode='47' versionName='1.4.9'") { throw 'APK version/package mismatch' }
$taskTests = @(Get-ChildItem -LiteralPath (Join-Path $taskRepo 'app/build/test-results/testDebugUnitTest') -Filter '*.xml' | ForEach-Object {
    [xml]$taskXml = Get-Content -LiteralPath $_.FullName
    @{name = [string]$taskXml.testsuite.name; tests = [int]$taskXml.testsuite.tests; failures = [int]$taskXml.testsuite.failures; errors = [int]$taskXml.testsuite.errors}
})
if (($taskTests | Where-Object { $_.failures -ne 0 -or $_.errors -ne 0 }).Count -ne 0 -or ($taskTests.tests | Measure-Object -Sum).Sum -ne 11) { throw 'Expected 11 successful Android unit tests' }
$taskSources = @{}
@('app/build.gradle.kts','app/src/main/java/com/wdtt/client/account/AccountTab.kt','app/src/main/java/com/wdtt/client/tunnel/TunnelManager.kt','app/src/main/java/com/wdtt/client/account/AccountUi.kt','app/src/main/java/com/wdtt/client/ui/settings/TunnelConnectButton.kt','app/src/main/java/com/wdtt/client/account/AccountRepository.kt','app/src/main/java/com/wdtt/client/account/AccountProfiles.kt','app/src/main/java/com/wdtt/client/profiles/ProfilesStore.kt','app/src/main/java/com/wdtt/client/settings/SettingsStore.kt','app/src/main/java/com/wdtt/client/ui/profiles/ProfilesTab.kt','app/src/main/java/com/wdtt/client/ui/profiles/SubscriptionsSheet.kt','app/src/main/java/com/wdtt/client/ui/settings/SettingsTab.kt') | ForEach-Object {
    $taskSources[$_] = (Get-FileHash -LiteralPath (Join-Path $taskRepo $_) -Algorithm SHA256).Hash.ToLowerInvariant()
}
$taskEvidence = @{
    version = '1.4.9'; versionCode = 47; package = 'ru.safarvpn.com'
    apk = 'SafarVPN-1.4.9-universal-release.apk'
    apkSha256 = (Get-FileHash -LiteralPath $taskApk -Algorithm SHA256).Hash.ToLowerInvariant()
    apkBytes = (Get-Item -LiteralPath $taskApk).Length
    signingCertificateSha256 = '8d16ae71e2423d5d599060e1d32c18ce52bb233c29732940313aee4be4f2ce68'
    androidTests = $taskTests; releaseBuild = $true; r8 = $true; phoneQA = $false; adbUsed = $false
    sourceSha256 = $taskSources
    backendActiveImage = 'sha256:d181ede1c2c7b62f61c507acb04a0a13465bc648ef460ec33d0025d5b1607031'
}
$taskEvidence | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskRepo 'artifacts/SafarVPN-1.4.9-verification.json') -Encoding utf8
@{apkSha256 = $taskEvidence.apkSha256; apkBytes = $taskEvidence.apkBytes; androidTests = 11; originalCertificate = $true} | ConvertTo-Json -Compress
