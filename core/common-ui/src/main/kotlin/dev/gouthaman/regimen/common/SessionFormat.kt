package dev.gouthaman.regimen.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.gouthaman.regimen.common.SessionFormat.time
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.util.UnitConverter
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** Human-readable labels for a past session, shared by the history calendar and session detail. */
object SessionFormat {

    private fun dayFormatter() = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
    private fun shortDateFormatter() = SimpleDateFormat("MMM d", Locale.getDefault())

    fun fullDate(millis: Long): String = dayFormatter().format(millis)

    /** Time of day, honoring the system's 12h/24h clock preference (Settings > Date & time). */
    @Composable
    fun time(millis: Long): String {
        val context = LocalContext.current
        val formatter = remember(context) { DateFormat.getTimeFormat(context) }
        return formatter.format(Date(millis))
    }

    /** [time], prefixed with a short date ("MMM d") when [millis] isn't today - for lists that
     * can span multiple days (e.g. a "recent workouts" list), where time alone would be ambiguous. */
    @Composable
    fun timeWithDateIfNotToday(millis: Long): String {
        val timeText = time(millis)
        val isToday = remember(millis) {
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() ==
                    LocalDate.now()
        }
        return if (isToday) {
            timeText
        } else {
            stringResource(
                R.string.session_format_date_time,
                shortDateFormatter().format(Date(millis)),
                timeText
            )
        }
    }

    /** "45 min", "1h 05m", or "-" when the session has no recorded end. Excludes paused time. */
    @Composable
    fun duration(startMillis: Long, endMillis: Long?, pausedMs: Long = 0): String {
        if (endMillis == null) return stringResource(R.string.session_format_placeholder_dash)
        val totalMin = ((endMillis - startMillis - pausedMs) / 60_000L).coerceAtLeast(0)
        val hours = totalMin / 60
        val minutes = totalMin % 60
        return if (hours > 0) {
            stringResource(R.string.session_format_duration_hours_minutes, hours, minutes)
        } else {
            stringResource(R.string.session_format_duration_minutes, minutes)
        }
    }

    /** One logged strength set, e.g. "60 kg × 8", "8 reps" (bodyweight), or "60 kg" (no reps). */
    @Composable
    fun setLabel(set: SetEntry, weightUnit: UnitSystem): String {
        val weight = set.weightKg?.let {
            stringResource(
                R.string.session_format_weight_value,
                UnitConverter.formatValue(UnitConverter.kgToDisplay(it, weightUnit)),
                UnitConverter.weightLabel(weightUnit).text(),
            )
        }
        val repsValue = set.reps
        val reps =
            repsValue?.let { pluralStringResource(R.plurals.session_format_reps_count, it, it) }
        return when {
            weight != null && repsValue != null ->
                stringResource(R.string.session_format_weight_reps, weight, repsValue)

            weight != null -> weight
            reps != null -> reps
            else -> stringResource(R.string.session_format_placeholder_dash)
        }
    }

    /** A cardio bout, e.g. "12:30" or "12:30 · 3.2 km". */
    @Composable
    fun cardioLabel(cardio: CardioEntry, distanceUnit: UnitSystem): String {
        val minutes = cardio.durationSec / 60
        val seconds = cardio.durationSec % 60
        val time = "%d:%02d".format(minutes, seconds)
        val distance = cardio.distanceMeters?.let {
            stringResource(
                R.string.session_format_distance_value,
                UnitConverter.formatValue(UnitConverter.metersToDisplay(it, distanceUnit)),
                UnitConverter.distanceLabel(distanceUnit).text(),
            )
        }
        return if (distance != null) stringResource(
            R.string.session_format_time_distance,
            time,
            distance
        ) else time
    }
}
