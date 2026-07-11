import com.android.build.gradle.LibraryExtension

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
