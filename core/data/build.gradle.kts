plugins {
    id("regimen.android.library")
    id("regimen.android.hilt")
    id("regimen.android.instrumented-test")
}

android {
    namespace = "dev.gouthaman.regimen.data"

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
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

    implementation(libs.androidx.health.connect.client)

    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
}
