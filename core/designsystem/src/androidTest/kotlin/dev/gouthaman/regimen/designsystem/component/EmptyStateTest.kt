package dev.gouthaman.regimen.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class EmptyStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersTheMessage() {
        composeTestRule.setContent {
            EmptyState(message = "No workouts yet")
        }

        composeTestRule.onNodeWithText("No workouts yet").assertExists()
    }

    @Test
    fun rendersWithAnIconAndNoActionByDefault() {
        composeTestRule.setContent {
            EmptyState(message = "No results", icon = Icons.Filled.Search)
        }

        composeTestRule.onNodeWithText("No results").assertExists()
    }

    @Test
    fun actionButtonHiddenWhenActionLabelIsNull() {
        composeTestRule.setContent {
            EmptyState(message = "No routines yet")
        }

        composeTestRule.onNodeWithText("Create routine").assertDoesNotExist()
    }

    @Test
    fun actionButtonClickInvokesOnAction() {
        var clicked = false
        composeTestRule.setContent {
            EmptyState(
                message = "No routines yet",
                actionLabel = "Create routine",
                onAction = { clicked = true },
            )
        }

        composeTestRule.onNodeWithText("Create routine").performClick()

        assert(clicked)
    }
}
