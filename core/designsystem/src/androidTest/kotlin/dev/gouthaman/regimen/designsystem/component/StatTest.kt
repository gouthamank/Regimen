package dev.gouthaman.regimen.designsystem.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class StatTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersLabelAndValue() {
        composeTestRule.setContent {
            Stat(label = "Workouts", value = "12")
        }

        composeTestRule.onNodeWithText("12").assertExists()
        composeTestRule.onNodeWithText("Workouts").assertExists()
    }
}
