import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidTest)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.roserequiem.app.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Macrobenchmark needs 28+ regardless of the app's own minSdk (now 23) --
        // this only affects the device this test module runs on, not the app's
        // published minSdk.
        minSdk = 28
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    // A Gradle Managed Device so `generateBaselineProfile` can spin up an emulator
    // itself instead of requiring a device plugged into this machine. NOTE: this
    // needs hardware virtualization (a PC/Mac running real Android Studio, or CI),
    // which Android Code Studio running on-device cannot provide -- see the run
    // instructions in BaselineProfileGenerator.kt.
    testOptions.managedDevices.devices {
        create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
