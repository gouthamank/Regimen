package dev.gouthaman.regimen.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.di.ApplicationScope
import dev.gouthaman.regimen.domain.usecase.ObserveActiveWorkoutIdUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the [ActiveWorkoutService] running exactly while a workout is in progress. Started once
 * from [dev.gouthaman.regimen.RegimenApplication.onCreate]; toggling only on the active/idle
 * transition (not on pause) so the service isn't needlessly restarted.
 */
@Singleton
class ActiveWorkoutServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val observeActiveWorkoutId: ObserveActiveWorkoutIdUseCase,
) {
    fun start() {
        observeActiveWorkoutId()
            .map { it != null }
            .distinctUntilChanged()
            .onEach { active ->
                if (active) ActiveWorkoutService.start(context) else ActiveWorkoutService.stop(
                    context
                )
            }
            .launchIn(scope)
    }
}
