# Day-Orb BLE Companion

A physical calendar: **one ESP32** with N orbs, each orb a single LED (that day's dominant
color) plus a button under it. Press an orb → that day's photos open in the app. It is one
BLE connection, not N devices.

POC hardware: **5 orbs**, September days 1–5 (`OrbController.calendarMonth` / `calendarDayNumbers`,
must match `ORB_MONTH` / `ORB_DAYS[]` in the firmware — `firmware-esp32-arduino/DayCalendar/`).
Scaling to 31: extend those arrays and read the extra buttons through 74HC165 shift registers.
Nothing in the app changes.

## Transport

BLE. The phone is **central**, the orb is **peripheral**. The app scans for the service UUID,
connects to the first match, and keeps reconnecting. Swapping to Wi-Fi later means writing one
new `OrbTransport` implementation — nothing else changes.

## GATT contract (orb firmware must implement this)

| Item | UUID | Properties | Payload |
|---|---|---|---|
| Service | `6b1c1500-6a2a-4b1a-9b1e-8f7c2a3d9e10` | — | — |
| Day-selected | `6b1c1501-6a2a-4b1a-9b1e-8f7c2a3d9e10` | **Notify** | 2 bytes: `[month (1–12), day (1–31)]`, sent when an orb's button is pressed |
| Colors | `6b1c1502-6a2a-4b1a-9b1e-8f7c2a3d9e10` | **Write** | `orbCount * 3` bytes: one `R G B` per orb, in orb order (orb 0 = first configured day). Firmware maps triple *i* → LED *i*. |

The device must advertise the service UUID so the scan filter finds it. Standard CCCD
(`00002902-…`) on the notify characteristic; the app enables notifications after connecting.
Payload stays under the 20-byte default BLE MTU up to 6 orbs; beyond that the app negotiates a
larger MTU (or the firmware exposes per-day writes).

## App behaviour

- **Button press** → app resolves the year (most recent year in the library with media on that
  date, else the current year), navigates to the existing day screen, and re-pushes the whole
  calendar's colors.
- **On connect** and **on opening any day** → `OrbController.syncCalendar()` pushes one color
  per orb (each day's most-prominent color; days with no computed color go out as off) in a
  single write.
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
2. Extend `OrbController.calendarDayNumbers` to `1..31` (and `ORB_DAYS[]` in the firmware).
3. Beyond ~6 orbs the color write exceeds the default BLE MTU — request MTU 247 in
   `BleOrbTransport` after connecting, or split into per-day writes.

The gallery, navigation, and color-extraction code are untouched by any of that.
