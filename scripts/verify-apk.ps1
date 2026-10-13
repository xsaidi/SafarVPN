param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$ExpectedSignerSha256,
    [string]$ExpectedVersionName,
    [int]$ExpectedVersionCode = 0
)
$ErrorActionPreference = 'Stop'
$rootDir = Split-Path $PSScriptRoot -Parent
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$tools = Join-Path $rootDir '.build-tools/android-sdk/build-tools/36.0.0'
$java = 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
$signature = (& $java -jar (Join-Path $tools 'lib/apksigner.jar') verify --verbose --print-certs $apk) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$digestMatch = [regex]::Match($signature, 'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)')
if (-not $digestMatch.Success -or $digestMatch.Groups[1].Value -ne $ExpectedSignerSha256) {
    throw 'APK signer does not match the permanent SafarVPN certificate.'
}
$badging = (& (Join-Path $tools 'aapt.exe') dump badging $apk) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK manifest.' }
$versionMatch = [regex]::Match($badging, "package: name='ru.safarvpn.com' versionCode='([0-9]+)' versionName='([^']+)'")
if (-not $versionMatch.Success) { throw 'Cannot inspect APK version.' }
$versionCode = [int]$versionMatch.Groups[1].Value
$versionName = $versionMatch.Groups[2].Value
if ($ExpectedVersionName -and $versionName -ne $ExpectedVersionName) { throw 'APK version name mismatch.' }
if ($ExpectedVersionCode -and $versionCode -ne $ExpectedVersionCode) { throw 'APK version code mismatch.' }
foreach ($expected in @("package: name='ru.safarvpn.com'", "application-label:'SafarVPN'", "sdkVersion:'28'", "targetSdkVersion:'35'")) {
    if (-not $badging.Contains($expected)) { throw "APK metadata mismatch: $expected" }
}
if ($badging.Contains('android.permission.REQUEST_INSTALL_PACKAGES')) { throw 'Unexpected APK installer permission.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($apk)
$abis = @('arm64-v8a', 'armeabi-v7a', 'x86_64')
$supportFound = $false
try {
    foreach ($abi in $abis) {
        $nativeEntry = $archive.GetEntry("lib/$abi/libclient.so")
        if (-not $nativeEntry -or $nativeEntry.Length -lt 1MB) { throw "Missing native VPN client: $abi" }
    }
    if (-not $archive.GetEntry('assets/server')) { throw 'Missing bundled server asset.' }
    foreach ($entry in $archive.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' }) {
        $stream = $entry.Open()
        $buffer = New-Object IO.MemoryStream
        try {
            $stream.CopyTo($buffer)
            $dexText = [Text.Encoding]::UTF8.GetString($buffer.ToArray())
            foreach ($forbidden in @('darkbitVPN', 'darkbit_vpnbot', 'darkbit_chat', 'sidylinkbot', 'pay.cloudtips.ru', 'api.github.com/repos/SpaceNeuroX', 'github.com/SpaceNeuroX/proxy-turn-vk-android/releases')) {
                if ($dexText.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -ge 0) { throw "Old promotional/update link in APK: $forbidden" }
            }
            if ($dexText.Contains('https://t.me/safarvpn_bot')) { $supportFound = $true }
        } finally { $stream.Dispose(); $buffer.Dispose() }
    }
} finally { $archive.Dispose() }
if (-not $supportFound) { throw 'SafarVPN support link missing from APK.' }
$file = Get-Item -LiteralPath $apk
[PSCustomObject]@{
    apk = $apk
    bytes = $file.Length
    sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
    applicationId = 'ru.safarvpn.com'
    versionName = $versionName
    versionCode = $versionCode
    label = 'SafarVPN'
    minSdk = 28
    targetSdk = 35
    abis = $abis
    signerSha256 = $digestMatch.Groups[1].Value.ToUpperInvariant()
    signatureVerified = $true
    oldPromotionalAndUpdateLinksAbsent = $true
    runtimeTested = $false
} | ConvertTo-Json -Depth 4
