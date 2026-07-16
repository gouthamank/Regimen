package dev.gouthaman.regimen.designsystem.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SectionHeaderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersTheProvidedText() {
        composeTestRule.setContent {
            SectionHeader(text = "Personal records")
        }

        composeTestRule.onNodeWithText("Personal records").assertExists()
    }
}
