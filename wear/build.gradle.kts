plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.coloradomesh.wearmesh"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.coloradomesh.wearmesh"
        // Wear OS 4: the Android 12 Bluetooth permissions and notification permission always apply.
        minSdk = 33
        // Google Play needs API 35 for Wear OS apps from 31 August 2026 (docs/releasing.md).
        targetSdk = 35
        // Releases are 0.1.0, 0.2.0 and so on; between them, the next one with "-dev". The
        // version code counts up by one for every build installed on a watch, release or not.
        // The release workflow checks a tag such as v0.1.0 against versionName.
        versionCode = 26
        versionName = "0.1.0"
    }

    // Release signing (M5). The key is never in the repository: its path and passwords come
    // from the environment, set by the release workflow from GitHub secrets, or by hand for a
    // signed build at home (docs/releasing.md). Without them, the release build is unsigned,
    // as in CI on every pull request.
    val keystore = System.getenv("WEARMESH_KEYSTORE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("WEARMESH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WEARMESH_KEY_ALIAS")
                keyPassword = System.getenv("WEARMESH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric, which runs the database tests on the JVM, reads the app's resources.
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    // Room writes each database version's schema here; commit it, so a later version can be
    // checked against it and given a migration.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // The shared Bluetooth link and session; brings in org.coloradomesh.meshcore:protocol too.
    implementation("org.coloradomesh.meshcore:ble")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.input)
    implementation(libs.wear.complications.data.source)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    // Flow queries, so the screens update as check-ins store messages.
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
