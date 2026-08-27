# Wireless Protocol — Phone ↔ ESP32

Transport: **local Wi-Fi**. The ESP32 runs an HTTP + WebSocket server on port 80.
Discovery: **mDNS**, service type `_journalgallery._tcp`, default hostname `dayjournal.local`.
Protocol version: **1** (`GET /status` → `protocol`).

The ESP32 joins your Wi-Fi if `WIFI_STA_SSID` is set in `firmware-esp32/src/config.h`;
otherwise it hosts a SoftAP (`DayJournal-ESP32` / `journal123`).

---

## REST endpoints

### `GET /status`
```json
{
  "protocol": 1,
  "firmware": "0.1.0",
  "current_month": 8,
  "cached_months_mask": 384,
  "sd_free_bytes": 7900000000,
  "sd_total_bytes": 8000000000
}
```
`cached_months_mask` — bit *i* (0-based) set ⇒ month *i+1* has color data in NVS.

### `POST /month/{m}/day/{n}/color`
`m` = 1..12, `n` = 1..31.
```json
{ "colors": ["#RRGGBB", "#RRGGBB", "#RRGGBB"] }
```
→ `{ "ok": true }`. Updates NVS; if `m` is the slider-selected month, the 3 LEDs for day `n`
update immediately.

### `POST /month/{m}/colors`  (batch — whole month in one call)
```json
{ "days": [ { "day": 1, "colors": ["#..","#..","#.."] }, { "day": 2, "colors": [...] } ] }
```
→ `{ "ok": true }`. Days not listed are left unchanged. Use this whenever a month is fully
(re)computed or `cached_months_mask` shows the device is missing it.

### `POST /month/{m}/day/{n}/audio`  (multipart file upload)
- Part name `file`, `application/octet-stream`, the compressed audio bytes.
- Header `X-CRC32: <decimal>` — CRC-32 (IEEE, poly `0xEDB88320`) of the file.
- Size cap: `MAX_AUDIO_BYTES` (2.5 MB ≈ 90 s @ 24 kbps).

→ `{ "ok": true, "crc32": <decimal> }` when the device's computed CRC matches `X-CRC32`.
→ `{ "ok": false, "message": "crc mismatch" | "write failed" }` otherwise (client retries).

The file is stored at `/NN/DDD.mp3` on the SD card (`NN` = month, `DDD` = day) — the
DFPlayer-Mini folder/file convention. **Note:** DFPlayer plays MP3/WAV only. The Android
app currently records AAC `.m4a` (see build plan Open Item 1); until that path emits MP3 the
firmware audio module needs an AAC-capable I2S DAC instead of the DFPlayer.

---

## WebSocket `WS /events`

Server → client, one JSON object per frame:

```json
{ "event": "hello",          "month": 8, "needs_sync": true,  "seq": 1 }
{ "event": "month_selected",  "month": 3, "needs_sync": false, "seq": 12 }
{ "event": "day_selected",    "month": 3, "day": 7,            "seq": 13 }
```

- `hello` — sent on connect and on boot/reconnect; `needs_sync` hints the phone to push the
  current month in case its data is newer than NVS.
- `month_selected` — the physical slider moved to a new month. The phone switches its visible
  month; if `needs_sync` (or the month isn't in `cached_months_mask`) it pushes the batch.
- `day_selected` — a physical day button was pressed. The phone opens month+day. The ESP32
  has already started local audio playback and pulsed that day's LEDs.

`seq` is a monotonic counter for gap detection; the client may ignore it.

---

## Resilience

- **Color sync:** phone marks each `day_color` row `NOT_SENT → SENDING → SYNCED/FAILED`;
  `SyncQueue.drain()` retries `FAILED`/`NOT_SENT` on every reconnect and from background work.
- **Audio:** `PENDING → SENDING → SENT → CONFIRMED`; CRC mismatch drops back to `SENT` for retry.
- **Device restart:** phone reconnects the WebSocket with backoff; on `hello` it re-runs a drain.
- **Offline:** all endpoints are best-effort; the gallery never blocks on the device.
