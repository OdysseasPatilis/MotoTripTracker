# API calls & local storage (Android)

Where the Android app talks to remote APIs, and where it reads/writes local data.

> Scope: `app/` (Android). The iOS twin mostly uses Apple MapKit instead of Google for search/directions/geocoding.

---

## 1. Google APIs

Config: `MAPS_API_KEY` in `local.properties` → manifest meta-data `com.google.android.geo.API_KEY` via `MapsApiKeyProvider`.

### 1.1 Maps SDK (map tiles / rendering)

Not a REST call you write by hand — Google Maps Compose / Maps SDK loads tiles while a map is on screen.

| UI surface | File |
|------------|------|
| Live ride map | `ui/tracker/LiveRideMapView.kt` |
| Ride summary map preview | `ui/dashboard/RideSummaryScreenUpdate.kt` |
| Full route / replay map | `ui/dashboard/FullRouteScreenGMaps.kt` |

**When:** Continuously while those screens are visible (pan/zoom loads more tiles).

---

### 1.2 Places SDK & Places REST

| Call | API | File | Trigger |
|------|-----|------|---------|
| Text Search (New) | Places SDK `searchByText` | `data/navigation/NavigationService.kt` → `fetchPlacesTextSearch` | Destination search (after 350 ms debounce) |
| Autocomplete | Places SDK `findAutocompletePredictions` | same → `fetchPlacesAutocomplete` | If text search empty |
| Text Search (legacy REST) | `maps/api/place/textsearch/json` | same → `fetchGoogleTextSearchRest` | If SDK autocomplete empty |
| Place Details (SDK) | `fetchPlace` (geometry) | same → `resolvePlace` | User picks a Google place without lat/lng |
| Place Details (REST) | `maps/api/place/details/json` | same | SDK client unavailable |
| Nearby Search (legacy REST URLs present) | `nearbysearch` / `textsearch` | same (petrol helpers in nav service) | Legacy petrol helpers (prefer `PetrolStationFinder` path) |
| Nearby gas stations | Places SDK `searchNearby` (`gas_station`) | `data/petrol/PetrolPlacesEnricher.kt` | Open petrol sheet / refresh |
| Open-now + hours | `isOpen` + `fetchPlace` per place | same → `fetchOpenInfo` / `resolveOpenInfo` | After nearby list (up to **12** place IDs) |
| Station details | `fetchPlace` + optional `fetchPhoto` | same → `fetchDetails` | Tap a petrol station details card |
| Static map preview | `maps/api/staticmap` | same → `fetchStaticMapPreview` | Petrol details (photo fallback / map card) |

**Search cascade** (`NavigationService.searchDestinations`):

1. Places Text Search (SDK)  
2. Places Autocomplete (SDK)  
3. Places Text Search (REST)  
4. Photon (OSM — not Google)  
5. Nominatim (OSM — not Google)

---

### 1.3 Directions

| Call | Endpoint / API | File | Trigger |
|------|----------------|------|---------|
| Driving route | `maps/api/directions/json` | `NavigationService.fetchDirections` | Set destination / off-route recalculate |
| Fallback | OSRM (not Google) | `fetchOsrmDirections` | Google Directions fails |

**Recalculate:** if rider is **>80 m** off the polyline, cooldown **12 s** (`RECALCULATE_COOLDOWN_MS`).

---

### 1.4 Geocoding

| Call | Endpoint | File | Trigger |
|------|----------|------|---------|
| Reverse geocode | `maps/api/geocode/json` | `data/waypoint/AdvancedWaypointAnalyzer.kt` → `getStreetName` | End of ride: **start**, **end**, and each **rest stop >5 min** |
| Fallback | Android `Geocoder` | same | Google geocode fails (not a Google Cloud SKU) |

---

## 2. Other remote APIs (non-Google)

| Service | Endpoint(s) | File | Purpose | When |
|---------|-------------|------|---------|------|
| **OpenStreetMap Overpass** | `lz4.overpass-api.de`, `z.overpass-api.de`, `overpass.kumi.systems`, `overpass-api.de` | `data/road/OverpassSpeedLimitProvider.kt` | Road `maxspeed` | Speed-limit miss / implausible Athens pack hit |
| **Overpass** (petrol) | same mirrors | `data/petrol/PetrolStationFinder.kt` (+ helpers in `NavigationService`) | OSM fuel stations | Petrol search (merged with Google Places) |
| **Open-Meteo** | `api.open-meteo.com/v1/forecast` | `data/weather/RouteWeatherService.kt` | Hourly weather along route | User opens route weather (samples along polyline) |
| **Photon** | `photon.komoot.io/api` | `NavigationService.fetchPhotonSearch` | Destination search fallback | Google Places returned nothing |
| **Nominatim** | `nominatim.openstreetmap.org/search` | `NavigationService` | Destination search last resort | Photon empty |
| **OSRM** | `router.project-osrm.org/route/v1/driving/...` | `NavigationService.fetchOsrmDirections` | Route fallback | Google Directions failed |
| **MotoTripTracker backend** | `{BACKEND_BASE_URL}/v1/trips/upload` | `data/backend/TripCloudUploader.kt` | Upload finished trip JSON | Auto on stop (if URL set) + manual **Upload to server** on summary |

Config: `BACKEND_BASE_URL` in `local.properties` → `BuildConfig.BACKEND_BASE_URL` / `BackendConfig`.

---

## 3. Local assets (not network)

| Resource | File | Purpose |
|----------|------|---------|
| Athens / region speed-limit packs | `data/road/SpeedLimitRegionPackStore.kt` (+ assets) | Offline-first speed limits before Overpass |
| Dark map style JSON | inline / style options in map screens | Visual only |

---

## 4. Database — ObjectBox

**Setup:** `di/AppModule.kt` → `MyObjectBox.builder()` → `BoxStore`.  
**Access layer:** `data/trip/TripRepository.kt`  
**Entities:**

- `TripEntity` — ride summary / metadata  
- `RoutePointEntity` — GPS trail + waypoint flags  

### Write paths

| Operation | `TripRepository` | Called from |
|-----------|------------------|-------------|
| Create trip | `startNewTrip` | `TripManager` on start |
| Append GPS + running stats | `addRoutePointAndUpdateStats` | `TripManager` while recording |
| Finalize (waypoints, polyline, stats) | `saveTrip` | `TripManager` on stop |
| Rename | `updateTitle` (via use case) | Summary / history |
| Favorite | toggle via repository | Summary / history |
| Delete trip + points | `deleteTrip` | Summary / history |

Waypoint analysis during `saveTrip` may call **Google Geocoding** (see §1.4), then writes waypoint fields back to ObjectBox.

### Read paths

| Operation | `TripRepository` | Consumers |
|-----------|------------------|-----------|
| All trips | `getTrips` | History, leaderboard |
| One trip | `getTrip` | Summary, full route, upload |
| Full trail | `getRoutePointsForMap` | Map, replay, GPX, share card, upload |
| Waypoints only | `getWaypointsForTrip` | Full route list |

Use cases wrapping the repo: `GetTripHistoryUseCase`, `GetTripRouteUseCase`, `GetTripUseCase`, `DeleteTripUseCase`, `UpdateTripTitleUseCase`, `ToggleFavoriteUseCase`, `UploadTripToCloudUseCase`, `GetLeaderboardUseCase`.

---

## 5. SharedPreferences & other local storage

| Store | File | Keys / data |
|-------|------|-------------|
| Theme | `ui/theme/ThemeStore.kt` | Dark / light mode |
| Fuel settings | `data/fuel/FuelService.kt` | Tank, consumption, remaining, etc. |
| Petrol preferences | `data/petrol/PetrolPreferences.kt` | Brand prefs / ranking order |
| Speed-limit cell cache | `data/road/SpeedLimitCacheStore.kt` | Geohash/cell → km/h JSON |
| Nav voice mute | `data/navigation/NavigationVoicePrompt.kt` | Mute preference |
| Backend user id | `data/backend/BackendUserIdStore.kt` | Stable anonymous `userId` for uploads |

No Room / SQLite — durable ride data is **ObjectBox only**.

---

## 6. Quick “who hits the network?” cheat sheet

| User action | Likely network |
|-------------|----------------|
| Start ride, ride without nav/petrol | GPS only (Fused Location). Speed limit: local pack → cache → maybe Overpass |
| Type destination | Places (± Photon / Nominatim) |
| Confirm destination | Directions (± OSRM) |
| Leave route | Directions again (12 s cooldown) |
| Open petrol sheet | Overpass + Places Nearby + up to 12× open-info |
| Open petrol details | Place Details + Photo + Static Maps |
| Open route weather | Open-Meteo |
| Stop ride | Local ObjectBox write; optional Geocode; optional backend upload |
| Summary → Upload to server | Backend `POST /v1/trips/upload` |
| View maps | Google Maps tiles |

---

## 7. Related files (entry points)

```
data/navigation/NavigationService.kt     # Places, Directions, search fallbacks, OSRM
data/petrol/PetrolPlacesEnricher.kt      # Places nearby / details / photo / static map
data/petrol/PetrolStationFinder.kt       # Overpass petrol + merge with Google
data/road/OverpassSpeedLimitProvider.kt  # Overpass maxspeed
data/weather/RouteWeatherService.kt      # Open-Meteo
data/waypoint/AdvancedWaypointAnalyzer.kt# Geocode on finalize
data/backend/TripCloudUploader.kt        # Own backend upload
data/trip/TripRepository.kt              # ObjectBox CRUD
domain/TripManager.kt                    # Ride lifecycle → repository
```
