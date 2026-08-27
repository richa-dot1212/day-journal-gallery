#pragma once
#include <stdint.h>

namespace led_controller {

void begin();

// Re-render all NUM_LEDS from color_store for `month` (1..12).
// Days without cached data render off.
void renderMonth(uint8_t month);

// Update just one day's LEDs (used when a color message arrives for the current month).
void renderDay(uint8_t month, uint8_t day);

// Briefly flash a day (button-press / test feedback), then restore.
void pulseDay(uint8_t day);

// Test helper: set an arbitrary day triplet without touching NVS.
void debugSetDay(uint8_t day, uint32_t c0, uint32_t c1, uint32_t c2);

void tick();  // call from loop() for pulse animations

}  // namespace led_controller
