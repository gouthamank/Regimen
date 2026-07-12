plugins {
    id("regimen.android.library")
    id("regimen.android.hilt")
}

android {
    namespace = "dev.gouthaman.regimen.data"
}

ksp {
    // Room schema history, for real migrations (see data/local/migration/Migrations.kt).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
}
