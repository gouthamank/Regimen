package dev.gouthaman.regimen.designsystem.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class WorkoutInProgressBannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersMessageAndViewLabel() {
        composeTestRule.setContent {
            WorkoutInProgressBanner(
                message = "Push Day in progress",
                viewLabel = "View",
                onResume = {})
        }

        composeTestRule.onNodeWithText("Push Day in progress").assertExists()
        composeTestRule.onNodeWithText("View").assertExists()
    }

    @Test
    fun clickingTheBannerInvokesOnResume() {
        var resumed = false
        composeTestRule.setContent {
            WorkoutInProgressBanner(
                message = "Push Day in progress",
                viewLabel = "View",
                onResume = { resumed = true })
        }

        composeTestRule.onNodeWithText("Push Day in progress").performClick()

        assert(resumed)
    }
}
