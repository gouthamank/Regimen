package dev.gouthaman.regimen

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.gouthaman.regimen.data.local.Seeder
import dev.gouthaman.regimen.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RegimenApplication : Application() {

    @Inject lateinit var seeder: Seeder

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seeder.seedIfNeeded() }
    }
}
