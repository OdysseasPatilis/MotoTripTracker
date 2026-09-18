package com.odys.mototriptracker.data.trip

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.domain.model.RoutePoint
import com.odys.mototriptracker.domain.model.Trip

internal fun TripEntity.toDomain(): Trip = Trip(
    id = id,
    startTime = startTime,
    endTime = endTime,
    distanceMeters = distanceMeters,
    movingTime = movingTime,
    stoppedTime = stoppedTime,
    maxSpeed = maxSpeed,
    maxGForce = maxGForce,
    elevationGain = elevationGain,
    avgSpeed = avgSpeed,
    encodedRoutePolyline = encodedRoutePolyline,
    title = title,
    isFavorite = isFavorite,
    maxLateralGForce = maxLateralGForce,
    cornerCount = cornerCount,
    twistinessScore = twistinessScore,
)

internal fun RoutePointEntity.toDomain(): RoutePoint = RoutePoint(
    id = id,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speedMps = speedMps,
    timestamp = timestamp,
    waypointType = waypointType,
    isWaypoint = isWaypoint,
    waypointTitle = waypointTitle,
    waypointSubtitle = waypointSubtitle,
)
