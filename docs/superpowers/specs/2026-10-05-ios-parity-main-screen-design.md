# Design: iOS-parity tracker main screen (layout + dial)

**Date:** 2026-10-05  
**Status:** Approved  
**Scope:** Android idle/tracking dashboard only. Navigating HUD unchanged this pass.

## Goal

Match the iOS tracker main screen visual structure and dial style:

- Full-bleed live map as the backdrop
- Floating dial + G-force + stats over the bottom of the map
- Destination / petrol / fuel chips and Start/Pause/Stop under that stack
- Speedometer as a neon progress arc (green to limit, red past limit) with center limit badge + large km/h — **no needle**

## Non-goals

- iOS liquid `glassEffect` (use Material translucent panels / radial scrim)
- Navigating turn-header / glance-bar parity with iOS
- Changing Roads snap, speed-limit logic, or navigation phase machine

## Layout

When `!navigation.isNavigating`:

```
┌─────────────────────────────┐
│ Map (fillMaxSize)           │
│  top: GPS / battery / menu  │
│                             │
│  bottom overlay:            │
│   ┌─ dial (glass scrim) ─┐  │
│   │ G-force               │  │
│   │ stats (scroll ~260dp) │  │
│   └───────────────────────┘  │
│   search / petrol / fuel     │
│   Start | Pause / Stop       │
└─────────────────────────────┘
```

When navigating: keep existing `mapHud` path (`CompactTurnChip`, floating nav dial, utility rail).

Hide the main dial stack when a map-place Go card is showing (iOS `showsMainDial` parity), so the card isn’t crowded.

## Dial

Rewrite `SpeedometerArc` (dashboard / floating) to iOS arc semantics:

- Track arc 135° + 270° sweep
- Progress: green while under limit; past limit = green to notch + red beyond
- Soft blur glow under the progress stroke
- Limit notch on the ring
- Center: `SpeedLimitSign` above large speed readout + `km/h`
- Remove needle and tick marks (iOS main dial has none)
- Optional `floating` radial scrim retained for map overlay

Default `maxSpeedKmh = max(stats.maxSpeed, 260)`.

## Controls

Move `TrackerBottomBar` into the map bottom overlay (transparent background, no solid `bgDeep` slab) so it floats over the map like iOS capsules. Keep existing labels and confirm-end-ride behavior.

## Files

- `RideTrackerScreen.kt` — scaffold: full-bleed map + overlays for idle/tracking
- `RideTrackerInstruments.kt` — dial rewrite
- `RideTrackerBottomBar.kt` — translucent / overlay-friendly styling
- `docs/Navigation.md` / `README.md` — note map-first dashboard

## Success criteria

- Idle/tracking looks map-first with floating dial like iOS
- Dial is arc+badge (no needle)
- Navigating HUD still works as today
- `compileDebugKotlin` succeeds
