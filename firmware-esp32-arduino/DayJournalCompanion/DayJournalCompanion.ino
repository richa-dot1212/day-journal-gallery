/*
 * Day-Journal ESP32 companion — Arduino IDE build (minimal proof-of-concept).
 *
 * Matches the phone app's Wi-Fi protocol (docs/protocol.md) closely enough to:
 *   - be discovered + paired by the app (mDNS _journalgallery._tcp on port 80)
 *   - answer GET /status
 *   - receive day colors (POST /month/{m}/day/{n}/color and POST /month/{m}/colors)
 *     and show them on a WS2812B strip
 *   - push a "day_selected" event over WS /events when the button is pressed,
 *     which makes the app open that day
 *
 * Left out vs the full firmware/ project (add later): month slider, 74HC165 button
 * matrix, SD card, DFPlayer audio. /status just reports zero SD space.
 *
 * -------------------------------------------------------------------------
 * LIBRARIES (Arduino IDE -> Tools -> Manage Libraries, install these):
 *   - "Adafruit NeoPixel"        by Adafruit
 *   - "ArduinoJson"              by Benoit Blanchon   (v7.x)
 *   - "ESP Async WebServer"      by ESP32Async
 *   - "Async TCP"                by ESP32Async
 * Board: your ESP32 dev module (Tools -> Board -> esp32). ESP32 core provides
 * WiFi, ESPmDNS, Preferences.
 *
 * WIRING (what you have now):
 *   WS2812B strip: 5V -> ESP 5V/VIN, GND -> ESP GND, DIN -> LED_PIN below
 *   Button: one leg -> GND, other leg -> BUTTON_PIN below (uses internal pull-up)
 * -------------------------------------------------------------------------
 */

#include <Adafruit_NeoPixel.h>
#include <ArduinoJson.h>
#include <AsyncTCP.h>
#include <ESPAsyncWebServer.h>
#include <ESPmDNS.h>
#include <Preferences.h>
#include <WiFi.h>

// ====================  EDIT THESE  ====================
// Leave WIFI_SSID as "" to run as a hotspot (phone joins "DayJournal-ESP32").
// Fill both in to instead join your home 2.4 GHz Wi-Fi.
const char* WIFI_SSID = "";
const char* WIFI_PASS = "";

const char* AP_SSID = "DayJournal-ESP32";
const char* AP_PASS = "journal123";          // >= 8 characters

#define LED_PIN      5      // the GPIO your strip's DIN is wired to
#define BUTTON_PIN   27     // your button (other leg to GND)

#define LEDS_PER_DAY 3
#define MAX_DAYS     31
#define NUM_LEDS     (LEDS_PER_DAY * MAX_DAYS)  // 93; harmless if your strip is shorter
#define BRIGHTNESS   40                          // 0-255, keep low unless you have a big PSU

uint8_t currentMonth = 8;   // which month the strip shows; change over serial: "month 9"
// =====================================================

#define FW_VERSION      "0.1.0-arduino"
#define PROTOCOL_VER    1
#define HTTP_PORT       80

Adafruit_NeoPixel strip(NUM_LEDS, LED_PIN, NEO_GRB + NEO_KHZ800);
AsyncWebServer server(HTTP_PORT);
AsyncWebSocket ws("/events");
Preferences prefs;

// Colours live in NVS: key "m01".."m12", each MAX_DAYS*9 bytes (R G B per LED, 3 LEDs/day).
static const size_t DAY_BYTES  = LEDS_PER_DAY * 3;
static const size_t MONTH_BYTES = MAX_DAYS * DAY_BYTES;
uint16_t cachedMonthsMask = 0;

int  selectedDay = 1;       // advances on each button press
uint32_t lastButtonMs = 0;
bool lastButtonDown = false;
uint32_t eventSeq = 0;

// ---------- NVS colour storage ----------
void monthKey(uint8_t m, char* out) { snprintf(out, 6, "m%02u", m); }

void loadMonthBlob(uint8_t m, uint8_t* blob) {
  char k[6]; monthKey(m, k);
  if (prefs.getBytes(k, blob, MONTH_BYTES) != MONTH_BYTES) memset(blob, 0, MONTH_BYTES);
}

void storeDayColor(uint8_t m, uint8_t d, const uint32_t rgb[LEDS_PER_DAY]) {
  if (m < 1 || m > 12 || d < 1 || d > MAX_DAYS) return;
  uint8_t blob[MONTH_BYTES];
  loadMonthBlob(m, blob);
  uint8_t* p = blob + (size_t)(d - 1) * DAY_BYTES;
  for (int i = 0; i < LEDS_PER_DAY; i++) {
    p[i*3+0] = (rgb[i] >> 16) & 0xFF;
    p[i*3+1] = (rgb[i] >> 8) & 0xFF;
    p[i*3+2] =  rgb[i] & 0xFF;
  }
  char k[6]; monthKey(m, k);
  prefs.putBytes(k, blob, MONTH_BYTES);
  cachedMonthsMask |= (1 << (m - 1));
  prefs.putUShort("mask", cachedMonthsMask);
}

// ---------- LED rendering ----------
void renderMonth(uint8_t m) {
  strip.clear();
  uint8_t blob[MONTH_BYTES];
  loadMonthBlob(m, blob);
  for (uint8_t d = 1; d <= MAX_DAYS; d++) {
    uint8_t* p = blob + (size_t)(d - 1) * DAY_BYTES;
    for (int i = 0; i < LEDS_PER_DAY; i++) {
      int idx = (d - 1) * LEDS_PER_DAY + i;
      if (idx < NUM_LEDS) strip.setPixelColor(idx, strip.Color(p[i*3], p[i*3+1], p[i*3+2]));
    }
  }
  strip.show();
}

void pulseDay(uint8_t d) {
  for (int i = 0; i < LEDS_PER_DAY; i++) {
    int idx = (d - 1) * LEDS_PER_DAY + i;
    if (idx >= 0 && idx < NUM_LEDS) strip.setPixelColor(idx, strip.Color(255, 255, 255));
  }
  strip.show();
  delay(120);
  renderMonth(currentMonth);
}

// ---------- protocol helpers ----------
uint32_t parseHexColor(const char* s) {
  if (!s) return 0;
  if (*s == '#') s++;
  return (uint32_t) strtoul(s, nullptr, 16) & 0xFFFFFF;
}

void sendEvent(const char* event, int month, int day) {
  JsonDocument doc;
  doc["event"] = event;
  doc["month"] = month;
  if (day >= 0) doc["day"] = day;
  doc["needs_sync"] = (cachedMonthsMask & (1 << (month - 1))) == 0;
  doc["seq"] = ++eventSeq;
  char buf[176];
  size_t n = serializeJson(doc, buf, sizeof(buf));
  ws.textAll(buf, n);
}

// ---------- HTTP handlers ----------
void handleStatus(AsyncWebServerRequest* req) {
  JsonDocument doc;
  doc["protocol"] = PROTOCOL_VER;
  doc["firmware"] = FW_VERSION;
  doc["current_month"] = currentMonth;
  doc["cached_months_mask"] = cachedMonthsMask;
  doc["sd_free_bytes"] = 0;
  doc["sd_total_bytes"] = 0;
  String out;
  serializeJson(doc, out);
  req->send(200, "application/json", out);
}

// Body accumulator for the dynamic POST routes (one request at a time).
String g_body;

void applyDayColor(int m, int d, JsonArrayConst colors) {
  if (colors.size() != LEDS_PER_DAY) return;
  uint32_t rgb[LEDS_PER_DAY];
  for (int i = 0; i < LEDS_PER_DAY; i++) rgb[i] = parseHexColor(colors[i].as<const char*>());
  storeDayColor(m, d, rgb);
  if (m == currentMonth) renderMonth(currentMonth);
}

void handleDynamicPost(AsyncWebServerRequest* req) {
  String url = req->url();               // e.g. /month/8/day/7/color  or  /month/8/colors
  int m = 0, d = 0;

  if (url.startsWith("/month/")) {
    int p1 = url.indexOf('/', 7);
    m = url.substring(7, p1 < 0 ? url.length() : p1).toInt();

    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, g_body);
    g_body = "";
    if (err) { req->send(400, "application/json", "{\"ok\":false,\"message\":\"bad json\"}"); return; }

    if (url.endsWith("/colors")) {                       // batch
      for (JsonObjectConst e : doc["days"].as<JsonArrayConst>()) {
        applyDayColor(m, e["day"] | 0, e["colors"].as<JsonArrayConst>());
      }
      req->send(200, "application/json", "{\"ok\":true}");
      return;
    }
    if (url.endsWith("/color")) {                        // single day
      int dp = url.indexOf("/day/");
      if (dp >= 0) d = url.substring(dp + 5).toInt();
      applyDayColor(m, d, doc["colors"].as<JsonArrayConst>());
      req->send(200, "application/json", "{\"ok\":true}");
      return;
    }
    if (url.endsWith("/audio")) {                        // no SD in this build
      g_body = "";
      req->send(200, "application/json", "{\"ok\":true,\"message\":\"no sd in arduino build\"}");
      return;
    }
  }
  req->send(404, "application/json", "{\"ok\":false}");
}

void onWsEvent(AsyncWebSocket*, AsyncWebSocketClient* c, AwsEventType type, void*, uint8_t*, size_t) {
  if (type == WS_EVT_CONNECT) sendEvent("hello", currentMonth, -1);
}

// ---------- setup / loop ----------
bool apMode = false;

void startMdns() {
  if (MDNS.begin("dayjournal")) {
    MDNS.addService("journalgallery", "tcp", HTTP_PORT);   // advertises _journalgallery._tcp
    MDNS.addServiceTxt("journalgallery", "tcp", "fw", FW_VERSION);
    Serial.println("mDNS: dayjournal.local  (_journalgallery._tcp)");
  }
}

void startHotspot() {
  apMode = true;
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASS);
  Serial.println("=================================================");
  Serial.printf("HOTSPOT MODE. On your phone, join Wi-Fi:\n  network:  %s\n  password: %s\n", AP_SSID, AP_PASS);
  Serial.print("Then open the app. Device IP: ");
  Serial.println(WiFi.softAPIP());        // usually 192.168.4.1
  Serial.println("=================================================");
  startMdns();
}

void connectWifi() {
  bool haveCreds = strlen(WIFI_SSID) > 0;
  if (haveCreds) {
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASS);
    Serial.print("Joining Wi-Fi");
    uint32_t start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < 20000) { delay(400); Serial.print("."); }
    Serial.println();
    if (WiFi.status() == WL_CONNECTED) {
      apMode = false;
      Serial.print("Connected. IP: "); Serial.println(WiFi.localIP());
      startMdns();
      return;
    }
    Serial.println("Wi-Fi join failed — falling back to hotspot.");
  }
  startHotspot();
}

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\nDay-Journal ESP32 companion " FW_VERSION);

  strip.begin();
  strip.setBrightness(BRIGHTNESS);
  strip.show();

  pinMode(BUTTON_PIN, INPUT_PULLUP);

  prefs.begin("journal", false);
  cachedMonthsMask = prefs.getUShort("mask", 0);

  connectWifi();

  ws.onEvent(onWsEvent);
  server.addHandler(&ws);
  server.on("/status", HTTP_GET, handleStatus);
  server.onRequestBody([](AsyncWebServerRequest*, uint8_t* data, size_t len, size_t index, size_t) {
    if (index == 0) g_body = "";
    for (size_t i = 0; i < len; i++) g_body += (char) data[i];
  });
  server.onNotFound([](AsyncWebServerRequest* req) {
    if (req->method() == HTTP_POST) handleDynamicPost(req);
    else req->send(404, "text/plain", "not found");
  });
  server.begin();
  Serial.println("HTTP + WS server on :80");

  renderMonth(currentMonth);
  Serial.println("Serial: 'month N' to change the shown month, 'day N' to set the button's day.");
}

void pollButton() {
  bool down = digitalRead(BUTTON_PIN) == LOW;         // pressed = LOW (pull-up)
  uint32_t now = millis();
  if (down && !lastButtonDown && now - lastButtonMs > 200) {
    lastButtonMs = now;
    selectedDay = selectedDay >= MAX_DAYS ? 1 : selectedDay + 1;
    Serial.printf("button -> month %d, day %d\n", currentMonth, selectedDay);
    pulseDay(selectedDay);
    sendEvent("day_selected", currentMonth, selectedDay);
  }
  lastButtonDown = down;
}

void pollSerial() {
  static String line;
  while (Serial.available()) {
    char ch = Serial.read();
    if (ch == '\n' || ch == '\r') {
      line.trim();
      if (line.startsWith("month ")) {
        currentMonth = constrain(line.substring(6).toInt(), 1, 12);
        renderMonth(currentMonth);
        sendEvent("month_selected", currentMonth, -1);
        Serial.printf("month -> %d\n", currentMonth);
      } else if (line.startsWith("day ")) {
        selectedDay = constrain(line.substring(4).toInt(), 1, MAX_DAYS);
        Serial.printf("button day -> %d\n", selectedDay);
      }
      line = "";
    } else {
      line += ch;
    }
  }
}

void loop() {
  if (!apMode && WiFi.status() != WL_CONNECTED) { connectWifi(); delay(1000); }
  ws.cleanupClients();
  pollButton();
  pollSerial();
  delay(10);
}
