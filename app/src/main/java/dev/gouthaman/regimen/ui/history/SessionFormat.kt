package dev.gouthaman.regimen.ui.history

import dev.gouthaman.regimen.data.local.entity.CardioEntry
import dev.gouthaman.regimen.data.local.entity.SetEntry
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.util.UnitConverter
import java.text.SimpleDateFormat
import java.util.Locale

/** Human-readable labels for a past session, shared by the history calendar and session detail. */
object SessionFormat {

    private val dayFormatter = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun fullDate(millis: Long): String = dayFormatter.format(millis)
    fun time(millis: Long): String = timeFormatter.format(millis)

    /** "45 min", "1h 05m", or "—" when the session has no recorded end. Excludes paused time. */
    fun duration(startMillis: Long, endMillis: Long?, pausedMs: Long = 0): String {
        if (endMillis == null) return "—"
        val totalMin = ((endMillis - startMillis - pausedMs) / 60_000L).coerceAtLeast(0)
        val hours = totalMin / 60
        val minutes = totalMin % 60
        return if (hours > 0) "${hours}h %02dm".format(minutes) else "$minutes min"
    }

    /** One logged strength set, e.g. "60 kg × 8", "8 reps" (bodyweight), or "60 kg" (no reps). */
    fun setLabel(set: SetEntry, weightUnit: UnitSystem): String {
        val weight = set.weightKg?.let {
            "${
                UnitConverter.formatValue(
                    UnitConverter.kgToDisplay(
                        it,
                        weightUnit
                    )
                )
            } ${UnitConverter.weightLabel(weightUnit)}"
        }
        val reps = set.reps?.let { "$it reps" }
        return when {
            weight != null && set.reps != null ->
                "$weight × ${set.reps}"

            weight != null -> weight
            reps != null -> reps
            else -> "—"
        }
    }

    /** A cardio bout, e.g. "12:30" or "12:30 · 3.2 km". */
    fun cardioLabel(cardio: CardioEntry, distanceUnit: UnitSystem): String {
        val minutes = cardio.durationSec / 60
        val seconds = cardio.durationSec % 60
        val time = "%d:%02d".format(minutes, seconds)
        val distance = cardio.distanceMeters?.let {
            "${
                UnitConverter.formatValue(
                    UnitConverter.metersToDisplay(
                        it,
                        distanceUnit
                    )
                )
            } ${UnitConverter.distanceLabel(distanceUnit)}"
        }
        return if (distance != null) "$time · $distance" else time
    }
}
