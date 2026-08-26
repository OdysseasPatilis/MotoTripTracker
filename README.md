# MotoTripTracker

Android motorcycle ride tracker. Records high-accuracy GPS rides, shows a live dashboard (speed, G-force, road speed limits), stores trips locally, and offers history, summary, map replay, share card, and GPX export.

| | |
|---|---|
| **Package** | `com.odys.mototriptracker` |
| **Min / target SDK** | 24 / 36 |
| **UI** | Jetpack Compose + Material 3 |
| **DI** | Hilt |
| **Storage** | ObjectBox (local) |
| **Maps / location** | Google Maps + Fused Location |

---

## Features

### Live tracking
- Start / pause / resume / stop from the tracker dashboard
- Foreground service keeps GPS alive when the screen is off or the app is backgrounded
- Live stats: distance, moving & stopped time, average / max speed, elevation gain
- Speedometer arc, G-force bar, battery and clock
- GPS quality indicator and recording pulse
- Screen stays on during an active ride

### Road speed limits
- Live limits from OpenStreetMap via Overpass (not Google Roads)
- On-screen speed-limit sign; translucent flash when over the limit
- Manual fallback limit (cycled in settings / theme store) when no live value
- Offline grid cache (`SpeedLimitCacheStore`) so recent cells work without network

### Sensors & dynamics
- Linear acceleration → current / max G and lateral G (`GForceTracker`)
- Corner detection from bearing change while moving (`CornerDetector`)
- Corner count and max lateral G persisted on each trip

### Ride history
- Newest → oldest ordering
- **All** and **Favorites** tabs (starred rides are not mixed into All sort order)
- Text by name (and related text fields)
- Date filter sheet: Today, Yesterday, This week, This month, or custom range
- Day dividers when the calendar day changes
- Favorite toggle from the list or summary

### Ride summary & map
- Post-ride summary with stats, rename, favorite, delete
- **Ride moments** highlights (e.g. top speed, max G, elevation, longest stop, corners, lean G)
- Full-route Google Map: speed / elevation colored polyline, profile chart, waypoints
- Share **PNG card** or export **GPX** (FileProvider)

### Theme
- Dark and light palettes (`AppPalette` / `ThemeStore`), toggle from the tracker

---

## Architecture

Single Gradle module (`:app`) with a layered package layout. ViewModels talk to **use cases**; use cases talk to repositories / domain services. The ride engine (`TripManager`) is shared with the foreground service so tracking continues without the UI.

```
┌─────────────────────────────────────────────────────────────┐
│  UI (Compose)                                               │
│  Routes → ViewModels → Screens                              │
│  tracker / history / summary / route / share / theme        │
└────────────────────────────┬────────────────────────────────┘
                             │ use cases
┌────────────────────────────▼────────────────────────────────┐
│  Domain                                                     │
│  TripManager · StopDetector · SpeedFilter · SpeedSmoother   │
│  ElevationSmoother · GForceTracker · CornerDetector         │
│  SpeedLimitResolver · RideMomentsCalculator · TripStats     │
│  usecase/*                                                  │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│  Data                                                       │
│  TripRepository · LocationRepository                        │
│  OverpassSpeedLimitProvider · SpeedLimitCacheStore          │
│  GpxExporter · AdvancedWaypointAnalyzer                     │
│  ObjectBox entities (TripEntity, RoutePointEntity)          │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│  Service                                                    │
│  TripForegroundService  (location FGS + notification)       │
└─────────────────────────────────────────────────────────────┘
```

Hilt (`SingletonComponent`) wires ObjectBox, repositories, `TripManager`, speed-limit provider, and the service controller.

### Package map

```
com.odys.mototriptracker/
├── MainActivity.kt / MotoTripTrackerApp.kt
├── di/                 # ObjectBox, road, service bindings
├── service/            # TripForegroundService
├── domain/             # ride engine, detectors, use cases
├── data/
│   ├── trip/           # TripEntity, TripRepository, service controller
│   ├── checkpoint/     # RoutePointEntity
│   ├── location/       # Fused location Flow
│   ├── road/           # Overpass + offline cache
│   ├── export/         # GPX
│   └── waypoint/       # post-ride waypoint tagging
├── ui/
│   ├── navigation/     # MotoTripNavHost, Routes
│   ├── tracker/        # RideTrackerRoute / ViewModel
│   ├── history/        # RideHistoryRoute / ViewModel / filters
│   ├── summary/        # RideSummaryRoute / ViewModel
│   ├── route/          # FullRouteRoute / ViewModel
│   ├── dashboard/      # Compose screens (tracker, history, summary, map)
│   ├── share/          # RideShareCard, GpxShare
│   ├── theme/          # AppPalette, ThemeStore
│   └── components/     # ScreenTopBar, …
└── util/               # AppLogger
```

### Navigation

| Route | Screen |
|-------|--------|
| `tracker` | Live ride dashboard |
| `history` | Trip list (tabs, search, date filters) |
| `summary/{tripId}` | Ride summary, moments, share / rename / delete |
| `full_route/{tripId}` | Google Maps route + waypoints |

Defined in `ui/navigation/Routes.kt`, hosted by `MotoTripNavHost`.

### Use cases

| Use case | Role |
|----------|------|
| `StartRideUseCase` / `StopRideUseCase` | Begin / end session + service |
| `PauseRideUseCase` / `ResumeRideUseCase` | Pause / resume GPS + sensors |
| `ObserveRideSessionUseCase` | Live `RideSessionState` / `TripStats` for UI |
| `GetTripHistoryUseCase` | All trips, newest first |
| `GetTripUseCase` / `GetTripRouteUseCase` | Single trip / route points |
| `DeleteTripUseCase` | Remove trip |
| `UpdateTripTitleUseCase` / `ToggleFavoriteUseCase` | Rename / favorite |

ViewModels depend on these use cases, not on `TripRepository` or `TripManager` directly (except where injection already targets a use case facade).

---

## Core domain concepts

### `TripManager`
Session orchestrator. Creates the ObjectBox trip on start, applies location filters, updates moving/stopped time, distance, speed, elevation, G-force and corners, writes route points, and finalizes polyline + waypoints on stop. Rejects GPS teleports while moving.

### `StopDetector`
Classifies each location delta as **moving** or **stopped** from a speed-based flag (aligned with the iOS twin). Ignores huge time gaps so background freezes do not inflate stopped time incorrectly.

### `SpeedFilter` / `SpeedSmoother` / `ElevationSmoother`
Accuracy and ghost-speed rejection, display speed smoothing, and elevation climb filtering.

### `SpeedLimitResolver`
Throttled Overpass lookups, in-memory + disk grid cache, soft neighbour fallback offline, pushes the current limit into the live session.

### `GForceTracker` / `CornerDetector`
Sensor-based G and lateral G; bearing-based corner count and estimated lean G for stats and moments.

### `RideMomentsCalculator`
Builds a short list of highlight moments from the saved trip and route points for the summary UI and share card.

---

## Data model (ObjectBox)

### `TripEntity`
`id`, `startTime`, `endTime`, `distanceMeters`, `movingTime`, `stoppedTime`, `maxSpeed`, `maxGForce`, `elevationGain`, `avgSpeed`, `encodedRoutePolyline`, `title`, `isFavorite`, `maxLateralGForce`, `cornerCount`  
Backlink: `routePoints` → `RoutePointEntity`

### `RoutePointEntity`
`latitude`, `longitude`, `altitude`, `speedMps`, `timestamp`, plus optional waypoint fields (`waypointType`, titles, …)  
Relation: `trip` → `TripEntity`

Schema dump: `app/objectbox-models/default.json`.

Waypoint types written on finalize include e.g. `START`, `END`, `TOP_SPEED`, `SUMMIT`, `BRIEF_STOP`, `REST_STOP`, and related tags from `AdvancedWaypointAnalyzer`.

---

## Key flows

### Start → track → stop
1. Tracker requests location (and notifications / background location as needed).
2. **Start** → use case → `TripManager.startTrip()` (empty `TripEntity`) + start `TripForegroundService`.
3. Service collects `LocationRepository` updates (~1s, accuracy gated) → `TripManager.onLocationUpdate` + `SpeedLimitResolver.onLocationUpdate`.
4. Each valid fix appends a `RoutePointEntity` and updates running stats.
5. **Stop** → finalize trip (end time, polyline encode, waypoints) and stop the service.
6. Rides under ~50 m are flagged as short in the stop result (logged); they are still saved.

### History → summary → map / share
1. History loads trips newest-first; tab / search / date filter refine the list.
2. Open summary → moments, rename, favorite, delete, share.
3. Share: PNG card (`RideShareCard`) or GPX (`GpxExporter` + `GpxShare`) via FileProvider (`cache/share/`).
4. View route → Maps Compose screen with layers and waypoints.

---

## Tech stack

| Area | Choice |
|------|--------|
| Language / build | Kotlin 2.3, AGP 8.13, KSP, Java 17 |
| UI | Compose BOM, Material 3, Navigation Compose |
| DI | Hilt + `hilt-navigation-compose` |
| Persistence | ObjectBox 5.x |
| Location | Play Services Location |
| Maps | Play Services Maps, Maps Compose, Maps Utils (polyline) |
| Network | OkHttp (Overpass); Geocoding via HTTP where used for waypoints |
| Secrets | Maps Secrets Gradle Plugin → `MAPS_API_KEY` |

Versions live in `gradle/libs.versions.toml`.

---

## Permissions & components

**Permissions:** `INTERNET`, fine/coarse/background location, `POST_NOTIFICATIONS`, foreground service + location type.

**Components:**
- `MainActivity` — Compose entry, `MotoTripNavHost`
- `TripForegroundService` — sticky location FGS, notification channel `ride_channel`
- `FileProvider` — `${applicationId}.fileprovider` → `res/xml/file_paths.xml`

A Google Maps API key is required for map screens (Secrets plugin). Overpass speed limits use public HTTP endpoints (no Maps key).

---

## Project layout (high level)

```
MotoTripTracker/
├── app/
│   ├── build.gradle.kts
│   ├── objectbox-models/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/odys/mototriptracker/…
│       └── res/
├── gradle/libs.versions.toml
└── README.md
```

---

## Testing

Unit tests under `app/src/test/…`:

- `StopDetectorTest` — moving / stopped accumulation and gaps
- `SpeedLimitParserTest` — OSM `maxspeed` parsing
- `RideMomentsCalculatorTest` — moment titles / selection

Instrumented / Compose UI tests are mostly scaffold; ride and ObjectBox flows are not fully covered by instrumentation yet.

---

## Building

1. Open the project in Android Studio (or use the Gradle wrapper).
2. Provide a Maps API key for the Secrets plugin (e.g. `local.properties` / CI secret as configured by the plugin).
3. Sync and run the `:app` debug configuration on a device or emulator with Google Play services.

```bash
./gradlew :app:assembleDebug
```

---

## Design notes

- **UI → use cases → data/domain** keeps screens thin and testable.
- **Foreground service + singleton `TripManager`** is the source of truth for an active ride, not the Compose lifecycle.
- **ObjectBox** is on-device only; there is no account or cloud sync in this codebase.
- History UX separates **Favorites** into its own tab and uses **date filters** for range queries; free-text search stays focused on naming and related fields.
