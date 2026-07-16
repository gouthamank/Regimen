package dev.gouthaman.regimen.feature.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType

/**
 * The type/equipment icon badge shown for an exercise in the Exercise Library - reused wherever
 * else an exercise needs the same at-a-glance glyph (e.g. Session Detail's exercise cards).
 */
@Composable
fun ExerciseIcon(type: ExerciseType, equipment: Equipment, modifier: Modifier = Modifier) {
    val (container, onContainer) = when (type) {
        ExerciseType.STRENGTH -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer

        ExerciseType.CARDIO -> MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .background(color = container, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            equipmentIcon(equipment),
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** No Material icon distinguishes barbell/dumbbell/kettlebell individually, so free weights share one glyph. */
internal fun equipmentIcon(equipment: Equipment) = when (equipment) {
    Equipment.BARBELL, Equipment.DUMBBELL, Equipment.KETTLEBELL -> Icons.Filled.FitnessCenter
    Equipment.MACHINE -> Icons.Filled.PrecisionManufacturing
    Equipment.CABLE -> Icons.Filled.Cable
    Equipment.BODYWEIGHT -> Icons.Filled.SelfImprovement
    Equipment.CARDIO_MACHINE -> Icons.Filled.DirectionsRun
    Equipment.OTHER -> Icons.Filled.Category
}
