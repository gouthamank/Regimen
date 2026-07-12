plugins {
    id("regimen.android.library.compose")
}

android {
    namespace = "dev.gouthaman.regimen.designsystem"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.window)
    implementation(libs.androidx.compose.material3.adaptive)
}
