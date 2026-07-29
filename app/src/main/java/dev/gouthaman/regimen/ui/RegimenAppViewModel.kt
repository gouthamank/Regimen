package dev.gouthaman.regimen.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.usecase.ObserveActiveWorkoutIdUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-shell state: the id of the in-progress workout (if any), backing the "resume" banner. */
@HiltViewModel
class RegimenAppViewModel @Inject constructor(
    observeActiveWorkoutId: ObserveActiveWorkoutIdUseCase,
) : ViewModel() {
    val inProgressWorkoutId: StateFlow<String?> =
        observeActiveWorkoutId().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null
        )
}
