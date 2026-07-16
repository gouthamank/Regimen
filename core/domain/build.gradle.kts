plugins {
    id("regimen.jvm.library")
    id("regimen.jvm.test")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(project(":core:testing"))
}
