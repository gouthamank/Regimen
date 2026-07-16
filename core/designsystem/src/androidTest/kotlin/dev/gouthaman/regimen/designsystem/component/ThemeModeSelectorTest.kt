package dev.gouthaman.regimen.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.gouthaman.regimen.domain.model.ThemeMode
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class ThemeModeSelectorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersEveryThemeModeOption() {
        composeTestRule.setContent {
            ThemeModeSelector(selected = ThemeMode.SYSTEM, onChange = {})
        }

        composeTestRule.onNodeWithText("Light").assertExists()
        composeTestRule.onNodeWithText("Dark").assertExists()
        composeTestRule.onNodeWithText("System").assertExists()
    }

    @Test
    fun selectedOptionIsMarkedSelected() {
        composeTestRule.setContent {
            ThemeModeSelector(selected = ThemeMode.DARK, onChange = {})
        }

        composeTestRule.onNodeWithText("Dark").assertIsSelected()
        composeTestRule.onNodeWithText("Light").assertIsNotSelected()
        composeTestRule.onNodeWithText("System").assertIsNotSelected()
    }

    @Test
    fun clickingAnOptionInvokesOnChange() {
        var changedTo: ThemeMode? = null
        composeTestRule.setContent {
            ThemeModeSelector(selected = ThemeMode.SYSTEM, onChange = { changedTo = it })
        }

        composeTestRule.onNodeWithText("Dark").performClick()

        assert(changedTo == ThemeMode.DARK)
    }
}
