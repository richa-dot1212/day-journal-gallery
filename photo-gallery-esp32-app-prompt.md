# Development Prompt: Day-Journal Gallery App + ESP32 Light & Audio Companion

## Project Summary
Build a mobile photo/video gallery app (Android first, iOS later — architect for both from day one) that doubles as a daily audio journal. The app groups media by day/month, extracts the 3 most prominent colors from each day's photos and video thumbnails, and blends them into a gradient. That gradient plus the date is transmitted wirelessly to a companion ESP32 device driving WS2812B LEDs (3 LEDs per calendar day, up to 31 days = 93 LEDs) so each day of the currently selected month is represented as a physical light. A physical **slider on the ESP32 selects the active month** (1–12): moving it re-lights the 31×3 LEDs with that month's day colors and tells the phone app to switch to that month's view. The ESP32 also stores compressed voice-journal audio per day on an SD card, plays it back through a DFRobot mini MP3 player + speaker, and exposes 31 physical buttons (one per day of the currently selected month) so a person can press a day's button to hear that day's journal — which also tells the phone app to open that same day.

Build this in phases. **Phase 1 = Android app with local gallery, grouping, and color extraction working standalone (no hardware needed).** Phase 2 = ESP32 firmware + wireless protocol + hardware integration. Phase 3 = iOS port.

---

## 1. Mobile App — Functional Requirements

### 1.1 Gallery ingestion
- Read all photos and videos from the device's media store (Android `MediaStore` API to start; abstract behind a repository interface so iOS `PHPhotoLibrary` can be swapped in later).
- For videos, use the system-generated thumbnail (`MediaStore.Video.Thumbnails` / `ThumbnailUtils`) rather than decoding video frames.
- Handle permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` on Android 13+, legacy `READ_EXTERNAL_STORAGE` fallback) gracefully with rationale UI.

### 1.2 Grouping / navigation
- Group all media into a two-level hierarchy: **Month → Day**.
- Day view: grid of that day's photos/videos, plus the day's extracted gradient and journal audio entry (if any).
- Month view: calendar-style or list of days, each day cell tinted/previewed with its computed gradient.
- Support fast scroll/jump to a specific month and day.

### 1.3 Color extraction
- For each photo and each video thumbnail, run a dominant-color extraction (k-means or median-cut quantization on downsampled pixels, k=3) to get that item's top 3 colors.
- For each **day**, aggregate the dominant colors across all of that day's items into one representative set of 3 colors (e.g., cluster all candidate colors for the day and pick the 3 largest clusters, weighted by pixel coverage/frequency).
- Build a linear gradient from those 3 colors for UI display (day cell background, day detail header).
- Cache computed per-item and per-day colors (e.g., Room/SQLite) keyed by media ID + content hash, so recomputation only happens for new/changed media.
- Run extraction off the main thread (WorkManager/background coroutine), incrementally, so first launch with a large library doesn't block the UI.

### 1.4 Audio journaling
- On any day view, allow recording a voice memo (one entry per day, or support re-recording/overwrite — decide and note in-app).
- Record using the platform recorder (`MediaRecorder` on Android) into a compressed format suitable for constrained wireless transfer and SD storage — target **Opus or AMR-WB at a low bitrate (e.g., 16–24 kbps)**, mono, since this is spoken voice, not music.
- Store the local copy in app storage; keep a sync/transfer state (pending / sent / confirmed-on-device) per day's audio.
- Playback controls in-app (independent of the ESP32 — the ESP32 is a secondary/physical playback surface, not the only one).

### 1.5 Wireless sync to ESP32 (see Section 3 for protocol)
- Discover and pair with the ESP32 device (BLE and/or local Wi-Fi — decide transport in Section 3).
- On demand (or automatically per day as data is computed), send:
  - The month + day-of-month + the day's 3 gradient colors, so the ESP32 can light the correct 3 LEDs for that day within the currently displayed month.
  - The compressed audio file for a given month+day, so the ESP32 can store it on its SD card and play it back via the DFRobot mini player.
- Whenever a month is fully (re)computed, or the ESP32 reports it doesn't have a month cached, push the **full set of up to 31 day-color entries for that month** in one batch so the ESP32 can light the whole board without needing 31 round trips.
- Receive from the ESP32:
  - A "day selected" event when a physical button is pressed → open that day (within the currently active month) in the app.
  - A "month selected" event when the slider moves → switch the app's current month view to match, and push that month's day-color batch (and any audio the ESP32 reports missing) if it isn't already synced.
- Show sync status per day (e.g., a small icon: not sent / sending / synced) and allow manual retry.
- Handle the ESP32 being offline/out of range gracefully — queue and retry, never block core gallery functionality on hardware being present.

### 1.6 Non-functional
- Target modern Android (minSdk realistic for MediaStore + BLE, e.g., API 26+; compileSdk latest).
- Architecture: MVVM or MVI, repository pattern for media/color/audio/device-sync so the same core logic can be reused when porting to iOS (Kotlin Multiplatform is worth considering for the non-UI layers — repositories, color extraction, sync protocol — if you want to minimize iOS duplication later; otherwise plan a parallel Swift implementation against the same protocol spec).
- Local persistence: Room (or SQLDelight if going KMP) for per-item and per-day color cache, audio sync state, and device pairing info.
- No cloud backend required for v1 — everything is on-device + local wireless link to the ESP32.

---

## 2. ESP32 Hardware & Firmware Requirements

### 2.1 Hardware on the ESP32 side
- ESP32 dev board (Wi-Fi + BLE capable).
- WS2812B addressable LED strip/array: 3 LEDs per day × up to 31 days = up to 93 LEDs, addressed individually. At any time these 93 LEDs represent the **currently selected month only**.
- A linear or rotary **slider (potentiometer)** wired to an ADC pin, read to select the active month (map the analog range to 12 discrete positions, with debounce/hysteresis so it doesn't flicker between months at the boundaries). A detented/click potentiometer or a 12-position rotary switch is worth considering over a smooth slider, to avoid needing software snapping.
- 31 physical momentary buttons, one per day-of-month. (ESP32 doesn't have 31 free GPIOs — use GPIO expanders, e.g., 74HC165 shift-register chain or an I/O expander like MCP23017, to multiplex button reads.)
- microSD card module for storing per-day compressed audio files.
- DFRobot mini MP3 player module (UART-controlled) wired to a speaker, for audio playback.
- Power supply sized for 93 WS2812B LEDs at full brightness (~60mA/LED worst case — plan the supply and consider brightness-limiting in firmware).

### 2.2 Firmware responsibilities
- Maintain a wireless link to the phone app (see Section 3).
- Track **current month** (1–12) as firmware state, driven by the slider.
- Persist all 12 months' day-color sets in flash/NVS (12 × 31 × 3 bytes RGB is trivially small) so every month's lights are known locally without needing the phone to resend on every slider move.
- On receiving a "day color" message (single day) or a "month colors" batch message: update NVS for that month/day, and if it's the currently selected month, immediately update the corresponding 3 WS2812B LEDs.
- On slider movement (debounced, snapped to nearest month):
  - If the resolved month changed, re-render all 93 LEDs from the NVS-cached colors for the new month (days without cached data render off/blank).
  - Send a "month selected: M" event to the phone app.
  - If NVS has no data yet for that month, include a flag in the event so the app knows to push a full batch.
- On receiving an audio payload for a given month+day: write/overwrite the compressed audio file on the SD card, named/indexed by month and day-of-month (e.g., `/audio/m08/day_07.opus`).
- On a button press (debounced) for day N, using whatever month is currently selected via the slider:
  - Trigger playback of `/audio/m{current_month}/day_N.*` via the DFRobot mini player over its UART command interface.
  - Send a "day selected: month=M, day=N" event back to the phone app over the same wireless link.
- Keep LED/slider/button/audio state independent of phone connectivity — slider-driven relighting and button playback must work even if the phone is disconnected (SD card is the source of truth for audio; NVS is the source of truth for colors and current month).

---

## 3. Wireless Protocol (Phone ↔ ESP32)

Decide and document explicitly (recommended default below, adjust if you have a strong preference):

- **Recommended: local Wi-Fi**, ESP32 running as a SoftAP or joining the home Wi-Fi network, exposing a small HTTP/WebSocket API (e.g., via `ESPAsyncWebServer`). Reasons: audio file transfer needs more throughput/reliability than BLE comfortably gives; a simple REST-ish API is easy to version and debug; WebSocket (or Server-Sent Events) gives a clean channel for the ESP32 → phone "button pressed" event.
  - `POST /month/{m}/day/{n}/color` — body `{ "colors": ["#RRGGBB", "#RRGGBB", "#RRGGBB"] }`
  - `POST /month/{m}/colors` — batch body `{ "days": [{ "day": 1, "colors": [...] }, ...] }`, used to push/refresh a whole month at once
  - `POST /month/{m}/day/{n}/audio` — multipart/binary upload of the compressed audio file
  - `GET /status` — device health, SD card free space, current selected month, which months are cached in NVS
  - `WS /events` — server pushes `{ "event": "day_selected", "month": m, "day": n }` when a button is pressed, and `{ "event": "month_selected", "month": m, "needs_sync": true|false }` when the slider moves
- **Alternative: BLE** with a custom GATT service (one characteristic for date+color as a small struct, one characteristic for chunked audio transfer, one notify characteristic for button events) — more power-efficient and doesn't require Wi-Fi credentials, but audio transfer will need manual chunking/reassembly and will be slower.
- Either way: define a compact binary or JSON schema for the color+date message, decide max audio file size / duration cap (e.g., 60–90 seconds per day) to keep transfer times and SD usage predictable, and add a simple pairing/discovery flow (e.g., mDNS/Bonjour service advertisement for Wi-Fi, or BLE advertising with a known service UUID) so the app can find the device without manual IP entry.
- Add basic resilience: checksums/acks on audio upload, retry on failed color sync, and a reconnect strategy when the ESP32 restarts.

---

## 4. Suggested Build Order

1. **Android gallery MVP**: read media, group by month/day, basic grid UI — no color/audio/hardware yet.
2. **Color extraction pipeline**: per-item and per-day dominant colors, gradient UI, caching.
3. **Audio journaling**: record, compress, store, play back locally in-app.
4. **ESP32 firmware skeleton**: Wi-Fi (or BLE) connectivity, WS2812B driver, confirm you can light arbitrary LEDs from a hardcoded test payload.
5. **Protocol integration**: implement the phone-side client and ESP32-side server/handlers for color sync (single-day and month-batch); verify a day's gradient reliably lights the correct 3 LEDs.
6. **Slider → month switching**: wire up the potentiometer, tune debounce/snapping to 12 clean positions, confirm moving it re-renders all 93 LEDs from NVS-cached month data and fires `month_selected` to the phone; confirm the app switches its visible month in response.
7. **Audio transfer + SD storage + DFRobot playback**: send a recorded journal entry for a given month+day, confirm it's saved under the right path and plays back correctly via the mini player.
8. **Button matrix + button→playback→app-sync loop**: wire up the 31 buttons, confirm press → local playback for the currently-selected month → event sent to phone → phone opens that month+day.
9. **Polish**: sync status UI, offline queueing, brightness/power tuning for the LED array, error states, behavior when the slider selects a month the phone hasn't synced yet.
10. **iOS port**: reuse the protocol and (if using KMP) shared business logic; rebuild the UI layer and swap `PHPhotoLibrary` in for `MediaStore`.

---

## 5. Open Decisions to Confirm Before/During Build
- Wi-Fi vs BLE as the primary transport (recommendation: Wi-Fi, per Section 3).
- One audio entry per day vs. multiple, and max duration/bitrate cap.
- Whether per-day color sync is automatic (as soon as computed) or user-triggered.
- Cross-platform strategy for iOS: Kotlin Multiplatform shared core vs. fully separate Swift app against the same protocol.
- GPIO expansion approach for 31 buttons (shift register chain vs. I/O expander IC) based on what's on hand.
- Slider hardware: smooth linear potentiometer (needs software snapping to 12 months, more prone to boundary flicker) vs. detented potentiometer/12-position rotary switch (cleaner discrete selection, simpler firmware).
- What happens when the slider selects a month the ESP32 has no cached data for and the phone is unreachable — leave LEDs off for that month, or keep showing the last-known month until new data arrives?
- Whether the ESP32 should proactively request a resync for the current month on boot/reconnect, in case the phone has newer data than what's in NVS.
