import java.util.Properties
import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    val targetAbis = providers.gradleProperty("targetAbis")
        .orNull
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.ifEmpty { null }
        ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")

    namespace = "com.wdtt.client"   
    compileSdk = 35
    
    defaultConfig {
        applicationId = "ru.safarvpn.com"
        minSdk = 28
        targetSdk = 35
        versionCode = 58
        versionName = "1.4.20"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*targetAbis.toTypedArray())
            isUniversalApk = targetAbis.size > 1
        }
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    // Credentials are supplied by a private file outside this Git checkout.
    val signingProperties = Properties()
    val signingPropertiesFile = rootProject.file("../safarvpn-signing/signing.properties")
    if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) } &&
        !signingPropertiesFile.isFile) {
        throw GradleException("Permanent release signing configuration is unavailable. Run scripts/create-signing-key.ps1 with the project owner's account.")
    }
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use { signingProperties.load(it) }
    }
    signingConfigs {
        create("release") {
            if (signingPropertiesFile.exists()) {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            jniLibs.setSrcDirs(listOf("src/main/jniLibs"))
        }
    }
}

// Use the project-local toolchain from Android Studio as well as from the build script.
val localGoDirectory = rootDir.resolve(".build-tools/go")
val goExecutable = localGoDirectory.resolve(
    if (System.getProperty("os.name").startsWith("Windows")) "bin/go.exe" else "bin/go"
).takeIf { it.isFile }?.absolutePath ?: "go"
val localNdkDirectory = rootDir.resolve(".build-tools/android-sdk/ndk/28.2.13676358")

tasks.register<Exec>("buildNativeLibs") {
    group = "build"
    description = "Build Go client binaries for Android ABIs and copy them into app/src/main/jniLibs"
    workingDir = rootDir
    environment("GO_EXE", goExecutable)
    if (localNdkDirectory.isDirectory) {
        environment("ANDROID_NDK_HOME", localNdkDirectory.absolutePath)
    }
    if (localGoDirectory.isDirectory) {
        environment("GOMODCACHE", rootDir.resolve(".build-tools/go-mod-cache").absolutePath)
        environment("GOCACHE", rootDir.resolve(".build-tools/go-build-cache").absolutePath)
        environment("GOPATH", rootDir.resolve(".build-tools/go-work").absolutePath)
    }
    if (System.getProperty("os.name").startsWith("Windows")) {
        commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
            rootDir.resolve("scripts/build-native-libs.ps1").absolutePath)
    } else {
        commandLine("bash", rootDir.resolve("scripts/build-native-libs.sh").absolutePath)
    }
    environment(
        "TARGET_ABIS",
        providers.gradleProperty("targetAbis").orNull?.replace(',', ' ')
            ?: "arm64-v8a armeabi-v7a x86_64",
    )
}

tasks.register<Exec>("buildServerAsset") {
    group = "build"
    description = "Build Linux server binary and copy it into app assets"
    workingDir = rootDir
    environment("GOOS", "linux")
    environment("GOARCH", "amd64")
    environment("CGO_ENABLED", "0")
    if (localGoDirectory.isDirectory) {
        environment("GOMODCACHE", rootDir.resolve(".build-tools/go-mod-cache").absolutePath)
        environment("GOCACHE", rootDir.resolve(".build-tools/go-build-cache").absolutePath)
        environment("GOPATH", rootDir.resolve(".build-tools/go-work").absolutePath)
    }
    commandLine(
        goExecutable,
        "build",
        "-trimpath",
        "-o",
        rootDir.resolve("app/src/main/assets/server").absolutePath,
        "./server",
    )
}

tasks.named("preBuild").configure {
    dependsOn("buildNativeLibs")
    dependsOn("buildServerAsset")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    implementation("com.github.mwiede:jsch:0.2.16")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
