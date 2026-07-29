package dev.gouthaman.regimen.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.usecase.DeleteRoutineUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ReorderRoutinesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutinesListViewModel @Inject constructor(
    observeRoutines: ObserveRoutinesUseCase,
    private val deleteRoutine: DeleteRoutineUseCase,
    private val reorderRoutines: ReorderRoutinesUseCase,
) : ViewModel() {

    /**
     * Optimistic ordering applied on drop so the list doesn't flicker back while the DB write
     * round-trips. Cleared once the persisted DB order matches it. Null = follow the DB order.
     */
    private val optimisticOrder = MutableStateFlow<List<String>?>(null)

    val routines: StateFlow<List<RoutineWithExercises>> =
        combine(observeRoutines(), optimisticOrder) { list, order ->
            val byPosition = list.sortedBy { it.routine.position }
            if (order == null) {
                byPosition
            } else {
                val byId = list.associateBy { it.routine.id }
                // Apply the optimistic order; drop stale ids, append anything new.
                val ordered = order.mapNotNull { byId[it] }
                val extras = byPosition.filter { it.routine.id !in order }
                ordered + extras
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(routine: RoutineWithExercises) = viewModelScope.launch {
        deleteRoutine(routine.routine)
    }

    /** Persist a new top-to-bottom ordering produced by a drag-and-drop reorder. */
    fun reorder(orderedIds: List<String>) {
        optimisticOrder.value = orderedIds
        viewModelScope.launch {
            reorderRoutines(orderedIds)
            // DB now matches; release the override so the DB stays the source of truth.
            if (optimisticOrder.value == orderedIds) optimisticOrder.value = null
        }
    }
}
