package dev.gouthaman.regimen.healthconnect

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gouthaman.regimen.domain.repository.HealthConnectPrefsRepository
import dev.gouthaman.regimen.domain.repository.HealthConnectRepository
import dev.gouthaman.regimen.domain.repository.HealthConnectScheduleRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthConnectModule {

    @Binds
    @Singleton
    abstract fun bindHealthConnectRepository(impl: HealthConnectRepositoryImpl): HealthConnectRepository

    @Binds
    @Singleton
    abstract fun bindHealthConnectPrefsRepository(
        impl: HealthConnectPrefsRepositoryImpl,
    ): HealthConnectPrefsRepository

    @Binds
    @Singleton
    abstract fun bindHealthConnectScheduleRepository(
        impl: HealthConnectSchedulerImpl,
    ): HealthConnectScheduleRepository
}
