package com.odys.mototriptracker.di

import android.content.Context
import com.odys.mototriptracker.data.MyObjectBox
import com.odys.mototriptracker.data.backend.TripCloudUploader
import com.odys.mototriptracker.data.trip.AndroidTripServiceController
import com.odys.mototriptracker.data.trip.ObjectBoxTripRepository
import com.odys.mototriptracker.data.trip.PolyUtilRoutePolylineReconstructor
import com.odys.mototriptracker.data.trip.TripServiceController
import com.odys.mototriptracker.data.waypoint.AndroidWaypointRoadNameResolver
import com.odys.mototriptracker.domain.RoutePolylineReconstructor
import com.odys.mototriptracker.domain.TripCloudUpload
import com.odys.mototriptracker.domain.TripRepository
import com.odys.mototriptracker.domain.WaypointRoadNameResolver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.objectbox.BoxStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ObjectBoxModule {

    @Provides
    @Singleton
    fun provideBoxStore(@ApplicationContext context: Context): BoxStore {
        return MyObjectBox.builder()
            .androidContext(context)
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceBindingsModule {

    @Binds
    @Singleton
    abstract fun bindTripServiceController(
        impl: AndroidTripServiceController
    ): TripServiceController

    @Binds
    @Singleton
    abstract fun bindTripRepository(
        impl: ObjectBoxTripRepository
    ): TripRepository

    @Binds
    @Singleton
    abstract fun bindTripCloudUpload(
        impl: TripCloudUploader
    ): TripCloudUpload

    @Binds
    @Singleton
    abstract fun bindRoutePolylineReconstructor(
        impl: PolyUtilRoutePolylineReconstructor
    ): RoutePolylineReconstructor

    @Binds
    @Singleton
    abstract fun bindWaypointRoadNameResolver(
        impl: AndroidWaypointRoadNameResolver
    ): WaypointRoadNameResolver
}
