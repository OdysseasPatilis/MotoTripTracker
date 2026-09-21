# Turn-by-turn navigation

How in-ride navigation works in MotoTripTracker (Android): the ideas behind it, then how the code implements them.

> **Scope.** This document covers *destination guidance* on the live ride map — search, route preview, follow-along, voice, off-route recalculation, motorcycle ETA learning.  
> It does **not** cover Compose screen routing (`ui/navigation/MotoTripNavHost`, `Routes`). That package is app-level screen navigation, unrelated to turn-by-turn.

Related: [API-and-Storage.md](./API-and-Storage.md) lists the remote APIs and prefs keys used here.

---

## Part I — Theory

### 1. What “navigation” means here

A motorcycle ride tracker is not a full Google Maps replacement. Navigation in this app is a **guided overlay on the live ride**:

1. Pick a destination (search, history, map POI, or petrol station).
2. Preview one or more driving routes with motorcycle-aware ETAs.
3. Start guidance: show the next maneuver, remaining distance, moto ETA.
4. Follow the rider’s GPS along the polyline: advance steps, announce approaches, detect off-route, recalculate, detect arrival.
5. After the leg ends, compare actual time to the car traffic ETA and (optionally) learn how much “filter benefit” this rider typically gets.

Trip recording (ObjectBox track points, twistiness, G-force, etc.) runs independently. Navigation consumes the same live location stream but does not own the trip lifecycle.

### 2. The phase model

Guidance is a small explicit state machine with three phases:

```
                    set destination
        Idle ──────────────────────────► Previewing
         ▲                                   │
         │                                   │ confirm Start
         │                                   ▼
         └──────── clear / arrive ──── Navigating
```

| Phase | Meaning |
|-------|---------|
| **Idle** | No destination. Search UI is available. Optional post-trip timing banner may still be present. |
| **Previewing** | Destination is set. Alternate routes (when available) are shown. Map draws the selected polyline. Voice is off for guidance. Weather is **not** yet bound to the route. |
| **Navigating** | Active guidance. Maneuver banner, remaining distance, off-route / recalc, voice prompts, arrival detection. Weather refreshes when a route is applied. |

Why preview is separate from navigating:

- The rider can compare routes and cancel without starting a timing sample.
- Weather sampling is expensive and only runs when a route is *applied* for real guidance (start or recalculation), not when flipping preview chips.
- Timing learning only counts legs that actually entered `Navigating` and lasted long enough.

### 3. Origin vs destination vs route

Three geometric concepts stay distinct:

| Concept | Role |
|---------|------|
| **Origin** | Current rider GPS. Updated continuously. Kept across `clear()` so the next destination can route immediately. |
| **Destination** | Fixed lat/lng + display name until cleared. |
| **Route** | Ordered polyline + step list from a directions provider. “Active” route coordinates drive remaining-distance math and map drawing. |

Progress is not “fraction of time elapsed.” It is **geometry first**: find where the rider sits relative to the polyline, then derive remaining meters and ETA from that.

### 4. Snapping and remaining distance

Commercial SDKs often project the rider onto the nearest *segment* (perpendicular foot). This app uses a simpler, robust approach that is easy to test:

1. Find the **nearest vertex** on the route polyline to the current GPS fix.
2. Remaining distance ≈ distance from rider to that vertex **plus** the sum of edge lengths from that vertex to the end.

Trade-offs:

- Cheap and deterministic (`NavigationProgressLogic.nearestOnRoute`).
- Vertex density depends on the overview polyline; sparse polylines can slightly over/under-estimate remaining meters.
- Off-route uses the same nearest-vertex distance (not segment distance).

### 5. Off-route detection and hysteresis

GPS noise and brief cut-throughs should not thrash recalculation.

- **Off-route** when nearest-vertex distance **> 80 m**.
- **Clear off-route flag** only when distance **≤ 40 m** (half threshold) — classic hysteresis so the flag does not flicker around the boundary.
- Recalculation itself is further rate-limited (**12 s** cooldown) so a noisy stretch does not spam Directions.

While off-route, the UI shows “Off route — recalculating”; when a new route arrives, step indices and announce bookkeeping reset.

### 6. Step advancement and voice

Each route is broken into **steps** (maneuvers): instruction text, step length, and an **end lat/lng** (the maneuver point).

- When the rider is within **35 m** of the current step’s end (and it is not the last step), the current step index advances.
- Within **250 m** of a step end, speak once: `"In {distance}, {instruction}"`.
- On advance, speak the new instruction once and fire a short haptic.

Dedupe keys (`approachedStepId`, `announcedStepId`) prevent repeating the same prompt for the same step UUID.

### 7. Arrival

Arrival must not fire from a single noisy GPS sample near the pin, and must not fire early when the rider is merely near the pin but still has a long remaining polyline (e.g. one-way loop).

Complete arrival only if **all** of:

1. Distance to destination pin ≤ **45 m**.
2. Either remaining route ≤ **120 m**, **or** the rider is already on the **final** step.
3. Those conditions have held continuously for **≥ 2.5 s** (dwell).

Leaving the candidate region resets the dwell timer.

### 8. Motorcycle travel time (theory)

Directions APIs return **car** ETAs, often with live traffic (`duration_in_traffic`). Motorcycles often spend less time in congestion (filtering, lane use, different stop behavior). The app models that as a personal **filter benefit** \(b \in [0.15, 0.75]\), default **0.45**.

Definitions:

- Free-flow baseline speed: **50 km/h** → \(v = 50/3.6\) m/s.
- Baseline time: \(\max(\text{distance}/v,\ 45\text{s})\).
- Traffic delay: \(\max(0,\ t_{\text{car}} - \text{baseline})\).
- Moto ETA: \(\max(\text{baseline} + \text{delay}\cdot(1-b),\ 30\text{s})\).

Intuition: the rider always pays roughly free-flow time for the distance; of the *extra* car delay, they only absorb a fraction \((1-b)\).

After a guided leg (≥ 45 s actual, with a car plan), the app can **learn** \(b\) from observed actual vs delay:

\[
b_{\text{obs}} = 1 - \frac{t_{\text{actual}} - \text{baseline}}{\text{delay}}
\]

then EMA-blend:

\[
b_{\text{new}} = 0.72\, b_{\text{old}} + 0.28\, b_{\text{obs}}
\]

(both clamped to \([0.15, 0.75]\)). Learning is skipped when the sample is too short or traffic delay is negligible.

Live ETA while navigating scales the planned moto total by remaining/total distance (same fraction as geometry remaining).

### 9. Destination discovery cascade

Place search is a **fallback ladder**. Prefer Google quality when the key works; degrade to open geocoders when Google returns nothing:

1. Places SDK **SearchByText** (location-biased ~50 km).
2. Places SDK **Autocomplete** (session token).
3. Places REST text search.
4. **Photon** (Komoot).
5. **Nominatim** (OSM).

Autocomplete/session tokens are reset after a successful pick so billing/session semantics stay correct. Photon/Nominatim results already carry coordinates; Google place IDs may need a Place Details resolve before preview can start.

### 10. Routing providers

Route fetch is also a ladder:

1. **Google Directions** (`mode=driving`, `departure_time=now` for traffic, optional `alternatives=true`).
2. If empty → **OSRM** public router once (single route, no traffic).

Overview polylines are decoded with Maps Android `PolyUtil`. Google steps use stripped `html_instructions`; OSRM steps are synthesized from maneuver `type` / `modifier` / road `name`.

Preview requests alternatives; **recalculation always requests a single route**.

### 11. Side effects: weather and cameras

| Concern | Coupling |
|---------|----------|
| **Route weather** | Tight. `onRouteApplied(coords, motoTravelTime)` → sample weather along the polyline using moto duration for segment timing. `onRouteCleared` clears. Preview-only selection does **not** apply weather. |
| **Traffic cameras** | Loose. Share TTS (`NavigationVoicePrompt`) and distance formatting. Camera alerts are based on rider position/heading, **not** the nav polyline. |

### 12. End-to-end mental model

```
User picks destination
  → Previewing + history write
  → Directions (+ alternates) + moto ETA per option
  → Rider selects route chip (map/ETA only)
  → Start → Navigating → onRouteApplied → weather
  → GPS ticks → remaining / ETA / steps / voice / off-route / arrival
  → Clear or arrive → timing learn + Idle (+ weather clear)
```

---

## Part II — Implementation

### 13. Package map

Primary package: `com.odys.mototriptracker.data.navigation`.

| File | Role |
|------|------|
| `NavigationService.kt` | Singleton orchestrator: phase machine, search debounce, route jobs, GPS progress, voice/haptics, timing finalize, weather callbacks |
| `NavigationState.kt` | Immutable UI state + `NavStep` / `NavRouteOption` / `NavigationPhase` + display helpers |
| `NavigationProgressLogic.kt` | Pure math: nearest-on-route, ETA fraction, step advance, approach, arrival dwell, off-route hysteresis |
| `DirectionsRouter.kt` | Google Directions + OSRM → `DirectionsResult` |
| `DestinationSearcher.kt` | Places / REST / Photon / Nominatim + map-place resolve |
| `DestinationSearchHistory.kt` | SharedPreferences recent destinations |
| `DestinationSearchHistoryLogic.kt` | Pure prepend / dedupe / cap |
| `MotoTravelEstimator.kt` | Formula + learn + `NavTimingResult` copy |
| `MotoTravelEstimatorStore.kt` | Persist `filterBenefit` |
| `NavigationVoicePrompt.kt` | Android TTS wrapper |
| `NavigationHttp.kt` | OkHttp GET + Places Task await helper |
| `PickedMapPlace.kt` | Map POI card model |

UI / application wiring:

| File | Role |
|------|------|
| `application/RideTrackerFacade.kt` | Only nav API the ViewModel should use; wires weather callbacks |
| `application/TrackerModels.kt` | Typealiases so UI imports application types, not `data.*` |
| `ui/tracker/RideTrackerViewModel.kt` | Combines `facade.navigation` into UI state; GPS → `updateNavigationOrigin` |
| `ui/tracker/RideTrackerScreen.kt` | Composes search / preview / maneuver / active chip / timing overlays |
| `ui/tracker/RideTrackerNavOverlays.kt` | `RoutePreviewCard`, `ManeuverBanner`, `ActiveRouteChip`, `TimingResultBanner` |
| `ui/tracker/DestinationSearchSheet.kt` | Search + history UI |
| `ui/tracker/LiveRideMapView.kt` | Polylines, dest marker, POI click |
| `ui/tracker/MapPlaceCoordinator.kt` | POI resolve → Go → `setDestination` |
| `ui/tracker/PetrolSearchCoordinator.kt` | Petrol Go → `setDestination` |

Unit tests: `NavigationProgressLogicTest`, `NavigationStateLogicTest`, `MotoTravelEstimatorTest`, `DestinationSearchHistoryLogicTest`.

### 14. Call chain (UI → service)

```
RideTrackerRoute / Screen
  → RideTrackerViewModel (+ MapPlaceCoordinator, PetrolSearchCoordinator)
    → RideTrackerFacade
      → NavigationService
      → DestinationSearchHistory (list / remove only)
```

The UI **never** injects `NavigationService` directly. Facade exposes `navigation: StateFlow<NavigationState>` and thin forwards (`updateSearchQuery`, `confirmStartNavigation`, …).

Weather hooks in facade `init`:

```kotlin
navigationService.onRouteApplied = { coordinates, travelTime ->
    routeWeatherService.refreshForRoute(coordinates, travelTime)
}
navigationService.onRouteCleared = routeWeatherService::clear
```

### 15. Live GPS feed

In `RideTrackerViewModel` init, every `facade.lastLocation` update calls `facade.updateNavigationOrigin(lat, lng)`, which calls `NavigationService.updateOrigin`.

`updateOrigin` behavior:

1. Store `originLat` / `originLng`.
2. If **Previewing**, no routes yet, not currently routing, has destination → kick `computeRoute(..., requestAlternates = true)` (handles “destination chosen before first GPS fix”).
3. If there is a route polyline → `recomputeRemaining`.
4. If **Navigating** → `advanceStepIfNeeded`, `checkOffRouteAndRecalculate`, `checkArrival`.

Origin survives `clear()`; destination and route do not.

### 16. `NavigationState` fields

Published via `StateFlow`; UI reads derived properties.

| Field | Purpose |
|-------|---------|
| `searchQuery` / `searchResults` / `isSearching` / `searchError` | Destination search sheet |
| `destinationName` / `destinationLatitude` / `destinationLongitude` | Target |
| `routeCoordinates` | Active / preview polyline drawn on map |
| `distanceRemainingMeters` | Live remaining along route |
| `etaEpochMs` | Clock time for moto ETA |
| `isRouting` / `isRecalculating` / `isOffRoute` | Status flags |
| `isVoiceEnabled` | TTS mute (also used by cameras sharing the same prompt) |
| `steps` / `currentStepIndex` / `distanceToNextManeuverMeters` | Maneuver banner |
| `phase` | Idle / Previewing / Navigating |
| `previewRoutes` / `selectedRouteId` / `previewErrorMessage` | Multi-route preview |
| `plannedCarTravelTimeSeconds` / `plannedMotoTravelTimeSeconds` | Timing + traffic hint |
| `lastTimingResult` | Post-trip banner; learning already applied when set |

Derived helpers of note:

- `summaryText` → `"1.2 km · Moto ETA 3:45"`.
- `trafficHintText` → `"Cars +N min"` when car − moto ≥ **90 s** (preview or navigating).
- `guidanceSummary` → recalculating / off-route / maneuver line / else summary.

Distance formatting: ≥ 1 km → one decimal km; else integer meters. Duration formatting goes through `MotoTravelEstimator.formatMinutes` (minimum 1 minute display).

### 17. Search → preview

**Typing** (`updateSearchQuery`):

- No-op if query unchanged.
- Blank → cancel job, clear results.
- Else debounce **350 ms**, then `destinationSearcher.search(query, originLat, originLng)`.
- Stale-query guard: ignore results if the query changed while in flight.
- Empty results → user-facing `searchError`.

**Selecting a search result** (`selectSearchResult`):

- Use coords if present; else `resolvePlace(placeId)`.
- On failure → error string; on success → `resetAutocompleteSession()` then `beginPreview`.

**History / POI / petrol** call `beginPreview` / `setDestination` directly (history and map places already have coordinates).

**`beginPreview`**:

1. Bump `routeRequestGeneration` (invalidate in-flight route jobs).
2. `destinationHistory.add(...)`.
3. Reset approach/announce IDs; `voice.stop()`.
4. Set destination fields; clear previous route/steps/preview; `phase = Previewing`.
5. `computeRoute(isRecalculation = false, requestAlternates = true)`.

### 18. Route computation

`computeRoute`:

1. Require origin + destination; if previewing without origin → `"Waiting for your location…"`.
2. Cancel prior `routeJob`; bump generation; set `isRouting` or `isRecalculating`.
3. Launch: `directionsRouter.fetchRoutes(..., alternatives = requestAlternates && !isRecalculation)`.
4. Drop result if generation mismatch (stale).
5. Recalc path: only apply if still Navigating; take first route → `applyRoute(..., isRecalculation = true)`.
6. Preview path: only apply if still Previewing → `applyPreviewRoutes`.

**`applyPreviewRoutes`**: map each `DirectionsResult` to `NavRouteOption`, select the first via `applyPreviewSelection`. Does **not** invoke `onRouteApplied`.

**`applyPreviewSelection`**: updates selected polyline, planned times, steps, ETA from moto duration; stays Previewing.

**`confirmStartNavigation`**:

1. Require `selectedPreviewRoute` and Previewing.
2. Record `navigationStartedAtMs`, planned car/moto times; clear `lastTimingResult`.
3. `phase = Navigating` then `applyRoute` from the selected option (not a new network call).
4. `applyRoute` invokes **`onRouteApplied`** → weather.

**`applyRoute`** (start or recalc): resets announce IDs, stops voice, sets Navigating fields, clears preview list, fires `onRouteApplied`.

### 19. DirectionsRouter details

`fetchRoutes`: Google first; if empty, OSRM once wrapped as a single-element list.

**Google URL shape:**

```
directions/json?origin=…&destination=…&mode=driving
  &departure_time=now
  [&alternatives=true]
  &key=…
```

Per route / first leg:

- Distance from `distance.value`.
- Car time from `duration_in_traffic.value` else `duration.value`.
- `motoTravelEstimator.estimate(distance, carTime)`.
- Decode `overview_polyline.points`.
- Steps: strip HTML tags from `html_instructions`; take `end_location` + step distance.

**OSRM URL shape:**

```
router.project-osrm.org/route/v1/driving/{lng},{lat};{lng},{lat}
  ?overview=full&geometries=polyline&steps=true
```

Uses OSRM `duration` as the “car” input to the same moto estimator (no live traffic). Instruction builder covers depart / arrive / turn / merge / ramps / fork / roundabout / etc.

### 20. Progress loop (Navigating)

On each GPS fix with a route:

**Remaining + ETA** (`recomputeRemaining`):

```text
nearest = nearestOnRoute(lat, lng, routeCoordinates)
nearestRouteDistanceMeters = nearest.distanceMeters
distanceRemainingMeters = nearest.remainingMeters
etaEpochMs = now + totalTravelTimeSeconds * clamp(remaining/total, 0..1) * 1000
```

`totalTravelTimeSeconds` is the **moto** planned total for the current applied route.

**Steps** (`advanceStepIfNeeded`):

- Update `distanceToNextManeuverMeters` to distance to current step end.
- Maybe approach-announce at ≤ 250 m.
- `advancedStepIndex` may skip forward through multiple steps if the rider jumped past several ends (while loop).
- On index change: clear approach id, announce new step, light haptic (25 ms).

**Off-route** (`checkOffRouteAndRecalculate`):

- Skip if no dest/route or already routing/recalculating.
- If `nearestRouteDistanceMeters > 80` → set `isOffRoute`; if cooldown ≥ 12 s → `computeRoute(isRecalculation = true)`.
- Else if flagged off-route and distance ≤ 40 m → clear flag.

**Arrival** (`checkArrival` → `completeArrival`):

- Uses `NavigationProgressLogic.arrivalTick` with dest distance, remaining, step index.
- On complete: success haptic waveform → `finalizeTimingIfNeeded` → `clear(stopVoice = false)` → TTS `"You have arrived"`.

### 21. Timing finalize and learning

`finalizeTimingIfNeeded` (on clear while a nav start exists, or just before arrival clear):

- Must still be Navigating when evaluated from clear path’s start check; arrival calls it before clear.
- Requires `plannedCarTravelTimeSeconds > 0` and actual ≥ **45 s**.
- Builds `NavTimingResult(distance, car, moto, actual)`.
- `motoTravelEstimator.learn(from = result)` updates persisted filter benefit.
- Stores `lastTimingResult` on state for the banner.

`NavTimingResult.summaryLine` copy:

- ≥ 45 s faster than car → “faster than car traffic ETA”.
- ≥ 45 s slower → “slower than…”.
- Else → “close to car traffic ETA”.

`dismissTimingResult` clears the banner; `clear` preserves `lastTimingResult` and voice preference into a fresh Idle `NavigationState`.

### 22. Voice and haptics

`NavigationVoicePrompt`:

- Prefs key `moto_nav_voice_enabled` in `moto_app_prefs` (default **on**).
- English locale preferred; prefer on-device English voice (avoid network / mismatched accents).
- Speech rate **0.95**; `QUEUE_FLUSH` so a new prompt cuts the previous.
- Shared with traffic camera alerts (`speak`).

Haptics in `NavigationService`: light one-shot on step advance; waveform on arrival.

### 23. Destination history

- Prefs store up to **20** entries, newest first (`DestinationSearchHistory.MAX_ENTRIES`).
- Dedupe: drop existing entries within **0.00025°** (~25 m) of the new pin before prepend (`DestinationSearchHistoryLogic`).
- Written on every `beginPreview` (search pick, history re-pick, POI Go, petrol Go all go through preview).

### 24. Map place resolve

1. `LiveRideMapView` POI click → ViewModel → `MapPlaceCoordinator`.
2. Optimistic `PickedMapPlace(isResolving = true)` then `facade.resolveMapPlace` → Places `FetchPlace` (name, address, phone, website, category, refined coords).
3. `MapPlaceGoCard` **Go** → `setDestination` → preview; card hidden while Previewing/Navigating.

### 25. External Google Maps handoff

`openInGoogleMaps`:

- Prefer `google.navigation:q=lat,lng&mode=d` with Maps package.
- Fallback browser Directions URL with `travelmode=driving`.

Does not stop in-app navigation by itself; the ActiveRouteChip clear / cancel does.

### 26. Concurrency and staleness

| Mechanism | Purpose |
|-----------|---------|
| `searchJob` | Cancel prior debounce/search |
| `routeJob` | Cancel prior Directions fetch |
| `routeRequestGeneration` | Ignore late responses after clear / new preview / newer request |
| Main-immediate scope | State updates stay on main for Compose collectors |

### 27. Constants cheat sheet

| Constant | Value | Where |
|----------|-------|--------|
| Search debounce | 350 ms | `NavigationService` |
| Recalc cooldown | 12 s | `NavigationService` |
| Off-route enter | 80 m | `NavigationProgressLogic` |
| Off-route clear | 40 m | half of 80 |
| Step advance | 35 m | same |
| Approach announce | 250 m | same |
| Arrival pin | 45 m | same |
| Arrival remaining gate | 120 m | same |
| Arrival dwell | 2.5 s | same |
| Free-flow speed | 50 km/h | `MotoTravelEstimator` |
| Default filter benefit | 0.45 | same |
| Benefit clamp | 0.15–0.75 | same |
| Learn EMA | 0.72 / 0.28 | `learnedBenefit` |
| Min timing sample | 45 s actual | `finalizeTimingIfNeeded` |
| Traffic hint threshold | 90 s car−moto | `NavigationState.trafficHintText` |
| History max | 20 | `DestinationSearchHistory` |
| History dedupe | ~25 m | `DestinationSearchHistoryLogic` |

### 28. What is intentionally out of scope

- No full turn-by-turn SDK (no Google Navigation SDK / Mapbox Navigation).
- No lane guidance or road-name matching beyond provider step text.
- No motorcycle-specific routing graph (still driving profile from Google/OSRM; moto is ETA-only).
- Cameras, petrol, and trip recording are adjacent products that share location/TTS, not the route state machine.
- Compose `ui/navigation` is app screen graph only.

### 29. How to extend safely

| Change | Prefer |
|--------|--------|
| Thresholds / arrival / off-route math | `NavigationProgressLogic` + unit tests |
| Moto ETA formula / learning | `MotoTravelEstimator` + tests; prefs via store |
| New search provider | Insert in `DestinationSearcher.search` cascade |
| New directions provider | Insert in `DirectionsRouter.fetchRoutes` |
| New UI chrome | Read `NavigationState` via facade; do not talk to service |
| Side effects on apply/clear | Facade callbacks (`onRouteApplied` / `onRouteCleared`) pattern |

---

## Quick reference: public `NavigationService` API

| Method | Effect |
|--------|--------|
| `updateSearchQuery` | Debounced place search |
| `updateOrigin` | GPS tick → progress / recalc / arrival |
| `selectSearchResult` | Resolve + preview |
| `selectHistoryEntry` | Preview from history |
| `setDestination` | Preview from coords |
| `resolveMapPlace` | POI details (suspend) |
| `beginPreview` | Enter Previewing + fetch alternates |
| `selectPreviewRoute` | Switch preview chip |
| `confirmStartNavigation` | Previewing → Navigating + weather |
| `cancelPreview` / `clear` | Reset to Idle (optional stop voice) |
| `dismissTimingResult` | Clear post-trip banner |
| `toggleVoice` | Persist mute |
| `openInGoogleMaps` | External handoff |
| `state` | `StateFlow<NavigationState>` |
| `onRouteApplied` / `onRouteCleared` | Weather (and future) hooks |
