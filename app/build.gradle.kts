import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.roserequiem.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.roserequiem.app"
        // Was 21. AndroidX itself moved its default minSdk from 21 to 23 across the
        // board starting mid-2025 (activity, and others) -- staying on 21 would now mean
        // pinning every AndroidX dependency to a year-plus-old release to avoid manifest
        // merger failures. Android 5.0/5.1 share is negligible by now regardless.
        minSdk = 23
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 433
        versionName = "4.3.3"

        vectorDrawables {
            useSupportLibrary = true
        }
    }
    androidResources {
        generateLocaleConfig = true
    }
    signingConfigs {
        create("release") {
            if (System.getenv("RELEASE_STORE_FILE") != null) {
                storeFile = file(System.getenv("RELEASE_STORE_FILE"))
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (System.getenv("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs["release"]
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // No composeOptions.kotlinCompilerExtensionVersion here on purpose: that setting
    // is for the old standalone Compose Compiler artifact. This project uses the
    // Compose Compiler Gradle plugin (see the `compose.compiler` plugin alias below,
    // tied to the Kotlin version in libs.versions.toml), which supersedes it entirely --
    // leaving the old 1.5.14 pin in place alongside the plugin was inert at best and
    // a source of confusion about which one actually applied.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.preference)
    implementation(libs.ktor.core)
    implementation(libs.ktor.cio)
    implementation(libs.taglib)
    implementation(libs.kotlin.onetimepassword)
    implementation(libs.datastore.preferences)
    implementation(libs.ui.tooling) //NOT RECOMMENDED
    implementation(libs.ui.tooling.preview) //NOT RECOMMENDED
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Lets a locally-generated Baseline Profile actually be installed/used on-device,
    // and is required for API < 29 / devices without Play Services baseline delivery.
    implementation(libs.androidx.profileinstaller)
    // Points the plugin at the module that generates the profile (see :baselineprofile).
    baselineProfile(project(":baselineprofile"))
}
