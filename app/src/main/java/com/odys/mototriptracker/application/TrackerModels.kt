package com.odys.mototriptracker.application

/**
 * Public tracker models for the UI layer. Implementations live in `data/`;
 * UI should import these aliases instead of `data.*` packages.
 */
typealias NavigationState = com.odys.mototriptracker.data.navigation.NavigationState
typealias NavigationSearchResult = com.odys.mototriptracker.data.navigation.NavigationSearchResult
typealias DestinationHistoryEntry = com.odys.mototriptracker.data.navigation.DestinationHistoryEntry
typealias NavRouteOption = com.odys.mototriptracker.data.navigation.NavRouteOption
typealias PickedMapPlace = com.odys.mototriptracker.data.navigation.PickedMapPlace

typealias RouteWeatherState = com.odys.mototriptracker.data.weather.RouteWeatherState
typealias RouteWeatherSegment = com.odys.mototriptracker.data.weather.RouteWeatherSegment

typealias RankedPetrolStation = com.odys.mototriptracker.data.petrol.RankedPetrolStation
typealias PetrolSearchPlan = com.odys.mototriptracker.data.petrol.PetrolSearchPlan
typealias PetrolStationRecommendation = com.odys.mototriptracker.data.petrol.PetrolStationRecommendation
typealias GooglePetrolDetails = com.odys.mototriptracker.data.petrol.GooglePetrolDetails
typealias OpeningHoursEvaluator = com.odys.mototriptracker.domain.OpeningHoursEvaluator
typealias PetrolSearchResult = com.odys.mototriptracker.data.petrol.PetrolSearchResult

typealias TrafficCamera = com.odys.mototriptracker.data.camera.TrafficCamera
typealias TrafficCameraAlert = com.odys.mototriptracker.data.camera.TrafficCameraAlert
typealias TrafficCameraKind = com.odys.mototriptracker.data.camera.TrafficCameraKind
typealias TrafficCameraPackDownloadStatus = com.odys.mototriptracker.domain.TrafficCameraPackDownloadStatus
