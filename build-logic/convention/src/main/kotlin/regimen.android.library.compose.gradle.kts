import com.android.build.gradle.LibraryExtension
import dev.gouthaman.regimen.buildlogic.libs

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
    "implementation"(platform(libs.findLibrary("androidx-compose-bom").get()))
}
