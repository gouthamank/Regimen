package dev.gouthaman.regimen.sync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gouthaman.regimen.domain.repository.SyncDeviceRepository
import dev.gouthaman.regimen.sync.device.SyncDeviceRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncDeviceModule {

    @Binds
    @Singleton
    abstract fun bindSyncDeviceRepository(impl: SyncDeviceRepositoryImpl): SyncDeviceRepository
}
