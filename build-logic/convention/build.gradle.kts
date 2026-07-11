plugins {
    `kotlin-dsl`
}

group = "dev.gouthaman.regimen.buildlogic"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.compiler.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
}
