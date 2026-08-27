#include "web_server.h"

#include <Arduino.h>
#include <ArduinoJson.h>
#include <ESPAsyncWebServer.h>

#include "app_state.h"
#include "audio_player.h"
#include "color_store.h"
#include "config.h"
#include "crc32.h"
#include "led_controller.h"
#include "sd_store.h"
#include "slider.h"

namespace {
AsyncWebServer g_server(HTTP_PORT);
AsyncWebSocket g_ws("/events");

// --- per-upload state (single concurrent upload assumed) -------------------
struct UploadCtx {
  uint8_t month = 0;
  uint8_t day = 0;
  uint32_t expectedCrc = 0;
  uint32_t lastCrc = 0;
  bool open = false;
  bool failed = false;
};
UploadCtx g_upload;

uint32_t parseHexColor(const char *s) {
  if (!s) return 0;
  if (*s == '#') ++s;
  return (uint32_t)strtoul(s, nullptr, 16) & 0xFFFFFF;
}

void sendJson(AsyncWebServerRequest *req, int code, const JsonDocument &doc) {
  auto *res = req->beginResponseStream("application/json");
  res->setCode(code);
  serializeJson(doc, *res);
  req->send(res);
}

void sendAck(AsyncWebServerRequest *req, bool ok, uint32_t crc = 0, const char *msg = nullptr) {
  JsonDocument doc;
  doc["ok"] = ok;
  if (crc) doc["crc32"] = (double)crc;
  if (msg) doc["message"] = msg;
  sendJson(req, ok ? 200 : 400, doc);
}

// --- handlers -----------------------------------------------------------
void handleStatus(AsyncWebServerRequest *req) {
  JsonDocument doc;
  doc["protocol"] = PROTOCOL_VERSION;
  doc["firmware"] = FW_VERSION;
  doc["current_month"] = color_store::currentMonth();
  doc["cached_months_mask"] = color_store::cachedMonthsMask();
  doc["sd_free_bytes"] = (double)sd_store::freeBytes();
  doc["sd_total_bytes"] = (double)sd_store::totalBytes();
  sendJson(req, 200, doc);
}

// POST body handler shared by the two color endpoints.
void handleDayColorBody(AsyncWebServerRequest *req, uint8_t *data, size_t len, size_t index, size_t total) {
  if (index != 0 || len != total) {  // small bodies only; reject fragmented
    sendAck(req, false, 0, "body too large");
    return;
  }
  const int month = req->pathArg(0).toInt();
  const int day = req->pathArg(1).toInt();

  JsonDocument doc;
  if (deserializeJson(doc, data, len)) { sendAck(req, false, 0, "bad json"); return; }
  JsonArray colors = doc["colors"].as<JsonArray>();
  if (colors.isNull() || colors.size() != LEDS_PER_DAY) { sendAck(req, false, 0, "need 3 colors"); return; }

  uint32_t rgb[LEDS_PER_DAY];
  for (int i = 0; i < LEDS_PER_DAY; ++i) rgb[i] = parseHexColor(colors[i]);

  color_store::setDay(month, day, rgb);
  if (month == color_store::currentMonth()) led_controller::renderDay(month, day);
  app_state::markCurrentMonthSynced();
  sendAck(req, true);
}

void handleMonthBatchBody(AsyncWebServerRequest *req, uint8_t *data, size_t len, size_t index, size_t total) {
  if (index != 0 || len != total) { sendAck(req, false, 0, "body too large"); return; }
  const int month = req->pathArg(0).toInt();

  JsonDocument doc;
  if (deserializeJson(doc, data, len)) { sendAck(req, false, 0, "bad json"); return; }
  JsonArray days = doc["days"].as<JsonArray>();
  if (days.isNull()) { sendAck(req, false, 0, "no days"); return; }

  for (JsonObject entry : days) {
    int day = entry["day"] | 0;
    JsonArray colors = entry["colors"].as<JsonArray>();
    if (day < 1 || day > MAX_DAYS || colors.isNull() || colors.size() != LEDS_PER_DAY) continue;
    uint32_t rgb[LEDS_PER_DAY];
    for (int i = 0; i < LEDS_PER_DAY; ++i) rgb[i] = parseHexColor(colors[i]);
    color_store::setDay(month, day, rgb);
  }
  if (month == color_store::currentMonth()) led_controller::renderMonth(month);
  app_state::markCurrentMonthSynced();
  sendAck(req, true);
}

// multipart upload
void handleAudioUpload(AsyncWebServerRequest *req, const String &filename, size_t index,
                       uint8_t *data, size_t len, bool final) {
  if (index == 0) {
    g_upload.month = req->pathArg(0).toInt();
    g_upload.day = req->pathArg(1).toInt();
    g_upload.expectedCrc = req->hasHeader("X-CRC32")
                               ? (uint32_t)strtoul(req->getHeader("X-CRC32")->value().c_str(), nullptr, 10)
                               : 0;
    g_upload.failed = false;
    g_upload.open = sd_store::beginWrite(g_upload.month, g_upload.day);
    if (!g_upload.open) g_upload.failed = true;
  }
  if (g_upload.open && !g_upload.failed) {
    if (!sd_store::writeChunk(data, len)) g_upload.failed = true;
  }
  if (final && g_upload.open) {
    uint32_t crc = sd_store::finishWrite();
    g_upload.open = false;
    g_upload.lastCrc = crc;
  }
}

void handleAudioDone(AsyncWebServerRequest *req) {
  const uint32_t crc = g_upload.lastCrc;
  const bool crcOk = (g_upload.expectedCrc == 0) || (crc == g_upload.expectedCrc);
  if (g_upload.failed || !crcOk) {
    sendAck(req, false, crc, g_upload.failed ? "write failed" : "crc mismatch");
    return;
  }
  sendAck(req, true, crc);
}

void onWsEvent(AsyncWebSocket *server, AsyncWebSocketClient *client, AwsEventType type,
               void *arg, uint8_t *data, size_t len) {
  if (type == WS_EVT_CONNECT) {
    app_state::emitHello();
  }
}
}  // namespace

namespace web_server {

void begin() {
  g_ws.onEvent(onWsEvent);
  g_server.addHandler(&g_ws);

  g_server.on("/status", HTTP_GET, handleStatus);

  g_server.on("^\\/month\\/([0-9]+)\\/day\\/([0-9]+)\\/color$", HTTP_POST,
              [](AsyncWebServerRequest *req) {},
              nullptr, handleDayColorBody);

  g_server.on("^\\/month\\/([0-9]+)\\/colors$", HTTP_POST,
              [](AsyncWebServerRequest *req) {},
              nullptr, handleMonthBatchBody);

  g_server.on("^\\/month\\/([0-9]+)\\/day\\/([0-9]+)\\/audio$", HTTP_POST,
              handleAudioDone, handleAudioUpload);

  g_server.onNotFound([](AsyncWebServerRequest *req) { req->send(404, "text/plain", "not found"); });

  g_server.begin();
}

void tick() {
  g_ws.cleanupClients();
}

void broadcastEvent(const char *json) {
  g_ws.textAll(json);
}

}  // namespace web_server
