plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version: VERSION from /.versions; build number from CI (BUILD_NUMBER), 1 for local builds.
val shelfVersion: String = rootProject.file(".versions").readLines()
    .firstOrNull { it.startsWith("VERSION=") }?.substringAfter("=")?.trim() ?: "0.0.0"
val shelfBuild: Int = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "app.shelf.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.shelf.launcher"
        minSdk = 28
        targetSdk = 36 // behavior changes for newer targets only apply on newer OS versions; harmless on API 28
        versionCode = shelfBuild
        versionName = "$shelfVersion-b$shelfBuild"
    }

    // Release signing from CI secrets when present (keeps signatures stable so updates install over each
    // other); otherwise the local debug key.
    val ks = System.getenv("SHELF_KEYSTORE_PATH")
    signingConfigs {
        if (ks != null) create("release") {
            storeFile = file(ks)
            storePassword = System.getenv("SHELF_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SHELF_KEY_ALIAS")
            keyPassword = System.getenv("SHELF_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += setOf("META-INF/*.version", "META-INF/**/LICENSE*", "kotlin/**", "DebugProbesKt.bin")
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.palette:palette-ktx:1.1.0")

    testImplementation("junit:junit:4.13.2")
}
