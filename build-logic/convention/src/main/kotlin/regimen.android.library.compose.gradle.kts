import dev.gouthaman.regimen.buildlogic.libs

plugins {
    id("regimen.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    "implementation"(platform(libs.findLibrary("androidx-compose-bom").get()))
}
