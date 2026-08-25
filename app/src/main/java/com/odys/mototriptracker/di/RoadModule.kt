package com.odys.mototriptracker.di

import com.odys.mototriptracker.data.road.OverpassSpeedLimitProvider
import com.odys.mototriptracker.domain.SpeedLimitProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RoadModule {

    @Binds
    @Singleton
    abstract fun bindSpeedLimitProvider(
        impl: OverpassSpeedLimitProvider
    ): SpeedLimitProvider
}
