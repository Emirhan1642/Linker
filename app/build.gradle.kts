plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.linker.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.linker.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        manifestPlaceholders += mapOf(
            "redirectSchemeName" to "linker",
            "redirectHostName" to "spotify-callback"
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Load credentials from local.properties
        val properties = org.jetbrains.kotlin.konan.properties.Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }

        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${properties.getProperty("cloudinary.cloudName", "")}\"")
        buildConfigField("String", "CLOUDINARY_API_KEY", "\"${properties.getProperty("cloudinary.apiKey", "")}\"")
        buildConfigField("String", "CLOUDINARY_API_SECRET", "\"${properties.getProperty("cloudinary.apiSecret", "")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${properties.getProperty("cloudinary.uploadPreset", "default_preset")}\"")
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${properties.getProperty("spotify.clientId", "")}\"")
        buildConfigField("String", "SPOTIFY_CLIENT_SECRET", "\"${properties.getProperty("spotify.clientSecret", "")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${properties.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${properties.getProperty("supabase.anonkey", "")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${properties.getProperty("supabase.publishablekey", "")}\"")

        // Giphy API Key (add giphy.apiKey to local.properties)
        buildConfigField("String", "GIPHY_API_KEY", "\"${properties.getProperty("giphy.apiKey", "")}\"")

        // Foursquare API Key
        buildConfigField("String", "FOURSQUARE_API_KEY", "\"${properties.getProperty("foursquare.apiKey", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ENABLE_SENSITIVE_LOGS", "false")
            buildConfigField("boolean", "ENFORCE_SECURITY_POLICY", "true")
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "ENABLE_SENSITIVE_LOGS", "true")
            buildConfigField("boolean", "ENFORCE_SECURITY_POLICY", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
        }
    }

    // Fix for 16 KB page size on Android 15+:
    // Force .so files to be stored uncompressed in the APK so the OS installer
    // can extract and memory-map them correctly (bypasses libsqlcipher.so alignment issue).
    aaptOptions {
        noCompress += listOf(".so")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        lintConfig = file("lint.xml")
        disable += "NullSafeMutableLiveData"
    }
}

ksp {
    arg("dagger.hilt.disableModulesHaveInstallInCheck", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Spotify SDKs
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(files("libs/spotify-auth-release-2.1.0.aar"))
    implementation("com.google.code.gson:gson:2.10.1") // auth SDK needs Gson

    // Core
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.identity.jvm)
    implementation(libs.androidx.compose.remote.creation.core)
    implementation(libs.androidx.compose.runtime)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Database Encryption
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.google.firebase.config)

    // Google Play Services
    implementation(libs.play.services.auth)
    implementation(libs.play.services.nearby)

    // UI Components
    implementation(libs.google.material) // Added Material Design library for Views
    implementation(libs.androidx.emoji2.emojipicker)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)

    // Video Player
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation("androidx.datastore:datastore-core:1.1.1")
    implementation("androidx.datastore:datastore-core-android:1.1.1")

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Security
    implementation(libs.androidx.security.crypto)
    
    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // Signal Protocol (Rust-based JNI)
    implementation(libs.libsignal.client)

    // Core Library Desugaring (Required by Signal Protocol)
    coreLibraryDesugaring(libs.coreLibraryDesugaring)

    // Testing
    // Google Play Services (location only — no billing required)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // OpenStreetMap — osmdroid (free, no API key)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Retrofit & Network (For Spotify API Integration)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Cloudinary for Media Uploads
    implementation("com.cloudinary:cloudinary-android:2.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
