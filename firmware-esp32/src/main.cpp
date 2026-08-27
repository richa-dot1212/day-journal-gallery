#include <Arduino.h>

#include "app_state.h"
#include "audio_player.h"
#include "buttons.h"
#include "color_store.h"
#include "config.h"
#include "led_controller.h"
#include "sd_store.h"
#include "slider.h"
#include "web_server.h"
#include "wifi_link.h"

static void serialConsole();  // simple test commands over USB serial

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println();
  Serial.println("Day-Journal ESP32 firmware " FW_VERSION);

  color_store::begin();
  app_state::begin();
  led_controller::begin();

  if (!sd_store::begin()) Serial.println("WARN: SD card not detected");
  if (!audio_player::begin()) Serial.println("WARN: DFPlayer not responding");

  slider::begin();
  buttons::begin();

  wifi_link::begin();
  Serial.printf("Wi-Fi %s  IP %s  mDNS %s.local\n",
                wifi_link::connected() ? "up" : "down", wifi_link::ipString(), MDNS_HOSTNAME);

  app_state::setEventSink(web_server::broadcastEvent);
  web_server::begin();

  // Sync firmware's month to the slider's physical position, then light it.
  uint8_t m = slider::resolvedMonth();
  color_store::setCurrentMonth(m);
  led_controller::renderMonth(m);

  // Ask the phone to (re)confirm this month in case it has newer data than NVS.
  app_state::emitMonthSelected(m, app_state::needsSyncForCurrentMonth());

  Serial.println("Ready. Serial test commands: month N | day N | testday N RRGGBB RRGGBB RRGGBB | play M D | status");
}

void loop() {
  slider::tick();
  buttons::tick();
  led_controller::tick();
  wifi_link::tick();
  web_server::tick();
  serialConsole();
}

// ---------------------------------------------------------------------------
static void serialConsole() {
  static char line[96];
  static uint8_t n = 0;

  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (n == 0) continue;
      line[n] = 0;
      n = 0;

      char cmd[16];
      int a = 0, b = 0;
      unsigned int c0 = 0, c1 = 0, c2 = 0;

      if (sscanf(line, "%15s", cmd) != 1) continue;

      if (!strcmp(cmd, "month") && sscanf(line, "%*s %d", &a) == 1) {
        app_state::onSliderMonthChanged((uint8_t)a);
        Serial.printf("current month -> %d\n", color_store::currentMonth());
      } else if (!strcmp(cmd, "day") && sscanf(line, "%*s %d", &a) == 1) {
        app_state::onButtonPressed((uint8_t)a);
      } else if (!strcmp(cmd, "testday") &&
                 sscanf(line, "%*s %d %x %x %x", &a, &c0, &c1, &c2) == 4) {
        led_controller::debugSetDay((uint8_t)a, c0, c1, c2);
        Serial.printf("test day %d lit\n", a);
      } else if (!strcmp(cmd, "play") && sscanf(line, "%*s %d %d", &a, &b) == 2) {
        audio_player::playDay((uint8_t)a, (uint8_t)b);
      } else if (!strcmp(cmd, "status")) {
        Serial.printf("month=%d mask=0x%03X sd=%s free=%llu/%llu\n",
                      color_store::currentMonth(), color_store::cachedMonthsMask(),
                      sd_store::ready() ? "ok" : "none",
                      (unsigned long long)sd_store::freeBytes(),
                      (unsigned long long)sd_store::totalBytes());
      } else {
        Serial.printf("? %s\n", line);
      }
      continue;
    }
    if (n < sizeof(line) - 1) line[n++] = c;
  }
}
