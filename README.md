         ┌──────────────────────────────┐
         │        Dashboard UI          │  <-- View
         │  (Compose / Fragment)        │
         │  Observes StateFlow/LiveData │
         │  Shows speed, trip stats     │
         └─────────────┬────────────────┘
                       │ observes
                       ▼
         ┌──────────────────────────────┐
         │       TripViewModel          │  <-- ViewModel
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
         │  TripForegroundService       │  <-- Foreground Service
         │ Keeps GPS tracking alive     │
         │ Sends location updates to TripManager │
         │ Saves completed trips to ObjectBox     │
         │ Runs even if UI is closed    │
         └──────────────────────────────┘
