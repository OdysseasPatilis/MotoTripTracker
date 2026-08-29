# MotoTripTracker

Android motorcycle ride tracker. Records high-accuracy GPS rides, shows a live dashboard (speed, G-force, road speed limits, fuel range), stores trips locally, and offers history, summary, map replay, share card, and GPX export. Destination search, in-app routing, ranked petrol stops, and route weather sit alongside the tracker.

The app is the Android counterpart of the iOS **MotoTripTracker** project, with feature parity for tracking, navigation, fuel/petrol, Overpass speed limits, twistiness, ride moments, favorites, and GPX/share.

| | |
|---|---|
| **Package** | `com.odys.mototriptracker` |
| **Min / target SDK** | 24 / 36 |
| **UI** | Jetpack Compose + Material 3 |
| **DI** | Hilt |
| **Storage** | ObjectBox (local) |
| **Maps / location** | Google Maps + Fused Location + Places SDK |

---

## Features

### Live tracking
- Start / pause / resume / stop from the tracker dashboard
- Foreground service keeps GPS alive when the screen is off or the app is backgrounded
- Live stats: distance, moving & stopped time, average / max speed, elevation gain
- Speedometer arc, G-force bar, battery and clock
- **Twistiness score** (0–100) from corner density + peak lateral G — shown live and stored on each trip
- GPS signal bars (Excellent ≤5 m / Good ≤10 m / Fair ≤20 m / Weak) with ±m accuracy — live while idle and paused
- Recording pulse while tracking
- Screen stays on during an active ride

### Navigation (destination & route)
- **Set destination** via search sheet (Google Places autocomplete + text search; Nominatim/Photon fallbacks)
- **Driving route** from Directions API with **OSRM** fallback; drawn on the live map
- Distance remaining and ETA update as you move
- **Open in Google Maps** for voice guidance handoff; clear route from the dashboard
- Origin is kept when clearing a destination so the next search still has a GPS fix

### Fuel & range
- Tank capacity, remaining liters, and L/100 km consumption (persisted)
- **Estimated range** chip on the map (compact capsule); burns fuel from trip distance while riding
- Fill-up and preferences from the fuel settings sheet
- Low-fuel awareness when remaining range is short

### Petrol stations
- **Nearest petrol** opens a ranked recommendation list (brand order e.g. Shell → BP, preferred octane 95 / 98 / 100, open status, then distance)
- Search radius **adapts to context** — tighter in cities (~2 km), wider rural (~20 km+), **highway-biased** when riding fast on motorways
- OSM Overpass discovery + Google Places enrichment (open now, hours, rating, phone)
- Closed stations filtered out when status is known
- **Details** sheet with place photo (Google Places) or Static Maps preview; **Go** starts in-app navigation
- Brand / octane prefs live in Fuel settings

### Route weather
- When a route is set, **Open-Meteo** forecasts are sampled along the plan at estimated arrival times
- Summary line on the tracker; tap for the full segment timeline (rain, temp, wind)

### Road speed limits
- Live limits from OpenStreetMap via Overpass (mirrors, 30 m then 60 m radii, 35 m / 15 s throttle)
- On-screen speed-limit sign; translucent flash when over the limit
- Manual fallback limit (cycled) when no live value
- Offline SharedPreferences grid cache + neighbour soft fallback
- Bundled Greater Athens region pack (`athens_speed_limits.json`) — offline-only inside that bbox (no Overpass)
- OSM tag parsing includes country implicits (`GR:urban`, etc.)

### Sensors & dynamics
- Linear acceleration → current / max G and lateral G (`GForceTracker`)
- Corner detection from bearing change while moving (`CornerDetector`)
- Corner count, max lateral G, and twistiness persisted on each trip

### Ride history
- Newest → oldest ordering
- **All** and **Favorites** tabs (starred rides are not mixed into All sort order)
- Search by name (and related text fields)
- Date filter sheet: Today, Yesterday, This week, This month, or custom range
- Day dividers when the calendar day changes
- Favorite toggle from the list or summary

### Leaderboard
- Personal rankings across saved rides: **Speed** (max km/h), **Distance** (km), **Turns** (corner count), **Twistiness** (composite score)
- Tap an entry to open the same ride summary as from history

### Ride summary & map
- Post-ride summary with stats (including twistiness), rename, favorite, delete
- **Ride moments** highlights (e.g. top speed, max G, elevation, longest stop, corners, lean G)
- Full-route Google Map: speed / elevation colored polyline, profile chart, waypoints
- **Route replay**: play / pause at 1× / 2× / 5×; mint traveled trail + faded remaining; camera follows the rider
- Share **PNG card** or export **GPX** (FileProvider)

### Theme
- Dark and light palettes (`AppPalette` / `ThemeStore`), toggle from the tracker

---

## Architecture

Single Gradle module (`:app`) with a layered package layout. ViewModels talk to **use cases**; use cases talk to repositories / domain services. The ride engine (`TripManager`) is shared with the foreground service so tracking continues without the UI. Navigation, fuel, petrol, and weather are injected services used by the tracker ViewModel.

```
┌─────────────────────────────────────────────────────────────┐
│  UI (Compose)                                               │
│  Routes → ViewModels → Screens / sheets                     │
│  tracker · history · summary · route · share · theme        │
│  DestinationSearch · PetrolStations · FuelSettings · Weather│
└────────────────────────────┬────────────────────────────────┘
                             │ use cases / services
┌────────────────────────────▼────────────────────────────────┐
│  Domain                                                     │
│  TripManager · StopDetector · SpeedFilter · SpeedSmoother   │
│  ElevationSmoother · GForceTracker · CornerDetector         │
│  TwistinessCalculator · RouteReplayEngine                   │
│  SpeedLimitResolver · RideMomentsCalculator · TripStats     │
│  usecase/*                                                  │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│  Data                                                       │
│  TripRepository · LocationRepository · NavigationService    │
│  FuelService · PetrolStationFinder · PetrolPlacesEnricher   │
│  RouteWeatherService · OverpassSpeedLimitProvider           │
│  GpxExporter · AdvancedWaypointAnalyzer                     │
│  ObjectBox entities (TripEntity, RoutePointEntity)          │
└────────────────────────────┬────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│  Service                                                    │
│  TripForegroundService  (location FGS + notification)       │
└─────────────────────────────────────────────────────────────┘
```

Hilt (`SingletonComponent`) wires ObjectBox, repositories, `TripManager`, speed-limit provider, navigation / fuel / petrol / weather, and the service controller.

### Package map

```
com.odys.mototriptracker/
├── MainActivity.kt / MotoTripTrackerApp.kt
├── di/                 # ObjectBox, road, service bindings
├── service/            # TripForegroundService
├── domain/             # ride engine, detectors, replay, twistiness, use cases
├── data/
│   ├── trip/           # TripEntity, TripRepository, service controller
│   ├── checkpoint/     # RoutePointEntity
│   ├── location/       # Fused location Flow
│   ├── navigation/     # destination search, directions, nav state
│   ├── fuel/           # tank / consumption / range
│   ├── petrol/         # OSM finder, prefs, Places enricher, hours parsers
│   ├── weather/        # Open-Meteo route sampling
│   ├── road/           # Overpass + offline cache
│   ├── export/         # GPX
│   └── waypoint/       # post-ride waypoint tagging
├── ui/
│   ├── navigation/     # MotoTripNavHost, Routes
│   ├── tracker/        # RideTracker + destination / petrol / fuel / weather sheets
│   ├── history/        # RideHistoryRoute / ViewModel / filters
│   ├── summary/        # RideSummaryRoute / ViewModel
│   ├── route/          # FullRouteRoute / ViewModel / RouteReplayPanel
│   ├── dashboard/      # Compose screens (tracker, history, summary, map)
│   ├── leaderboard/    # personal rankings
│   ├── share/          # RideShareCard, GpxShare
│   ├── theme/          # AppPalette, ThemeStore
│   └── components/     # ScreenTopBar, …
└── util/               # AppLogger, MapsApiKeyProvider
```

### Navigation

| Route | Screen |
|-------|--------|
| `tracker` | Live ride dashboard (+ nav / fuel / petrol / weather sheets) |
| `history` | Trip list (tabs, search, date filters) |
| `leaderboard` | Personal rankings (speed / distance / turns / twistiness) |
| `summary/{tripId}` | Ride summary, moments, share / rename / delete |
| `full_route/{tripId}` | Google Maps route + waypoints + replay |

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
| `GetLeaderboardUseCase` | Rank trips by speed / distance / turns / twistiness |

ViewModels depend on these use cases for trip lifecycle and history. Tracker overlays (navigation, fuel, petrol, weather) call injected services from `RideTrackerViewModel`.

---

## Core domain concepts

### `TripManager`
Session orchestrator. Creates the ObjectBox trip on start, applies location filters, updates moving/stopped time, distance, speed, elevation, G-force and corners, writes route points, and finalizes polyline + waypoints + twistiness on stop. Rejects GPS teleports while moving.

### `StopDetector`
Classifies each location delta as **moving** or **stopped** from a speed-based flag (aligned with the iOS twin). Ignores huge time gaps so background freezes do not inflate stopped time incorrectly.

### `SpeedFilter` / `SpeedSmoother` / `ElevationSmoother`
Accuracy and ghost-speed rejection, display speed smoothing, and elevation climb filtering.

### `SpeedLimitResolver`
Throttled Overpass lookups, in-memory + disk grid cache, soft neighbour fallback offline, pushes the current limit into the live session.

### `GForceTracker` / `CornerDetector` / `TwistinessCalculator`
Sensor-based G and lateral G; bearing-based corner count and estimated lean G; composite 0–100 twistiness rating for stats, leaderboard, and moments.

### `RouteReplayEngine`
Interpolates rider position along saved route points for playback (speed multipliers, traveled trail coordinates).

### `RideMomentsCalculator`
Builds a short list of highlight moments from the saved trip and route points for the summary UI and share card.

### `NavigationService` / `FuelService` / `PetrolStationFinder` / `RouteWeatherService`
Destination search + routing; tank/range persistence; OSM + Google petrol ranking and details (including place photo / map preview); Open-Meteo samples along the active route.

---

## Data model (ObjectBox)

### `TripEntity`
`id`, `startTime`, `endTime`, `distanceMeters`, `movingTime`, `stoppedTime`, `maxSpeed`, `maxGForce`, `elevationGain`, `avgSpeed`, `encodedRoutePolyline`, `title`, `isFavorite`, `maxLateralGForce`, `cornerCount`, `twistinessScore`  
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
4. Each valid fix appends a `RoutePointEntity` and updates running stats (fuel range burns with distance).
5. **Stop** → finalize trip (end time, polyline encode, waypoints, twistiness) and stop the service.
6. Rides under ~50 m are flagged as short in the stop result (logged); they are still saved.

### Destination → route → petrol / weather
1. Search sheet → Places (or fallback geocoders) → select result → Directions / OSRM polyline on the map.
2. Weather samples the route asynchronously; petrol search uses GPS + prefs + OSM/Places and presents a ranked sheet.
3. **Go** on a station sets destination and navigates in-app.

### History → summary → map / share / replay
1. History loads trips newest-first; tab / search / date filter refine the list.
2. Open summary → moments, rename, favorite, delete, share.
3. Share: PNG card (`RideShareCard`) or GPX (`GpxExporter` + `GpxShare`) via FileProvider (`cache/share/`).
4. View route → Maps Compose screen with layers, waypoints, and **route replay**.

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
| Places | Google Places SDK 4.x (autocomplete, text search, photos, isOpen) |
| Network | OkHttp (Overpass, OSRM, Open-Meteo, Static Maps, Places REST fallbacks) |
| Secrets | Maps Secrets Gradle Plugin → `MAPS_API_KEY` |

Versions live in `gradle/libs.versions.toml`.

---

## Permissions & components

**Permissions:** `INTERNET`, fine/coarse/background location, `POST_NOTIFICATIONS`, foreground service + location type.

**Components:**
- `MainActivity` — Compose entry, `MotoTripNavHost`
- `TripForegroundService` — sticky location FGS, notification channel `ride_channel`
- `FileProvider` — `${applicationId}.fileprovider` → `res/xml/file_paths.xml`

A Google Maps API key is required for map screens, Places (search / petrol details / photos), Directions, and Static Maps previews (Secrets plugin → `MAPS_API_KEY` in `local.properties`). Enable the matching APIs in Google Cloud for best results. Overpass speed limits and Open-Meteo weather use public HTTP endpoints (no Maps key).

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
- `GpsQualityTest` — GPS bar thresholds
- `TwistinessCalculatorTest` — score / rating bands
- `GoogleWeekdayHoursParserTest` — Google weekday text → open/closed

Instrumented / Compose UI tests are mostly scaffold; ride and ObjectBox flows are not fully covered by instrumentation yet.

---

## Building

1. Open the project in Android Studio (or use the Gradle wrapper).
2. Provide a Maps API key for the Secrets plugin (e.g. `MAPS_API_KEY=…` in `local.properties`).
3. In Google Cloud, enable **Maps SDK for Android**, **Places API (New)**, **Directions API**, and **Maps Static API** (for petrol detail map previews).
4. Sync and run the `:app` debug configuration on a device or emulator with Google Play services.

```bash
./gradlew :app:assembleDebug
```

---

## Design notes

- **UI → use cases → data/domain** keeps screens thin and testable; tracker overlays use focused services (`NavigationService`, `FuelService`, petrol, weather).
- **Foreground service + singleton `TripManager`** is the source of truth for an active ride, not the Compose lifecycle.
- **ObjectBox** is on-device only; there is no account or cloud sync in this codebase.
- History UX separates **Favorites** into its own tab and uses **date filters** for range queries; free-text search stays focused on naming and related fields.
- Petrol ranking mirrors the iOS twin: prefs → open status → highway bias → distance, with Google hours/photos when a Place match exists.
