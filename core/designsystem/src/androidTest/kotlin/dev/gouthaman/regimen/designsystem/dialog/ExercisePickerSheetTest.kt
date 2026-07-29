package dev.gouthaman.regimen.designsystem.dialog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class ExercisePickerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val benchPress =
        Exercise("1", "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val squat =
        Exercise("2", "Squat", ExerciseType.STRENGTH, MuscleGroup.LEGS, Equipment.BARBELL)
    private val running =
        Exercise("3", "Running", ExerciseType.CARDIO, MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE)

    @Test
    fun rendersEveryProvidedExercise() {
        composeTestRule.setContent {
            ExercisePickerSheet(
                exercises = listOf(benchPress, squat, running),
                onConfirm = {},
                onDismiss = {},
                onCreateCustom = {},
            )
        }

        composeTestRule.onNodeWithText("Bench Press").assertExists()
        composeTestRule.onNodeWithText("Squat").assertExists()
        composeTestRule.onNodeWithText("Running").assertExists()
    }

    @Test
    fun searchQueryFiltersTheList() {
        composeTestRule.setContent {
            ExercisePickerSheet(
                exercises = listOf(benchPress, squat, running),
                onConfirm = {},
                onDismiss = {},
                onCreateCustom = {},
            )
        }

        composeTestRule.onNodeWithText("Search").performTextInput("bench")

        composeTestRule.onNodeWithText("Bench Press").assertExists()
        composeTestRule.onNodeWithText("Squat").assertDoesNotExist()
        composeTestRule.onNodeWithText("Running").assertDoesNotExist()
    }

    @Test
    fun saveButtonDisabledUntilAnExerciseIsChecked() {
        composeTestRule.setContent {
            ExercisePickerSheet(
                exercises = listOf(benchPress, squat),
                onConfirm = {},
                onDismiss = {},
                onCreateCustom = {},
            )
        }

        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()

        composeTestRule.onNodeWithText("Bench Press").performClick()

        composeTestRule.onNodeWithText("Save (1)").assertIsEnabled()
    }

    @Test
    fun confirmingReturnsTheCheckedExerciseIds() {
        var confirmedIds: List<String>? = null
        composeTestRule.setContent {
            ExercisePickerSheet(
                exercises = listOf(benchPress, squat),
                onConfirm = { confirmedIds = it },
                onDismiss = {},
                onCreateCustom = {},
            )
        }

        composeTestRule.onNodeWithText("Bench Press").performClick()
        composeTestRule.onNodeWithText("Squat").performClick()
        composeTestRule.onNodeWithText("Save (2)").performClick()

        assert(confirmedIds?.toSet() == setOf(benchPress.id, squat.id))
    }

    @Test
    fun initiallySelectedExercisesStaySelectedAndVisibleUnderASearchFilter() {
        composeTestRule.setContent {
            ExercisePickerSheet(
                exercises = listOf(benchPress, squat, running),
                onConfirm = {},
                onDismiss = {},
                onCreateCustom = {},
                initiallySelected = setOf(squat.id),
            )
        }

        composeTestRule.onNodeWithText("Save (1)").assertExists()

        composeTestRule.onNodeWithText("Search").performTextInput("bench")

        composeTestRule.onNodeWithText("Bench Press").assertExists()
        composeTestRule.onNodeWithText("Squat").assertExists()
    }

    @Test
    fun createCustomRowClickInvokesOnCreateCustom() {
        var created = false
        composeTestRule.setContent {
            ExercisePickerSheet(
                exercises = listOf(benchPress),
                onConfirm = {},
                onDismiss = {},
                onCreateCustom = { created = true },
            )
        }

        composeTestRule.onNodeWithText("Create custom exercise").performClick()

        assert(created)
    }
}
