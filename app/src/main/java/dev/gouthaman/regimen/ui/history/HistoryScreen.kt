package dev.gouthaman.regimen.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.R
import dev.gouthaman.regimen.designsystem.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.RegimenPosture
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
fun HistoryScreen(
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(uiState = uiState, onOpenSession = onOpenSession, modifier = modifier)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable (not remember) so the visible month survives navigating to a session's detail and
    // back — composable<Route> disposes this screen while Session Detail is on top, and remember
    // would reset to the current month on return.
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    // A day tapped with more than one session surfaces a picker dialog. Stored as an explicit
    // ArrayList since Bundle-backed rememberSaveable needs the list container itself to be
    // Serializable too, not just its elements (DaySession is Serializable).
    var pickerDay by rememberSaveable { mutableStateOf<ArrayList<DaySession>?>(null) }
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        // BookOrExpanded caps and centers the calendar at the same 600dp breakpoint as
        // Onboarding/Routines — a 7-column grid stretched full-bleed on a wide window would
        // blow up each day cell far beyond a useful tap target. Compact/Tabletop unchanged.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentModifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                Modifier
                    .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                    .fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }
            Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                MonthHeader(
                    month = month,
                    canGoNext = month.isBefore(YearMonth.now()),
                    onPrev = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                )
                WeekdayHeader()
                MonthGrid(
                    month = month,
                    sessionsByDay = uiState.sessionsByDay,
                    onDayClick = { sessions ->
                        if (sessions.size == 1) onOpenSession(sessions.first().workoutId)
                        else pickerDay = ArrayList(sessions)
                    },
                )

                if (uiState.loaded && uiState.isEmpty) {
                    Text(
                        stringResource(R.string.history_empty_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                    )
                }
            }
        }
    }

    val sessions = pickerDay
    if (sessions != null) {
        AlertDialog(
            onDismissRequest = { pickerDay = null },
            title = { Text(stringResource(R.string.history_choose_session_dialog_title)) },
            text = {
                Column {
                    sessions.forEach { session ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    session.routineName
                                        ?: stringResource(R.string.history_quick_workout_fallback)
                                )
                            },
                            supportingContent = { Text(SessionFormat.time(session.startMillis)) },
                            modifier = Modifier.clickable {
                                pickerDay = null
                                onOpenSession(session.workoutId)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pickerDay = null
                }) { Text(stringResource(R.string.history_session_picker_cancel_button)) }
            },
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.history_previous_month_description)
            )
        }
        Text(
            "${
                month.month.getDisplayName(
                    TextStyle.FULL,
                    LocalLocale.current.platformLocale
                )
            } ${month.year}",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        // Can't page into future months — there's nothing there to see yet.
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.history_next_month_description)
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val firstDay = WeekFields.of(LocalLocale.current.platformLocale).firstDayOfWeek
    val days = (0 until 7).map { firstDay.plus(it.toLong()) }
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Text(
                day.getDisplayName(TextStyle.NARROW, LocalLocale.current.platformLocale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    sessionsByDay: Map<LocalDate, List<DaySession>>,
    onDayClick: (List<DaySession>) -> Unit,
) {
    val firstDayOfWeek = WeekFields.of(LocalLocale.current.platformLocale).firstDayOfWeek
    val firstOfMonth = month.atDay(1)
    // Blank leading cells so day 1 lands under the right weekday column.
    val leadingBlanks = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val daysInMonth = month.lengthOfMonth()
    val cells = buildList {
        repeat(leadingBlanks) { add(null) }
        for (d in 1..daysInMonth) add(month.atDay(d))
        while (size % 7 != 0) add(null)
    }
    val today = LocalDate.now()

    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isFuture = date.isAfter(today),
                                sessions = sessionsByDay[date].orEmpty(),
                                onClick = onDayClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isFuture: Boolean,
    sessions: List<DaySession>,
    onClick: (List<DaySession>) -> Unit,
) {
    // Future dates can't have a logged session yet — dim them so they read as not-yet-available, not just empty.
    val hasWorkout = sessions.isNotEmpty() && !isFuture
    var cell = Modifier
        .padding(4.dp)
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(CircleShape)
    cell = when {
        hasWorkout -> cell.background(MaterialTheme.colorScheme.primaryContainer)
        isToday -> cell.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        else -> cell
    }
    // Always clickable (enabled = hasWorkout) rather than conditionally adding the modifier — that's
    // how Compose exposes a proper disabled state to accessibility services (TalkBack), instead
    // of the node silently not existing.
    val viewSessionsLabel = stringResource(R.string.history_day_view_sessions_click_label)
    val notYetAvailableDescription =
        stringResource(R.string.history_day_not_yet_available_description, date.dayOfMonth)
    cell = cell
        .clickable(
            enabled = hasWorkout,
            onClickLabel = if (hasWorkout) viewSessionsLabel else null,
            role = Role.Button,
        ) { onClick(sessions) }
        .then(
            if (isFuture) {
                Modifier.semantics { contentDescription = notYetAvailableDescription }
            } else {
                Modifier
            }
        )

    Box(modifier = cell, contentAlignment = Alignment.Center) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (hasWorkout || isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                hasWorkout -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
