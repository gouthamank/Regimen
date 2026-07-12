plugins {
    id("regimen.android.library.compose")
    id("regimen.android.hilt")
}

dependencies {
    "implementation"(project(":core:domain"))
    "implementation"(project(":core:designsystem"))
    "implementation"(project(":core:navigation-api"))
}
