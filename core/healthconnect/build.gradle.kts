plugins {
    id("regimen.android.library")
    id("regimen.android.hilt")
}

android {
    namespace = "dev.gouthaman.regimen.healthconnect"
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect.client)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
