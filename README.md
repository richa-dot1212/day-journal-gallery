# Day-Journal Gallery + ESP32 Light & Audio Companion

A photo/video gallery that groups your media by **month → day**, extracts each day's 3 dominant
colors into a gradient, lets you record a daily voice journal, and mirrors all of it to an
ESP32 companion: 3 WS2812B LEDs per calendar day, a physical slider to pick the month, and
31 buttons to play back each day's audio.

Full spec: [`photo-gallery-esp32-app-prompt.md`](photo-gallery-esp32-app-prompt.md).
Build plan & milestones: `.claude/plans/read-the-md-file-fluttering-noodle.md`.

## Repo layout

| Path | What |
|---|---|
| `shared/` | Kotlin Multiplatform core — media, color extraction, persistence, sync protocol |
| `androidApp/` | Jetpack Compose Android app |
| `iosApp/` | SwiftUI app (milestone M10 — not started) |
| `firmware-esp32/` | PlatformIO firmware for the companion device |
| `docs/` | [protocol](docs/protocol.md), [wiring](docs/esp32-wiring.md), [architecture](docs/architecture.md) |

## Status

| Milestone | State |
|---|---|
| M1 Android gallery MVP (grouping, permissions, calendar UI) | ✅ builds |
| M2 Color pipeline (extractor, aggregator, cache, incremental sweep) | ✅ core done, unit-tested |
| M3 Audio journaling (record / play / re-record, sync-state tracking) | ✅ core done (records AAC `.m4a` — see below) |
| M4 ESP32 firmware skeleton (LEDs, NVS, Wi-Fi, `/status`, serial console) | ✅ written, not yet flash-tested |
| M5 Color sync (REST client, discovery, SyncQueue, pairing screen) | ✅ code complete, needs on-device test |
| M6 Slider → month switching | 🔶 firmware done; app-side event wiring pending |
| M7 Audio transfer + SD + DFPlayer | 🔶 endpoints + SD store done; DFPlayer codec caveat |
| M8 Button matrix → playback → app sync | 🔶 firmware done; app navigation on event pending |
| M9 Polish · M10 iOS port | ⬜ not started |

Known deviation: Android `MediaRecorder` can't emit MP3, so the app records **AAC `.m4a`**.
The DFPlayer Mini plays MP3/WAV only — resolve at M7 by bundling a LAME encoder in
`shared/androidMain` or swapping the firmware audio module for an I2S DAC. Tracked as
"Open Item 1" in the build plan.

## Building the Android app

Requires the Android SDK (compileSdk 35) and JDK 17–25. AGP 8.13.2 / Gradle 8.13 accept
Android Studio's bundled JDK (JBR 25) — no separate JDK needed if you build from the IDE.
From the CLI, point `JAVA_HOME` at any 17–25 JDK.

```bash
# from repo root
./gradlew :androidApp:assembleDebug          # -> androidApp/build/outputs/apk/debug/
./gradlew :shared:testDebugUnitTest          # shared unit tests
./gradlew installDebug                        # to a connected device/emulator
```

Or just open the repo in Android Studio and let it sync.

First launch asks for media permission (`READ_MEDIA_IMAGES/VIDEO`, or `READ_EXTERNAL_STORAGE`
on API < 33) and, when you first record, `RECORD_AUDIO`.

## Building the firmware

Requires [PlatformIO](https://platformio.org/) (`pip install platformio` or the VS Code ext).

```bash
cd firmware-esp32
# edit src/config.h — Wi-Fi credentials and any pin changes
pio run                    # compile
pio run -t upload          # flash
pio device monitor         # serial console @ 115200
```

Serial test commands: `month N`, `day N`, `testday N RRGGBB RRGGBB RRGGBB`, `play M D`, `status`.

## Pairing

With the ESP32 on the same Wi-Fi (or connected to its `DayJournal-ESP32` SoftAP), open the
app's device screen (chip icon, top-right). It discovers `dayjournal.local` over mDNS; tap
**Pair**. `GET /status` then shows firmware version, slider month and SD free space.
