$ErrorActionPreference = 'Stop'
$rootDir = Split-Path $PSScriptRoot -Parent
$privateDir = Join-Path (Split-Path $rootDir -Parent) 'safarvpn-signing'
New-Item -ItemType Directory -Force -Path $privateDir | Out-Null
$sid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $privateDir /inheritance:r /grant:r "*${sid}:(OI)(CI)F" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot secure signing directory ACL' }
# A Codex sandbox account may share the human user's profile directory.
# Retain private access for that profile owner so Studio can read the same key.
$profileAccountName = Split-Path $env:USERPROFILE -Leaf
$profileAccount = Get-LocalUser -Name $profileAccountName -ErrorAction SilentlyContinue
if ($profileAccount -and $profileAccount.SID.Value -ne $sid) {
    $profileSid = $profileAccount.SID.Value
    & icacls.exe $privateDir /grant:r "*${profileSid}:(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot grant signing-key access to the profile owner' }
}
$keyFile = Join-Path $privateDir 'safarvpn-release.p12'
$propertiesFile = Join-Path $privateDir 'signing.properties'
if ((Test-Path -LiteralPath $keyFile) -or (Test-Path -LiteralPath $propertiesFile)) {
    if ((Test-Path -LiteralPath $keyFile) -and (Test-Path -LiteralPath $propertiesFile)) {
        Write-Host 'Existing permanent SafarVPN signing key retained.'
        exit 0
    }
    throw 'Signing directory is incomplete; refusing to replace an existing key.'
}
$randomBytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($randomBytes)
$rng.Dispose()
$password = [Convert]::ToBase64String($randomBytes)
$env:SAFAR_SIGNING_PASSWORD = $password
try {
    & 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe' -genkeypair -keystore $keyFile -storetype PKCS12 -alias safarvpn-release -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=SafarVPN, O=SafarVPN, C=RU' -storepass:env SAFAR_SIGNING_PASSWORD -keypass:env SAFAR_SIGNING_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed' }
    $escapedKeyFile = $keyFile.Replace('\','/')
    $props = "storeFile=$escapedKeyFile`nstorePassword=$password`nkeyAlias=safarvpn-release`nkeyPassword=$password`n"
    [System.IO.File]::WriteAllText($propertiesFile, $props, [System.Text.Encoding]::ASCII)
    [System.IO.File]::WriteAllText((Join-Path $privateDir 'BACKUP.txt'), 'Keep this folder private and back up both safarvpn-release.p12 and signing.properties. The same permanent key is required for future SafarVPN upgrades. Do not commit this folder or send its contents in chat.')
    Write-Host 'Permanent release key created outside Git with a private signing properties file.'
} finally {
    Remove-Item Env:\SAFAR_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
    $props = $null
}
