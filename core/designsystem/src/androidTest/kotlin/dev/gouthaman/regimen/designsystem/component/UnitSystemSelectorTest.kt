package dev.gouthaman.regimen.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.gouthaman.regimen.domain.model.UnitSystem
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class UnitSystemSelectorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersWeightLabelsWhenWeightLabelsIsTrue() {
        composeTestRule.setContent {
            UnitSystemSelector(selected = UnitSystem.METRIC, onChange = {}, weightLabels = true)
        }

        composeTestRule.onNodeWithText("Metric (kg)").assertExists()
        composeTestRule.onNodeWithText("Imperial (lb)").assertExists()
    }

    @Test
    fun rendersDistanceLabelsWhenWeightLabelsIsFalse() {
        composeTestRule.setContent {
            UnitSystemSelector(selected = UnitSystem.METRIC, onChange = {}, weightLabels = false)
        }

        composeTestRule.onNodeWithText("Metric (km)").assertExists()
        composeTestRule.onNodeWithText("Imperial (mi)").assertExists()
    }

    @Test
    fun selectedOptionIsMarkedSelected() {
        composeTestRule.setContent {
            UnitSystemSelector(selected = UnitSystem.IMPERIAL, onChange = {}, weightLabels = true)
        }

        composeTestRule.onNodeWithText("Metric (kg)").assertIsNotSelected()
        composeTestRule.onNodeWithText("Imperial (lb)").assertIsSelected()
    }

    @Test
    fun clickingAnOptionInvokesOnChange() {
        var changedTo: UnitSystem? = null
        composeTestRule.setContent {
            UnitSystemSelector(
                selected = UnitSystem.METRIC,
                onChange = { changedTo = it },
                weightLabels = true
            )
        }

        composeTestRule.onNodeWithText("Imperial (lb)").performClick()

        assert(changedTo == UnitSystem.IMPERIAL)
    }
}
