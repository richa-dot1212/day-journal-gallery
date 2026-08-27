#include "buttons.h"

#include <Arduino.h>

#include "app_state.h"
#include "config.h"

namespace {
constexpr uint8_t TOTAL_BITS = BTN_CHIP_COUNT * 8;  // 32

uint32_t g_stable = 0;        // debounced state, 1 = pressed
uint32_t g_lastRaw = 0;
uint32_t g_lastChangeMs[TOTAL_BITS] = {0};
uint32_t g_lastPoll = 0;

// Buttons wire to GND with pull-ups, so electrical 0 = pressed. We invert on read.
uint32_t readChain() {
  digitalWrite(BTN_PL_PIN, LOW);   // load parallel inputs
  delayMicroseconds(5);
  digitalWrite(BTN_PL_PIN, HIGH);

  uint32_t value = 0;
  for (uint8_t i = 0; i < TOTAL_BITS; ++i) {
    value <<= 1;
    if (digitalRead(BTN_Q7_PIN)) value |= 1;
    digitalWrite(BTN_CP_PIN, HIGH);
    delayMicroseconds(2);
    digitalWrite(BTN_CP_PIN, LOW);
  }
  return ~value;  // invert: 1 = pressed
}
}  // namespace

namespace buttons {

void begin() {
  pinMode(BTN_PL_PIN, OUTPUT);
  pinMode(BTN_CP_PIN, OUTPUT);
  pinMode(BTN_Q7_PIN, INPUT);
  digitalWrite(BTN_PL_PIN, HIGH);
  digitalWrite(BTN_CP_PIN, LOW);
  g_stable = 0;
  g_lastRaw = readChain() & 0x7FFFFFFF;
}

void tick() {
  const uint32_t now = millis();
  if (now - g_lastPoll < BTN_POLL_MS) return;
  g_lastPoll = now;

  uint32_t raw = readChain();

  for (uint8_t i = 0; i < TOTAL_BITS && i < 31; ++i) {
    const uint32_t bit = 1UL << i;
    const bool rawPressed = raw & bit;
    const bool wasPressed = g_stable & bit;

    if (rawPressed != (bool)(g_lastRaw & bit)) {
      g_lastChangeMs[i] = now;
    }
    if ((now - g_lastChangeMs[i]) >= BTN_DEBOUNCE_MS && rawPressed != wasPressed) {
      if (rawPressed) {
        g_stable |= bit;
        app_state::onButtonPressed(i + 1);  // day 1..31
      } else {
        g_stable &= ~bit;
      }
    }
  }
  g_lastRaw = raw;
}

}  // namespace buttons
