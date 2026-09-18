package com.odys.mototriptracker.di

import com.odys.mototriptracker.data.road.OverpassSpeedLimitProvider
import com.odys.mototriptracker.data.road.SpeedLimitCacheStore
import com.odys.mototriptracker.data.road.SpeedLimitRegionPackStore
import com.odys.mototriptracker.domain.SpeedLimitCache
import com.odys.mototriptracker.domain.SpeedLimitProvider
import com.odys.mototriptracker.domain.SpeedLimitRegionPacks
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

    @Binds
    @Singleton
    abstract fun bindSpeedLimitCache(
        impl: SpeedLimitCacheStore
    ): SpeedLimitCache

    @Binds
    @Singleton
    abstract fun bindSpeedLimitRegionPacks(
        impl: SpeedLimitRegionPackStore
    ): SpeedLimitRegionPacks
}
