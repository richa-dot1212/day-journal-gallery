#include "slider.h"

#include <Arduino.h>

#include "app_state.h"
#include "config.h"

namespace {
constexpr int ADC_MAX = 4095;
constexpr int BAND = (ADC_MAX + 1) / 12;  // ~341 counts per month

int g_avg = 0;
uint8_t g_month = 1;
uint32_t g_lastPoll = 0;
uint32_t g_stableSince = 0;
uint8_t g_candidate = 1;

uint8_t monthFromAdc(int adc) {
  // Center-of-band mapping with hysteresis: only leave the current band once we're
  // SLIDER_HYSTERESIS counts past its edge.
  int lo = (g_month - 1) * BAND - SLIDER_HYSTERESIS;
  int hi = g_month * BAND + SLIDER_HYSTERESIS;
  if (adc >= lo && adc < hi) return g_month;

  int m = adc / BAND + 1;
  if (m < 1) m = 1;
  if (m > 12) m = 12;
  return (uint8_t)m;
}
}  // namespace

namespace slider {

void begin() {
  analogReadResolution(12);
  analogSetPinAttenuation(SLIDER_ADC_PIN, ADC_11db);
  g_avg = analogRead(SLIDER_ADC_PIN);
  g_month = monthFromAdc(g_avg);
  g_candidate = g_month;
}

void tick() {
  const uint32_t now = millis();
  if (now - g_lastPoll < SLIDER_POLL_MS) return;
  g_lastPoll = now;

  int raw = analogRead(SLIDER_ADC_PIN);
  g_avg += (raw - g_avg) / SLIDER_SAMPLES;

  uint8_t m = monthFromAdc(g_avg);
  if (m != g_candidate) {
    g_candidate = m;
    g_stableSince = now;
    return;
  }
  if (g_candidate != g_month && (now - g_stableSince) >= 120) {
    g_month = g_candidate;
    app_state::onSliderMonthChanged(g_month);
  }
}

uint8_t resolvedMonth() { return g_month; }

}  // namespace slider
