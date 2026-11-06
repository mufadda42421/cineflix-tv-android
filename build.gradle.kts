plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.cineflix.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cineflix.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        val tmdbAccessToken = (project.findProperty("TMDB_ACCESS_TOKEN") as String?)
            ?: System.getenv("TMDB_ACCESS_TOKEN")
            ?: ""
        val tmdbApiKey = (project.findProperty("TMDB_API_KEY") as String?)
            ?: System.getenv("TMDB_API_KEY")
            ?: ""

        buildConfigField("String", "DEFAULT_TMDB_ACCESS_TOKEN", "\"$tmdbAccessToken\"")
        buildConfigField("String", "DEFAULT_TMDB_API_KEY", "\"$tmdbApiKey\"")
        buildConfigField("String", "CINEFLIX_WEB_URL", "\"https://cineflixbymj.vercel.app\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ""
            isDebuggable = true
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

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Jetpack Compose BOM + Material 3 + Material Icons
    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Jetpack Compose for TV (Google TV / Android TV Leanback Material & Focus)
    implementation("androidx.tv:tv-material:1.0.0")

    // AndroidX Media3 ExoPlayer (Direct Video, HLS, DASH, Subtitles & Audio Tracks)
    val media3Version = "1.5.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Hybrid WebView Support
    implementation("androidx.webkit:webkit:1.12.1")

    // Networking & JSON Serialization (TMDB API v3 + Firebase Firestore REST Sync)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Image Loading (TMDB Posters, Backdrops & Cast Portraits)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Persistent Local Storage (BYOK TMDB Credential, Watchlist, History, Preferred Server)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
