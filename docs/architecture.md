# Architecture

## Modules

```
:shared        Kotlin Multiplatform — all non-UI logic (Android + iOS targets)
:androidApp    Jetpack Compose UI, MVI ViewModels, Koin DI
iosApp/        SwiftUI (milestone M10 — not yet built)
firmware-esp32 PlatformIO / Arduino firmware
```

## `:shared` layout

| Package | Responsibility | Platform bits |
|---|---|---|
| `domain` | Value types: `MonthKey`, `DayKey`, `Rgb`, `DayColors`, `MediaItem`, `AudioEntry`, `DeviceInfo` | — |
| `media` | `MediaSource` (expect-style interface), `MediaGrouping` (Month→Day), `MediaRepository` | Android `MediaStore`, iOS `PHPhotoLibrary` |
| `color` | `ColorExtractor` (median-cut + weighted k-means, k=3), `DayColorAggregator`, `ColorPipeline` (incremental + cached) | pixel decode only |
| `data` | SQLDelight `JournalDatabase`, `ColorCache`, `AudioStore`, `PairingStore`, `DriverFactory` (expect) | driver |
| `audio` | `AudioRecorder` / `AudioPlayer` interfaces, `FileBytes` (expect) | `MediaRecorder`, `AVAudioRecorder` |
| `sync` | `Protocol` DTOs, `DeviceSyncClient` (Ktor HTTP+WS), `DeviceDiscovery` (expect mDNS), `SyncQueue` | OkHttp / Darwin engine, `NsdManager` / `NSNetService` |
| `work` | `BackgroundScheduler` interface | WorkManager / BGTaskScheduler |
| `di` | Koin `sharedModule` + `expect fun platformModule()` | platform bindings |
| `util` | `Crc32`, JSON blob codecs | — |

## expect/actual boundary

Only these are `expect`: `DriverFactory`, `FileBytes`, `platformModule()`, `defaultHttpClient()`.
Everything else that needs a platform is an ordinary interface bound in `platformModule()` —
easier to fake in tests and to add a third platform later.

## Data flow (color)

```
MediaStore ─► MediaRepository.refresh() ─► MediaGrouping ─► List<MonthBucket>
                                                              │
WorkManager ─► ColorPipeline.sweep() ─► per item: ColorExtractor ─► ColorCache.item_color
                                     └► per day:  DayColorAggregator ─► ColorCache.day_color
                                                                        │  emits DayColorReady
GalleryViewModel ◄──────────────────────────────────────────────────────┘
        │
        └► SyncQueue.drain() ─► DeviceSyncClient ─► POST /month/{m}/colors ─► ESP32 NVS ─► LEDs
```

## Data flow (events from device)

```
ESP32 slider/button ─► WS /events ─► DeviceSyncClient.events() Flow
                                        │
   (wired in M6/M8) ─► GalleryViewModel switches month / navigates to day
```
