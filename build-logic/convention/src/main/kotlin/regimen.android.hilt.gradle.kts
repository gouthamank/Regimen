plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    "implementation"(libs.hilt.android)
    "ksp"(libs.hilt.compiler)
}
