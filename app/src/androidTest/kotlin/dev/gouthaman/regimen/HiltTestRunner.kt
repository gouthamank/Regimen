package dev.gouthaman.regimen

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/** Swaps in [HiltTestApplication] so `@HiltAndroidTest`-annotated instrumentation tests get a
 * Hilt-testable app component - required for any androidTest that injects real (or
 * `@TestInstallIn`-overridden) Hilt dependencies, e.g. via `createAndroidComposeRule<MainActivity>()`. */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
