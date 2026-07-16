plugins {
    id("regimen.android.library")
}

android {
    namespace = "dev.gouthaman.regimen.testingandroid"
}

dependencies {
    api(libs.mockk)
    api(libs.junit)
}
