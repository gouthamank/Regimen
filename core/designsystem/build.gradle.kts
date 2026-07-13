plugins {
    id("regimen.android.library.compose")
}

android {
    namespace = "dev.gouthaman.regimen.designsystem"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common-ui"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.window)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.core)
}
