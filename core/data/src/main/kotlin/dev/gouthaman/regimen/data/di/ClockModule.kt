package dev.gouthaman.regimen.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gouthaman.regimen.data.util.SystemClock
import dev.gouthaman.regimen.domain.util.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock
}
