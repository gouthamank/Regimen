import dev.gouthaman.regimen.buildlogic.libs

dependencies {
    "androidTestImplementation"(libs.findLibrary("androidx-test-core").get())
    "androidTestImplementation"(libs.findLibrary("androidx-junit").get())
    "androidTestImplementation"(libs.findLibrary("androidx-room-testing").get())
}
