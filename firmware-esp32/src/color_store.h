#pragma once
#include <stdint.h>
#include "config.h"

// Persists all 12 months' day-color sets in NVS so slider moves never need the phone.
// Layout: month m (1..12) has MAX_DAYS entries of 3 bytes RGB. A per-month "present" bit
// records whether the phone has ever pushed data for that month.

struct DayRgb {
  uint8_t r[LEDS_PER_DAY];
  uint8_t g[LEDS_PER_DAY];
  uint8_t b[LEDS_PER_DAY];
  bool hasData;
};

namespace color_store {

void begin();

// month: 1..12, day: 1..31 (1-indexed). colors: LEDS_PER_DAY packed 0xRRGGBB.
void setDay(uint8_t month, uint8_t day, const uint32_t colors[LEDS_PER_DAY]);

// Returns false (and zeroed out) if that day has no cached data.
bool getDay(uint8_t month, uint8_t day, DayRgb &out);

bool monthHasData(uint8_t month);

// Bit i (0-based) set => month (i+1) has at least one day of data.
uint16_t cachedMonthsMask();

// Persist the currently-selected month across reboots.
void setCurrentMonth(uint8_t month);
uint8_t currentMonth();

}  // namespace color_store
