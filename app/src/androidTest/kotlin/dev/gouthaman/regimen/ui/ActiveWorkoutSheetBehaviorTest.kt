package dev.gouthaman.regimen.ui

import android.Manifest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.gouthaman.regimen.MainActivity
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Drives the persistent [ActiveWorkoutSheet]: start a workout, collapse it, switch tabs while
 * collapsed, re-expand, then switch tabs while expanded (which should collapse it first rather
 * than leaving it stuck on top). Runs against a real Hilt graph with an in-memory Room database
 * ([TestDatabaseModule]), exercising the whole chain including the real foreground service.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutSheetBehaviorTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // Pre-granted so RegimenApp's own POST_NOTIFICATIONS request (fired right after onboarding)
    // never pops a system dialog mid-test - that dialog would otherwise steal focus and block
    // every subsequent Compose interaction.
    @get:Rule(order = 2)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @Inject
    lateinit var database: RegimenDatabase

    @Before
    fun seedRoutine() {
        hiltRule.inject()
        // One routine with no exercises is enough to get Home past its empty state and make
        // "Start Workout" reachable - this test is about the sheet's mount/collapse/expand
        // mechanics, not exercise logging.
        runBlocking {
            database.routineDao()
                .insertRoutine(RoutineEntity(id = "0", name = "Push Day", position = 0))
        }
    }

    @Test
    fun emptyThenStartCollapseSwitchTabsExpand() {
        skipOnboardingIfShown()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Start Workout").fetchSemanticsNodes().isNotEmpty()
        }

        // Empty: no workout in progress yet, so no collapsed banner.
        composeTestRule.onAllNodesWithText("Workout in progress").assertCountEquals(0)

        // Start a workout from the seeded routine.
        composeTestRule.onNodeWithText("Start Workout").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Push Day").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Push Day").performClick()

        // Starting from Home auto-expands the sheet - full workout content should be showing
        // (the seeded routine has no exercises, so its empty-state message is a reliable,
        // unambiguous signal that we're looking at the expanded sheet, not just the banner).
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("No exercises yet. Add one to start logging.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Collapse via back press (BackHandler only fires while Expanded and on a top-level tab).
        Espresso.pressBack()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Workout in progress").fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Workout in progress").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("No exercises yet. Add one to start logging.")
            .assertCountEquals(0)

        // Switching tabs while collapsed: the banner persists across every top-level tab.
        composeTestRule.onNodeWithText("History").performClick()
        composeTestRule.onNodeWithText("Workout in progress").assertIsDisplayed()

        composeTestRule.onNodeWithText("Progress").performClick()
        composeTestRule.onNodeWithText("Workout in progress").assertIsDisplayed()

        // Tapping the banner re-expands it.
        composeTestRule.onNodeWithText("Workout in progress").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("No exercises yet. Add one to start logging.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Switching tabs while expanded collapses the sheet first, rather than leaving the
        // full-screen workout content stuck on top of whatever tab was just tapped.
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Workout in progress").fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("No exercises yet. Add one to start logging.")
            .assertCountEquals(0)
    }

    /** Onboarding's completion is a persisted preference, not reset by [TestDatabaseModule] (that
     * only replaces the Room database) - it may or may not still be showing depending on whether
     * a previous run already completed it on this device/AVD. "Skip" is always present and
     * available regardless of which onboarding page is showing. */
    private fun skipOnboardingIfShown() {
        // Waits through the splash screen's own loading gate (prefs load async, before either
        // Onboarding or Home ever composes) rather than just waitForIdle(), which can return
        // before that first real content shows up.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() ||
                    composeTestRule.onAllNodesWithText("Start Workout").fetchSemanticsNodes()
                        .isNotEmpty()
        }
        val skip = composeTestRule.onAllNodesWithText("Skip").fetchSemanticsNodes()
        if (skip.isNotEmpty()) {
            composeTestRule.onNodeWithText("Skip").performClick()
        }
    }
}
