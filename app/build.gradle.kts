import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Untracked local overrides. `local.properties` is git-ignored, so it is the one place a keystore password may live
 * on a developer machine; CI passes the same keys as environment variables instead.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** Environment variable first (CI), then `local.properties` (developer machine), then null. */
fun secret(name: String): String? =
    (System.getenv(name) ?: localProperties.getProperty(name))?.takeIf { it.isNotBlank() }

/** Gradle property first (`-PversionName=...`), then environment variable, then the value baked into this file. */
fun buildValue(gradleProperty: String, envVar: String, default: String): String =
    (providers.gradleProperty(gradleProperty).orNull ?: System.getenv(envVar))?.takeIf { it.isNotBlank() } ?: default

// Release signing is opt-in: with no keystore configured `assembleRelease` still succeeds and emits an unsigned APK.
val keystorePath = secret("ANDROID_KEYSTORE_PATH")
val keystoreFile = keystorePath?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "dev.ntainy.guitar_tuner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.ntainy.guitar_tuner"
        minSdk = 36
        targetSdk = 37
        versionCode = buildValue("versionCode", "VERSION_CODE", "1").toInt()
        versionName = buildValue("versionName", "VERSION_NAME", "0.2-beta.2")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Only declared when a keystore is actually present, so an unconfigured checkout has nothing to fail on.
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = secret("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = secret("ANDROID_KEY_ALIAS")
                keyPassword = secret("ANDROID_KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // AGP 9 DSL: one switch for R8 code shrinking, obfuscation and resource shrinking.
            // Keep rules live in `src/main/keepRules/`, which AGP merges automatically.
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // android.util.Log etc. return defaults in JVM unit tests instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
}

// The recording replay harness (RecordingReplayTest) takes its inputs as `-Ptuner.replay.*` properties, forwarded
// here into the forked test JVM: environment variables do not reliably reach it through a warm daemon.
tasks.withType<Test>().configureEach {
    val replayProperties = listOf("tuner.replay.file", "tuner.replay.gainDb", "tuner.replay.sensitivityDb", "tuner.replay.out")
    replayProperties.forEach { key ->
        providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
    }
}

dependencies {
    implementation(project(":dsp"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
