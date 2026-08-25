         ┌──────────────────────────────┐
         │        Dashboard UI          │  <-- View
         │  (Compose)                   │
         │  Observes StateFlow          │
         │  Shows speed, trip stats     │
         └─────────────┬────────────────┘
                       │ observes (hiltViewModel)
                       ▼
         ┌──────────────────────────────┐
         │       TripViewModel          │  <-- ViewModel (@HiltViewModel)
         │ Holds TripStats & TrackPoints│
         │ Receives updates from TripManager │
         │ Exposes ride data to UI      │
         │ Calls TripRepository to save trips │
         └─────────────┬────────────────┘
                       │ calls functions
                       ▼
         ┌──────────────────────────────┐
         │        Domain Layer          │  <-- Model / Business Logic
         │ TripManager                  │
         │ StopDetector                 │
         │ SpeedFilter                  │
         │ Calculates moving/stopped time,
         │ distance, speed, stop classification
         └─────────────┬────────────────┘
                       │ uses
                       ▼
         ┌──────────────────────────────┐
         │      Repository Layer        │  <-- Data Layer
         │ LocationRepository           │
         │ FusedLocationProviderClient  │
         │ TripRepository               │
         │  • Saves TripEntity to ObjectBox
         │  • Provides reactive query of trips
         └─────────────┬────────────────┘
                       │ provides data to
                       ▼
         ┌──────────────────────────────┐
         │  TripForegroundService       │  <-- Foreground Service (@AndroidEntryPoint)
         │ Keeps GPS tracking alive     │
         │ Sends location updates to TripManager │
         │ Saves completed trips to ObjectBox     │
         │ Runs even if UI is closed    │
         └──────────────────────────────┘

Dependency injection: Hilt (SingletonComponent + constructor injection)
Navigation: Jetpack Navigation Compose (tracker → history → summary → full route)
UI state: per-screen UiState exposed as StateFlow from feature ViewModels
Domain: use cases in domain/usecase (ViewModels depend on use cases, not repos/managers)

