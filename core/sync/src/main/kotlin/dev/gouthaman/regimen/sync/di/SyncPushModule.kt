package dev.gouthaman.regimen.sync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gouthaman.regimen.domain.repository.SyncPushRepository
import dev.gouthaman.regimen.domain.repository.SyncScheduleRepository
import dev.gouthaman.regimen.sync.push.SyncPushRunner
import dev.gouthaman.regimen.sync.push.SyncSchedulerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncPushModule {

    @Binds
    @Singleton
    abstract fun bindSyncPushRepository(impl: SyncPushRunner): SyncPushRepository

    @Binds
    @Singleton
    abstract fun bindSyncScheduleRepository(impl: SyncSchedulerImpl): SyncScheduleRepository
}
