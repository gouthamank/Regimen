import com.android.build.gradle.LibraryExtension

plugins {
    id("regimen.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<LibraryExtension> {
    buildFeatures {
        compose = true
    }
}

dependencies {
    "implementation"(platform(libs.androidx.compose.bom))
}
