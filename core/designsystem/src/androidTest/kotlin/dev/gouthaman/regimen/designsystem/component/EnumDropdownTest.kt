package dev.gouthaman.regimen.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class EnumDropdownTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val options = listOf("Off", "Four", "Six")

    @Test
    fun rendersSelectedValue() {
        composeTestRule.setContent {
            EnumDropdown(
                label = "Duration",
                options = options,
                selected = "Four",
                optionLabel = { it },
                onSelect = {},
            )
        }

        composeTestRule.onNodeWithText("Four").assertExists()
    }

    @Test
    fun tappingFieldExpandsMenuWithEveryOption() {
        composeTestRule.setContent {
            EnumDropdown(
                label = "Duration",
                options = options,
                selected = "Off",
                optionLabel = { it },
                onSelect = {},
            )
        }

        composeTestRule.onNodeWithText("Off").performClick()

        composeTestRule.onNodeWithText("Six").assertExists()
    }

    @Test
    fun tappingAnOptionInvokesOnSelectAndCloses() {
        var selected: String? = null
        composeTestRule.setContent {
            EnumDropdown(
                label = "Duration",
                options = options,
                selected = "Off",
                optionLabel = { it },
                onSelect = { selected = it },
            )
        }

        composeTestRule.onNodeWithText("Off").performClick()
        composeTestRule.onNodeWithText("Six").performClick()

        assert(selected == "Six")
        composeTestRule.onNodeWithText("Four").assertDoesNotExist()
    }
}
