# Day-Orb BLE Companion

A physical orb that represents one calendar day: it glows with that day's 3 dominant colors
and has a button that opens that day in the app. Proof-of-concept is **one orb**; the code
scales to **31** (one per day of a month) without app changes.

## Transport

BLE. The phone is **central**, the orb is **peripheral**. The app scans for the service UUID,
connects to the first match, and keeps reconnecting. Swapping to Wi-Fi later means writing one
new `OrbTransport` implementation — nothing else changes.

## GATT contract (orb firmware must implement this)

| Item | UUID | Properties | Payload |
|---|---|---|---|
| Service | `6b1c1500-6a2a-4b1a-9b1e-8f7c2a3d9e10` | — | — |
| Day-selected | `6b1c1501-6a2a-4b1a-9b1e-8f7c2a3d9e10` | **Notify** | 2 bytes: `[month (1–12), day (1–31)]`, sent on button press |
| Colors | `6b1c1502-6a2a-4b1a-9b1e-8f7c2a3d9e10` | **Write** (no response ok) | 9 bytes: `R0 G0 B0 R1 G1 B1 R2 G2 B2` (day's 3 dominant colors, most-prominent first) |

The orb must advertise the service UUID so the scan filter finds it. Standard CCCD
(`00002902-…`) on the notify characteristic; the app enables notifications after connecting.

## App behaviour

- **Button press** → app resolves the year (most recent year in the library with media on that
  date, else the current year) and navigates to the existing day screen. It also re-pushes that
  day's colors to the orb.
- **Opening a day** in the app (`DayDetailScreen`) → pushes that day's colors to the orb once
  they've been computed by the existing color pipeline. (POC choice: the orb mirrors the day
  you're looking at. With 31 orbs, pre-populate `OrbRegistry` and call
  `OrbController.syncAllBoundDays()` after a month finishes computing.)
- **Permissions**: `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` on Android 12+, `ACCESS_FINE_LOCATION`
  below that. Requested non-blockingly from the gallery screen; the gallery works without them.

## Code map

| Concern | File |
|---|---|
| Transport interface, events, registry, GATT constants + 9-byte codec | `shared/commonMain/.../orb/Orb.kt` |
| Orchestration (year resolution, event → navigation, color push) | `shared/commonMain/.../orb/OrbController.kt`, `DayResolution.kt` |
| Android BLE central | `shared/androidMain/.../orb/BleOrbTransport.kt` |
| DI wiring | `shared/commonMain/.../di/SharedModule.kt`, `shared/androidMain/.../di/PlatformModule.android.kt` |
| Permissions + lifecycle + nav hookup | `androidApp/.../ui/orb/OrbConnection.kt`, wired in `AppNavHost.kt` |
| Color push on open day | `androidApp/.../ui/day/DayDetailScreen.kt` |
| Connection indicator | gallery top bar (`MonthGridScreen.kt`) |

## Scaling to 31 orbs

1. `BleOrbTransport`: don't `stopScan()` after the first hit — connect each distinct address,
   track one `BluetoothGatt` per orb (keyed by `OrbId`).
2. Populate `OrbRegistry` with all 31 `OrbId → DayKey` bindings for the visible month.
3. Call `OrbController.syncAllBoundDays()` when a month's colors finish computing.

The gallery, navigation, and color-extraction code are untouched by any of that.
