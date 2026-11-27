import java.net.URI
import com.android.build.gradle.api.ApkVariantOutput

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Sherpa-ONNX v1.12.31 includes built-in espeak-ng/piper-phonemize for full offline English Piper VITS models
val sherpaOnnxVersion = "1.12.31"
val sherpaOnnxAarFile = file("libs/sherpa-onnx-$sherpaOnnxVersion.aar")
val sherpaOnnxAarUrl =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"

val downloadSherpaOnnxAar by tasks.registering {
    description = "Downloads prebuilt Sherpa-ONNX Android AAR if not present in app/libs/"
    outputs.file(sherpaOnnxAarFile)
    onlyIf { !sherpaOnnxAarFile.exists() }
    doLast {
        sherpaOnnxAarFile.parentFile.mkdirs()
        val tmp = file("${sherpaOnnxAarFile.absolutePath}.tmp")
        logger.lifecycle("Downloading Sherpa-ONNX AAR from $sherpaOnnxAarUrl ...")
        URI(sherpaOnnxAarUrl).toURL().openStream().use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (!tmp.renameTo(sherpaOnnxAarFile)) {
            tmp.copyTo(sherpaOnnxAarFile, overwrite = true)
            tmp.delete()
        }
        logger.lifecycle("Saved ${sherpaOnnxAarFile.name} (${sherpaOnnxAarFile.length()} bytes)")
    }
}

tasks.named("preBuild") {
    dependsOn(downloadSherpaOnnxAar)
}

android {
    namespace = "com.geminidict.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.geminidict.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.6.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // APK is built directly as GeminiDict-lite.apk (no post-build copy/rename in CI).
    // NOTE: AGP 8.7's new VariantOutput API has no outputFileName property
    // (verified against gradle-api-8.7.2.jar); the old ApkVariantOutput API does.
    applicationVariants.all {
        outputs.all {
            if (this is ApkVariantOutput) {
                outputFileName = "GeminiDict-lite.apk"
            }
        }
    }

    // Stable debug signing: a committed app/debug.keystore (debug-only key,
    // password "android") so every CI build is signed with the SAME key and
    // installs as an update over the previous version. Without this, each
    // GitHub runner generates a fresh random debug key and Android refuses
    // to update ("signatures do not match"), forcing an uninstall first.
    // NOTE: a bare `debug { }` inside signingConfigs resolves to the
    // buildTypes debug container in Kotlin DSL; address it by name.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Room SQLite Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Networking & JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Encrypted storage for Gemini API Key
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Pure-Java tar.bz2 extraction for Piper VITS neural voice archives
    implementation("org.apache.commons:commons-compress:1.27.1")

    // Sherpa-ONNX offline neural TTS runtime (auto-downloaded into app/libs/)
    implementation(files("libs/sherpa-onnx-$sherpaOnnxVersion.aar"))

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("org.apache.commons:commons-compress:1.27.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
