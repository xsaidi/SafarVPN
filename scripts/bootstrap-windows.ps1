$ErrorActionPreference = 'Stop'
$rootDir = Split-Path $PSScriptRoot -Parent
$toolsDir = Join-Path $rootDir '.build-tools'
New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
& curl.exe --fail --location --silent --show-error --connect-timeout 20 --max-time 120 'https://dl-ssl.google.com/android/repository/repository2-3.xml' --output (Join-Path $toolsDir 'repository.xml')
if ($LASTEXITCODE -ne 0) { throw 'Official Android SDK package metadata download failed' }
& curl.exe --fail --location --connect-timeout 20 --max-time 600 --retry 2 'https://dl-ssl.google.com/android/repository/commandlinetools-win-14742923_latest.zip' --output (Join-Path $toolsDir 'commandlinetools.zip')
if ($LASTEXITCODE -ne 0) { throw 'Android command-line tools download failed' }
[xml]$repository = Get-Content (Join-Path $toolsDir 'repository.xml')
$expectedCliHash = $null
foreach ($package in $repository.'sdk-repository'.remotePackage) {
    foreach ($archive in $package.archives.archive) {
        if ($archive.complete.url -eq 'commandlinetools-win-14742923_latest.zip') {
            $expectedCliHash = [string]$archive.complete.checksum.'#text'
        }
    }
}
if (-not $expectedCliHash -or (Get-FileHash (Join-Path $toolsDir 'commandlinetools.zip') -Algorithm SHA1).Hash -ne $expectedCliHash) {
    throw 'Official Android command-line tools checksum mismatch'
}
& curl.exe --fail --location --connect-timeout 20 --max-time 600 --retry 2 'https://dl-ssl.google.com/go/go1.26.0.windows-amd64.zip' --output (Join-Path $toolsDir 'go.zip')
if ($LASTEXITCODE -ne 0) { throw 'Go download failed' }
$sdkDir = Join-Path $toolsDir 'android-sdk'
New-Item -ItemType Directory -Force -Path (Join-Path $sdkDir 'cmdline-tools') | Out-Null
Expand-Archive -LiteralPath (Join-Path $toolsDir 'commandlinetools.zip') -DestinationPath (Join-Path $toolsDir 'commandline-unpack') -Force
$unpackedDir = [System.IO.Path]::GetFullPath((Join-Path $toolsDir 'commandline-unpack/cmdline-tools'))
$latestDir = [System.IO.Path]::GetFullPath((Join-Path $sdkDir 'cmdline-tools/latest'))
$allowedPrefix = [System.IO.Path]::GetFullPath($toolsDir).TrimEnd('\') + '\'
if (-not $unpackedDir.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
    -not $latestDir.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'SDK extraction path is outside project build-tools directory.'
}
if (-not (Test-Path -LiteralPath $latestDir)) {
    Move-Item -LiteralPath $unpackedDir -Destination $latestDir
}
Expand-Archive -LiteralPath (Join-Path $toolsDir 'go.zip') -DestinationPath $toolsDir -Force
Write-Host 'Command-line tools and Go downloaded.'

