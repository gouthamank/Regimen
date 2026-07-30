package dev.gouthaman.regimen

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.gouthaman.regimen.data.local.Seeder
import dev.gouthaman.regimen.domain.di.ApplicationScope
import dev.gouthaman.regimen.service.ActiveWorkoutServiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RegimenApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var seeder: Seeder

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Inject
    lateinit var activeWorkoutServiceController: ActiveWorkoutServiceController

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seeder.seedIfNeeded() }
        // Runs the foreground service while a workout is in progress (incl. resume after death).
        activeWorkoutServiceController.start()
    }
}
