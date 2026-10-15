$ErrorActionPreference = 'Stop'
$taskRepo = Split-Path $PSScriptRoot -Parent
$taskApk = Join-Path $taskRepo 'artifacts/SafarVPN-1.4.20-universal-release.apk'
Copy-Item -LiteralPath (Join-Path $taskRepo 'app/build/outputs/apk/release/app-universal-release.apk') -Destination $taskApk
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$taskTools = Join-Path $taskRepo '.build-tools/android-sdk/build-tools/35.0.0'
$taskCert = & (Join-Path $taskTools 'apksigner.bat') verify --print-certs $taskApk 2>&1
if ($LASTEXITCODE -ne 0 -or ($taskCert -join "`n") -notmatch 'certificate SHA-256 digest: 8d16ae71e2423d5d599060e1d32c18ce52bb233c29732940313aee4be4f2ce68') { throw 'Original APK certificate verification failed' }
$taskManifest = & (Join-Path $taskTools 'aapt.exe') dump badging $taskApk
if ($LASTEXITCODE -ne 0 -or ($taskManifest -join "`n") -notmatch "package: name='ru.safarvpn.com' versionCode='58' versionName='1.4.20'") { throw 'APK version/package mismatch' }
$taskTests = @(Get-ChildItem -LiteralPath (Join-Path $taskRepo 'app/build/test-results/testDebugUnitTest') -Filter '*.xml' | ForEach-Object {
    [xml]$taskXml = Get-Content -LiteralPath $_.FullName
    @{name = [string]$taskXml.testsuite.name; tests = [int]$taskXml.testsuite.tests; failures = [int]$taskXml.testsuite.failures; errors = [int]$taskXml.testsuite.errors}
})
if (($taskTests | Where-Object { $_.failures -ne 0 -or $_.errors -ne 0 }).Count -ne 0 -or ($taskTests.tests | Measure-Object -Sum).Sum -ne 39) { throw 'Expected 39 successful Android unit tests' }
$taskSources = @{}
$taskNative = @{}
$taskNativeUnstripped = @{}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskArchive = [System.IO.Compression.ZipFile]::OpenRead($taskApk)
try {
    foreach ($taskAbi in @('arm64-v8a','armeabi-v7a','x86_64')) {
        $taskEntry = $taskArchive.GetEntry("lib/$taskAbi/libclient.so")
        if ($null -eq $taskEntry) { throw "Missing native client: $taskAbi" }
        $taskStream = $taskEntry.Open()
        $taskHasher = [System.Security.Cryptography.SHA256]::Create()
        try { $taskDigest = [BitConverter]::ToString($taskHasher.ComputeHash($taskStream)).Replace('-', '').ToLowerInvariant() }
        finally { $taskStream.Dispose(); $taskHasher.Dispose() }
        # Release packaging strips debug symbols from the freshly rebuilt native client.
        $taskLocalDigest = (Get-FileHash -LiteralPath (Join-Path $taskRepo "app/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/$taskAbi/libclient.so") -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($taskDigest -ne $taskLocalDigest) { throw "Packaged native client does not match rebuilt binary: $taskAbi" }
        $taskNative[$taskAbi] = $taskDigest
        $taskNativeUnstripped[$taskAbi] = (Get-FileHash -LiteralPath (Join-Path $taskRepo "app/src/main/jniLibs/$taskAbi/libclient.so") -Algorithm SHA256).Hash.ToLowerInvariant()
    }
} finally { $taskArchive.Dispose() }
@('app/src/main/java/com/wdtt/client/account/DeviceLimitPurchase.kt','app/src/test/java/com/wdtt/client/account/DeviceLimitPurchaseTest.kt','app/src/main/java/com/wdtt/client/DeviceIdentity.kt','app/src/main/java/com/wdtt/client/account/ServerDevices.kt','app/src/main/java/com/wdtt/client/MainActivity.kt','app/src/main/java/com/wdtt/client/profiles/SubscriptionImport.kt','app/src/main/res/xml/account_backup_rules.xml','app/src/main/res/xml/account_data_extraction_rules.xml','go_client/main.go','app/src/test/java/com/wdtt/client/account/ServerDevicesTest.kt','app/src/test/java/com/wdtt/client/account/AccountProfilesTest.kt','app/build.gradle.kts','app/src/main/java/com/wdtt/client/account/AccountTab.kt','app/src/main/java/com/wdtt/client/account/AccountAvatar.kt','app/src/main/java/com/wdtt/client/account/BillingSheet.kt','app/src/main/java/com/wdtt/client/account/BillingModels.kt','app/src/main/java/com/wdtt/client/account/AccountBilling.kt','app/src/main/java/com/wdtt/client/account/TopupStatus.kt','app/src/main/java/com/wdtt/client/account/TopupSuccessSheet.kt','app/src/main/java/com/wdtt/client/account/AccountApi.kt','app/src/main/java/com/wdtt/client/tunnel/TunnelManager.kt','app/src/main/java/com/wdtt/client/account/AccountUi.kt','app/src/main/java/com/wdtt/client/ui/settings/TunnelConnectButton.kt','app/src/main/java/com/wdtt/client/account/AccountRepository.kt','app/src/main/java/com/wdtt/client/account/AccountProfiles.kt','app/src/main/java/com/wdtt/client/profiles/ProfilesStore.kt','app/src/main/java/com/wdtt/client/settings/SettingsStore.kt','app/src/main/java/com/wdtt/client/ui/profiles/ProfilesTab.kt','app/src/main/java/com/wdtt/client/ui/profiles/SubscriptionsSheet.kt','app/src/main/java/com/wdtt/client/ui/settings/SettingsTab.kt') | ForEach-Object {
    $taskSources[$_] = (Get-FileHash -LiteralPath (Join-Path $taskRepo $_) -Algorithm SHA256).Hash.ToLowerInvariant()
}
$taskEvidence = @{
    version = '1.4.20'; versionCode = 58; package = 'ru.safarvpn.com'
    apk = 'SafarVPN-1.4.20-universal-release.apk'
    apkSha256 = (Get-FileHash -LiteralPath $taskApk -Algorithm SHA256).Hash.ToLowerInvariant()
    apkBytes = (Get-Item -LiteralPath $taskApk).Length
    signingCertificateSha256 = '8d16ae71e2423d5d599060e1d32c18ce52bb233c29732940313aee4be4f2ce68'
    androidTests = $taskTests; releaseBuild = $true; r8 = $true; phoneQA = $false; adbUsed = $false
    sourceSha256 = $taskSources
    nativeClientSha256 = $taskNative
    nativeClientUnstrippedSha256 = $taskNativeUnstripped
    backendChanged = $false; backendChecked = $false
    backendLastKnownImage = 'sha256:64ac8d24c7ea7268035f5ef5974eb30509a46aa956898b0835cdc81c19b1bfd3'
    backendLedgerSchema = 2; backendSchema = '0108'; backendTests = 30; backendHealthyLastVerified = $true; realPaymentTest = $false
}
$taskEvidence | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskRepo 'artifacts/SafarVPN-1.4.20-verification.json') -Encoding utf8
@{apkSha256 = $taskEvidence.apkSha256; apkBytes = $taskEvidence.apkBytes; androidTests = 39; originalCertificate = $true} | ConvertTo-Json -Compress
