package dev.gouthaman.regimen.designsystem.dialog

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ConfirmDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersTitleAndText() {
        composeTestRule.setContent {
            ConfirmDialog(
                title = "Delete workout?",
                text = "This can't be undone.",
                confirmLabel = "Delete",
                onConfirm = {},
                onDismiss = {},
                dismissLabel = "Cancel",
            )
        }

        composeTestRule.onNodeWithText("Delete workout?").assertExists()
        composeTestRule.onNodeWithText("This can't be undone.").assertExists()
    }

    @Test
    fun dismissButtonHiddenWhenNoDismissLabelProvided() {
        composeTestRule.setContent {
            ConfirmDialog(
                title = "Finish workout?",
                text = "You're about to finish.",
                confirmLabel = "Finish",
                onConfirm = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Finish").assertExists()
    }

    @Test
    fun confirmClickInvokesOnConfirm() {
        var confirmed = false
        composeTestRule.setContent {
            ConfirmDialog(
                title = "Delete workout?",
                text = "This can't be undone.",
                confirmLabel = "Delete",
                onConfirm = { confirmed = true },
                onDismiss = {},
                dismissLabel = "Cancel",
                destructive = true,
            )
        }

        composeTestRule.onNodeWithText("Delete").performClick()

        assert(confirmed)
    }

    @Test
    fun dismissClickInvokesOnDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            ConfirmDialog(
                title = "Delete workout?",
                text = "This can't be undone.",
                confirmLabel = "Delete",
                onConfirm = {},
                onDismiss = { dismissed = true },
                dismissLabel = "Cancel",
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assert(dismissed)
    }

    @Test
    fun confirmDisabledUntilDelayElapsesThenClickable() {
        composeTestRule.mainClock.autoAdvance = false
        var confirmed = false
        composeTestRule.setContent {
            ConfirmDialog(
                title = "Finish workout?",
                text = "Some sets aren't complete.",
                confirmLabel = "Finish",
                onConfirm = { confirmed = true },
                onDismiss = {},
                confirmEnableDelayMillis = 1000,
            )
        }

        composeTestRule.onNodeWithText("Finish").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Finish").performClick()
        assert(!confirmed)

        composeTestRule.mainClock.advanceTimeBy(1000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Finish").assertIsEnabled()
        composeTestRule.onNodeWithText("Finish").performClick()
        assert(confirmed)
    }
}
