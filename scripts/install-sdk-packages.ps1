param([string[]]$PackageNames = @('platforms;android-35', 'build-tools;35.0.0', 'build-tools;36.0.0', 'platform-tools', 'ndk;28.2.13676358'))
$ErrorActionPreference = 'Stop'
$rootDir = Split-Path $PSScriptRoot -Parent
$toolsDir = Join-Path $rootDir '.build-tools'
$sdkDir = Join-Path $toolsDir 'android-sdk'
New-Item -ItemType Directory -Force -Path $sdkDir | Out-Null
[xml]$repository = Get-Content (Join-Path $toolsDir 'repository.xml')
$packages = $PackageNames
$allowedPrefix = [System.IO.Path]::GetFullPath($toolsDir).TrimEnd('\') + '\'
foreach ($packageName in $packages) {
    $package = $repository.'sdk-repository'.remotePackage | Where-Object path -eq $packageName
    $archive = $package.archives.archive | Where-Object { -not $_.'host-os' -or $_.'host-os' -eq 'windows' } | Select-Object -First 1
    $remoteFile = [string]$archive.complete.url
    $zipFile = Join-Path $toolsDir $remoteFile
    $expectedHash = [string]$archive.complete.checksum.'#text'
    $relativeTarget = $packageName.Replace(';','/')
    $targetDir = [System.IO.Path]::GetFullPath((Join-Path $sdkDir $relativeTarget))
    if (Test-Path -LiteralPath (Join-Path $targetDir 'source.properties')) {
        Write-Host "Already installed: $packageName"
        continue
    }
    Write-Host "Downloading official SDK package: $packageName ($($archive.complete.size) bytes)"
    if (-not (Test-Path -LiteralPath $zipFile) -or (Get-Item -LiteralPath $zipFile).Length -ne [long]$archive.complete.size) {
        & curl.exe --fail --location --silent --show-error --connect-timeout 20 --max-time 2400 --retry 5 --retry-all-errors --continue-at - ("https://dl-ssl.google.com/android/repository/" + $remoteFile) --output $zipFile
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $packageName" }
    }
    $actualHash = (Get-FileHash -LiteralPath $zipFile -Algorithm SHA1).Hash
    if ($actualHash -ne $expectedHash) { throw "Official SDK checksum mismatch: $packageName" }
    $stagingDir = Join-Path $toolsDir ('unpack-' + $packageName.Replace(';','-'))
    New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null
    & tar.exe -xf $zipFile -C $stagingDir
    if ($LASTEXITCODE -ne 0) { throw "Cannot extract: $packageName" }
    $sourceDir = [System.IO.Path]::GetFullPath((Get-ChildItem -LiteralPath $stagingDir -Directory | Select-Object -First 1).FullName)
    if (-not $sourceDir.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not $targetDir.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'SDK package move is outside project build-tools directory.'
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $targetDir -Parent) | Out-Null
    if (Test-Path -LiteralPath $targetDir) { throw "Incomplete package already exists: $targetDir" }
    Move-Item -LiteralPath $sourceDir -Destination $targetDir
    Write-Host "Installed and checksum verified: $packageName"
}
$sdkPropertyPath = $sdkDir.Replace('\','/')
[System.IO.File]::WriteAllText((Join-Path $rootDir 'local.properties'), "sdk.dir=$sdkPropertyPath`n", [System.Text.Encoding]::ASCII)
Write-Host 'Android SDK ready; project SDK path configured.'
