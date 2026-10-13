$ErrorActionPreference = 'Stop'
$rootDir = Split-Path $PSScriptRoot -Parent
$goExe = Join-Path $rootDir '.build-tools/go/bin/go.exe'
$env:GOMODCACHE = Join-Path $rootDir '.build-tools/go-mod-cache'
$env:GOCACHE = Join-Path $rootDir '.build-tools/go-build-cache'
$env:GOPATH = Join-Path $rootDir '.build-tools/go-work'
$env:GOTOOLCHAIN = 'local'
foreach ($moduleDir in @((Join-Path $rootDir 'go_client'), $rootDir)) {
    Push-Location $moduleDir
    try {
        Write-Host "Downloading pinned Go dependencies for $moduleDir"
        & $goExe mod tidy
        if ($LASTEXITCODE -ne 0) { throw 'Go dependency download failed' }
        & $goExe mod verify
        if ($LASTEXITCODE -ne 0) { throw 'Go module checksum verification failed' }
        Write-Host 'Pinned module dependencies downloaded and verified.'
    } finally { Pop-Location }
}
