package dev.gouthaman.regimen

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.gouthaman.regimen.data.local.Seeder
import dev.gouthaman.regimen.di.ApplicationScope
import dev.gouthaman.regimen.ui.active.ActiveWorkoutServiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RegimenApplication : Application() {

    @Inject
    lateinit var seeder: Seeder

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Inject
    lateinit var activeWorkoutServiceController: ActiveWorkoutServiceController

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seeder.seedIfNeeded() }
        // Runs the foreground service while a workout is in progress (incl. resume after death).
        activeWorkoutServiceController.start()
    }
}
