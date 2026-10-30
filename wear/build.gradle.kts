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
        targetSdk = 34
        versionCode = 10
        versionName = "0.0.10-m3-step2"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    // Flow queries, so the screens update as check-ins store messages.
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
