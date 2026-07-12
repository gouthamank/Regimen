plugins {
    id("regimen.android.library.compose")
}

android {
    namespace = "dev.gouthaman.regimen.common"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.compose.ui)
}
