plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tpmanley.spotifywidgetbutton"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tpmanley.spotifywidgetbutton"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Used by the Spotify auth library's redirect receiver: spotifywidgetbutton://callback
        manifestPlaceholders["redirectSchemeName"] = "spotifywidgetbutton"
        manifestPlaceholders["redirectHostName"] = "callback"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(libs.gson)
    implementation(libs.spotify.auth)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.org.json) // real JSON parser; android.jar only has stubs
}
