#include "led_controller.h"

#include <FastLED.h>

#include "color_store.h"
#include "config.h"

namespace {
CRGB g_leds[NUM_LEDS];

uint8_t g_pulseDay = 0;       // 1-based; 0 = none
uint32_t g_pulseUntil = 0;

void showDay(uint8_t day, const DayRgb &d) {
  const int base = (day - 1) * LEDS_PER_DAY;
  for (uint8_t i = 0; i < LEDS_PER_DAY; ++i) {
    g_leds[base + i] = d.hasData ? CRGB(d.r[i], d.g[i], d.b[i]) : CRGB::Black;
  }
}
}  // namespace

namespace led_controller {

void begin() {
  FastLED.addLeds<WS2812B, LED_PIN, LED_COLOR_ORDER>(g_leds, NUM_LEDS);
  FastLED.setBrightness(LED_MAX_BRIGHTNESS);
  FastLED.setMaxPowerInVoltsAndMilliamps(5, 2000);  // extra safety clamp
  FastLED.clear(true);
}

void renderMonth(uint8_t month) {
  for (uint8_t day = 1; day <= MAX_DAYS; ++day) {
    DayRgb d;
    color_store::getDay(month, day, d);
    showDay(day, d);
  }
  FastLED.show();
}

void renderDay(uint8_t month, uint8_t day) {
  if (day < 1 || day > MAX_DAYS) return;
  DayRgb d;
  color_store::getDay(month, day, d);
  showDay(day, d);
  FastLED.show();
}

void pulseDay(uint8_t day) {
  if (day < 1 || day > MAX_DAYS) return;
  g_pulseDay = day;
  g_pulseUntil = millis() + 600;
  const int base = (day - 1) * LEDS_PER_DAY;
  for (uint8_t i = 0; i < LEDS_PER_DAY; ++i) g_leds[base + i] = CRGB::White;
  FastLED.show();
}

void debugSetDay(uint8_t day, uint32_t c0, uint32_t c1, uint32_t c2) {
  if (day < 1 || day > MAX_DAYS) return;
  const int base = (day - 1) * LEDS_PER_DAY;
  g_leds[base + 0] = CRGB((c0 >> 16) & 0xFF, (c0 >> 8) & 0xFF, c0 & 0xFF);
  g_leds[base + 1] = CRGB((c1 >> 16) & 0xFF, (c1 >> 8) & 0xFF, c1 & 0xFF);
  g_leds[base + 2] = CRGB((c2 >> 16) & 0xFF, (c2 >> 8) & 0xFF, c2 & 0xFF);
  FastLED.show();
}

void tick() {
  if (g_pulseDay != 0 && (int32_t)(millis() - g_pulseUntil) >= 0) {
    uint8_t day = g_pulseDay;
    g_pulseDay = 0;
    renderDay(color_store::currentMonth(), day);
  }
}

}  // namespace led_controller
