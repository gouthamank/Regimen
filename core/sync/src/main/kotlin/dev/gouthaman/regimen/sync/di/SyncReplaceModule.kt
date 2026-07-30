package dev.gouthaman.regimen.sync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gouthaman.regimen.domain.repository.SyncReplaceRepository
import dev.gouthaman.regimen.sync.replace.SyncReplaceRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncReplaceModule {

    @Binds
    @Singleton
    abstract fun bindSyncReplaceRepository(impl: SyncReplaceRepositoryImpl): SyncReplaceRepository
}
