import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Signing settings
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

// Butterscotch runtime checkout (native code is built from its root CMakeLists.txt)
// Versions are the build date: versionName "2026.10.16", versionCode 20261016 (always increasing, one release per day)
val buildDate: LocalDate = LocalDate.now()

// Looked up in this order: -Pbutterscotch.dir=..., a Butterscotch folder next to this android folder (the layout of the
// GitHub repo), or one level higher (the original local setup)
val butterscotchRepoDir: File = (findProperty("butterscotch.dir") as String?)?.let { file(it) }
    ?: listOf(rootProject.file("../Butterscotch"), rootProject.file("../../Butterscotch")).firstOrNull { File(it, "CMakeLists.txt").exists() }
    ?: error("Butterscotch runtime not found, see README.md (or pass -Pbutterscotch.dir=/path/to/Butterscotch)")

val ccachePath = System.getenv("PATH")
    .orEmpty()
    .split(File.pathSeparator)
    .map { File(it, "ccache") }
    .firstOrNull { it.canExecute() }

android {
    namespace = "net.perfectdreams.butterscotch.android"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.mercimekcik.corbakasesi"
        minSdk = 24
        targetSdk = 36
        versionCode = (findProperty("butterscotch.versionCode") as String?)?.toInt() ?: buildDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
        versionName = (findProperty("butterscotch.versionName") as String?) ?: buildDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DPLATFORM=android",
                    "-DENABLE_MODERN_GL=ON",
                    "-DENABLE_LEGACY_GL=OFF",
                    "-DAUDIO_BACKEND=miniaudio",
                    // Adds our APK asset reader (src/main/cpp) to the runtime, see inject.cmake
                    "-DCMAKE_PROJECT_butterscotch_INCLUDE=${file("src/main/cpp/inject.cmake").absolutePath}"
                )
                if (ccachePath != null) {
                    arguments += listOf("-DCMAKE_C_COMPILER_LAUNCHER=${ccachePath.absolutePath}", "-DCMAKE_CXX_COMPILER_LAUNCHER=${ccachePath.absolutePath}")
                }
                cppFlags += "-std=c++17"
                cFlags += "-std=gnu99"
            }
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {

            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystorePropertiesFile.exists()) signingConfigs.getByName("release") else null

            // Upload native symbol tables so Play Console can symbolicate crashes inside the Butterscotch runtime
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {

            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    androidResources {
        // The bundled game is read straight from the APK (see apk_assets.c); uncompressed entries make seeking cheap
        noCompress += listOf("win", "dat", "ogg")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            // Point straight at the Butterscotch repo's root CMakeLists.txt.
            // Adjust if your checkout layout differs.
            path = File(butterscotchRepoDir, "CMakeLists.txt")
            version = "3.22.1"
        }
    }

}

dependencies {
    implementation(project(":common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}