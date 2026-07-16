import dev.gouthaman.regimen.buildlogic.libs

plugins {
    id("com.android.library")
}

android {
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        // AGP's project-wide default here is this same class whether declared or not, but every
        // module still produces its own (often source-less) androidTest APK that needs the class
        // on its own classpath, or connectedAndroidTest fails with ClassNotFoundException trying
        // to launch it - hence the matching androidTestImplementation below, added to every module.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    "testImplementation"(libs.findLibrary("junit").get())
    "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
    "testImplementation"(libs.findLibrary("turbine").get())
    "testImplementation"(libs.findLibrary("mockk").get())
    "androidTestImplementation"(libs.findLibrary("androidx-test-runner").get())
}

// MockK's mockkConstructor/mockkStatic self-attach the inline-mocking agent on modern JDKs,
// which requires this flag (used by FakeBundleRule in :core:testing, needed by any ViewModel
// test whose SavedStateHandle.toRoute() bridges through a real android.os.Bundle).
tasks.withType<Test>().configureEach {
    jvmArgs("-Djdk.attach.allowAttachSelf=true")
}
