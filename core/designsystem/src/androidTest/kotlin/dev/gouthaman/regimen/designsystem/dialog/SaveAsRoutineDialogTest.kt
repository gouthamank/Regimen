package dev.gouthaman.regimen.designsystem.dialog

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import org.junit.Rule
import org.junit.Test

class SaveAsRoutineDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersTitleDialogTextAndDefaultName() {
        composeTestRule.setContent {
            SaveAsRoutineDialog(
                title = "Save as routine",
                dialogText = "Give this routine a name.",
                nameLabel = "Name",
                saveLabel = "Save",
                cancelLabel = "Cancel",
                defaultName = "Push Day",
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithText("Save as routine").assertExists()
        composeTestRule.onNodeWithText("Give this routine a name.").assertExists()
        composeTestRule.onNodeWithText("Push Day").assertExists()
    }

    @Test
    fun saveButtonDisabledWhenNameBlank() {
        composeTestRule.setContent {
            SaveAsRoutineDialog(
                title = "Save as routine",
                dialogText = "Give this routine a name.",
                nameLabel = "Name",
                saveLabel = "Save",
                cancelLabel = "Cancel",
                defaultName = "Push Day",
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithText("Push Day").performTextClearance()

        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun saveButtonEnabledWithNonBlankNameAndConfirmsTrimmed() {
        var savedName: String? = null
        composeTestRule.setContent {
            SaveAsRoutineDialog(
                title = "Save as routine",
                dialogText = "Give this routine a name.",
                nameLabel = "Name",
                saveLabel = "Save",
                cancelLabel = "Cancel",
                defaultName = "Push Day",
                onDismiss = {},
                onConfirm = { savedName = it },
            )
        }

        composeTestRule.onNodeWithText("Push Day").performTextReplacement("  Leg Day  ")
        composeTestRule.onNodeWithText("Save").assertIsEnabled()
        composeTestRule.onNodeWithText("Save").performClick()

        assert(savedName == "Leg Day")
    }

    @Test
    fun cancelClickInvokesOnDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            SaveAsRoutineDialog(
                title = "Save as routine",
                dialogText = "Give this routine a name.",
                nameLabel = "Name",
                saveLabel = "Save",
                cancelLabel = "Cancel",
                defaultName = "Push Day",
                onDismiss = { dismissed = true },
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assert(dismissed)
    }

    @Test
    fun typingAppendsToTheDefaultName() {
        composeTestRule.setContent {
            SaveAsRoutineDialog(
                title = "Save as routine",
                dialogText = "Give this routine a name.",
                nameLabel = "Name",
                saveLabel = "Save",
                cancelLabel = "Cancel",
                defaultName = "Push",
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithText("Push").performTextInputSelection(TextRange("Push".length))
        composeTestRule.onNodeWithText("Push").performTextInput(" Day")

        composeTestRule.onNodeWithText("Push Day").assertExists()
    }
}
