plugins {
    id("regimen.jvm.library")
    id("regimen.jvm.test")
}

dependencies {
    api(project(":core:domain"))

    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.mockk)
    implementation(libs.kotlinx.coroutines.core)
}
