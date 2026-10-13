$ErrorActionPreference = 'Stop'
$rootDir = Split-Path $PSScriptRoot -Parent
$goExe = if ($env:GO_EXE) { $env:GO_EXE } else { 'go' }
$ndkDir = $env:ANDROID_NDK_HOME
if (-not $ndkDir) { throw 'Set ANDROID_NDK_HOME to the Android NDK directory.' }
$toolchain = Join-Path $ndkDir 'toolchains/llvm/prebuilt/windows-x86_64/bin'
$targetAbis = if ($env:TARGET_ABIS) { $env:TARGET_ABIS -split ' ' } else { @('arm64-v8a', 'armeabi-v7a', 'x86_64') }
$env:GOOS = 'android'
$env:CGO_ENABLED = '1'
Push-Location (Join-Path $rootDir 'go_client')
try {
    foreach ($abi in $targetAbis) {
        switch ($abi) {
            'arm64-v8a' { $env:GOARCH = 'arm64'; $prefix = 'aarch64-linux-android' }
            'armeabi-v7a' { $env:GOARCH = 'arm'; $env:GOARM = '7'; $prefix = 'armv7a-linux-androideabi' }
            'x86_64' { $env:GOARCH = 'amd64'; $prefix = 'x86_64-linux-android' }
            default { throw "Unsupported ABI: $abi" }
        }
        $clang = Join-Path $toolchain 'clang.exe'
        if (-not (Test-Path -LiteralPath $clang)) { throw "Compiler not found for $abi" }
        $env:CC = '"' + $clang + '" --target=' + $prefix + '21'
        $outputDir = Join-Path $rootDir "app/src/main/jniLibs/$abi"
        New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
        Write-Host "Building native client for $abi"
        & $goExe build -trimpath -ldflags=-checklinkname=0 -o (Join-Path $outputDir 'libclient.so') .
        if ($LASTEXITCODE -ne 0) { throw "Go native build failed for $abi" }
    }
} finally { Pop-Location }
