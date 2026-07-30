package dev.gouthaman.regimen.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.gouthaman.regimen.R
import dev.gouthaman.regimen.sync.di.WebClientId

@Module
@InstallIn(SingletonComponent::class)
object AuthConfigModule {

    @Provides
    @WebClientId
    fun provideWebClientId(@ApplicationContext context: Context): String =
        context.getString(R.string.default_web_client_id)
}
