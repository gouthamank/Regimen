import dev.gouthaman.regimen.buildlogic.libs

dependencies {
    "testImplementation"(libs.findLibrary("junit").get())
    "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
    "testImplementation"(libs.findLibrary("turbine").get())
}
