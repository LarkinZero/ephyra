package dev.jdtech.jellyfin.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.player.DeviceProfileBuilder
import dev.jdtech.jellyfin.repository.DataChangeEvents
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.JellyfinRepositoryImpl
import dev.jdtech.jellyfin.repository.PlaybackEvents
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.UserDataSync
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Singleton
    @Provides
    fun provideDeviceProfileBuilder(application: Application): DeviceProfileBuilder =
        DeviceProfileBuilder(application)

    @Singleton @Provides fun provideDataChangeEvents(): DataChangeEvents = DataChangeEvents()

    @Singleton
    @Provides
    fun providePlaybackEvents(dataChanges: DataChangeEvents): PlaybackEvents =
        PlaybackEvents(dataChanges)

    @Singleton
    @Provides
    fun provideJellyfinRepositoryImpl(
        application: Application,
        jellyfinApi: JellyfinApi,
        serverDatabase: ServerDatabaseDao,
        appPreferences: AppPreferences,
        deviceProfileBuilder: DeviceProfileBuilder,
        userDataSync: UserDataSync,
    ): JellyfinRepositoryImpl {

        return JellyfinRepositoryImpl(
            application,
            jellyfinApi,
            serverDatabase,
            appPreferences,
            deviceProfileBuilder,
            userDataSync,
        )
    }

    /** CE (#281): one repository; downloaded items fall back to Room when the server is away. */
    @Provides
    fun provideJellyfinRepository(
        jellyfinRepositoryImpl: JellyfinRepositoryImpl
    ): JellyfinRepository = jellyfinRepositoryImpl
}
