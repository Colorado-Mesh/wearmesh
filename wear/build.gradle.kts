plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.coloradomesh.wearmesh"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.coloradomesh.wearmesh"
        // Wear OS 4: the Android 12 Bluetooth permissions and notification permission always apply.
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1-m0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.kotlinx.coroutines.android)
}
