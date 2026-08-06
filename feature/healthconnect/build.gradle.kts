plugins {
    id("regimen.android.feature")
}

android {
    namespace = "dev.gouthaman.regimen.feature.healthconnect"
}

dependencies {
    implementation(project(":core:common-ui"))

    // Only for the PermissionController.createRequestPermissionResultContract() launcher this
    // screen owns - never talks to HealthConnectClient itself, that's :core:healthconnect's job.
    implementation(libs.androidx.health.connect.client)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(project(":core:testing"))
}
