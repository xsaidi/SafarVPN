param([switch]$RunTests)
$ErrorActionPreference = 'Stop'
$rootDir = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = Join-Path $rootDir '.build-tools/android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_NDK_HOME = Join-Path $env:ANDROID_HOME 'ndk/28.2.13676358'
$env:GRADLE_USER_HOME = Join-Path $rootDir '.gradle-home'
$env:GOMODCACHE = Join-Path $rootDir '.build-tools/go-mod-cache'
$env:GOCACHE = Join-Path $rootDir '.build-tools/go-build-cache'
$env:GOPATH = Join-Path $rootDir '.build-tools/go-work'
$env:PATH = (Join-Path $rootDir '.build-tools/go/bin') + ';' + $env:JAVA_HOME + '/bin;' + $env:PATH
Push-Location $rootDir
try {
    $taskBuildTasks = if ($RunTests) { @(':app:testDebugUnitTest', ':app:assembleRelease') } else { @(':app:assembleRelease') }
    $localGradle = Join-Path $rootDir '.build-tools/gradle-9.1.0/bin/gradle.bat'
    if (Test-Path -LiteralPath $localGradle) {
        & $localGradle @taskBuildTasks --no-daemon --max-workers=2 '-Pkotlin.compiler.execution.strategy=in-process' '-PgoogleMavenUrl=https://dl-ssl.google.com/dl/android/maven2/'
    } else {
        & "$env:JAVA_HOME/bin/java.exe" -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain @taskBuildTasks --no-daemon --max-workers=2 '-Pkotlin.compiler.execution.strategy=in-process' '-PgoogleMavenUrl=https://dl-ssl.google.com/dl/android/maven2/'
    }
    if ($LASTEXITCODE -ne 0) { throw 'SafarVPN release build failed' }
} finally { Pop-Location }
