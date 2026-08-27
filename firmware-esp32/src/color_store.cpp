#include "color_store.h"
#include <Preferences.h>
#include <string.h>

// NVS layout:
//   "m01".."m12"  : blob of MAX_DAYS * 9 bytes  (per day: R0 G0 B0 R1 G1 B1 R2 G2 B2)
//   "present01".."present12" : uint32 bitmask, bit (day-1) set => that day has data
//   "mask"        : uint16, bit (month-1) set => that month has >= 1 day of data
//   "curMonth"    : uint8

namespace {
Preferences prefs;
constexpr size_t DAY_BYTES = LEDS_PER_DAY * 3;                 // 9
constexpr size_t MONTH_BLOB_SIZE = (size_t)MAX_DAYS * DAY_BYTES; // 279

uint16_t g_mask = 0;
uint8_t g_curMonth = 1;

void monthKey(uint8_t month, char *out) { snprintf(out, 12, "m%02u", month); }
void presentKey(uint8_t month, char *out) { snprintf(out, 16, "present%02u", month); }

bool loadMonth(uint8_t month, uint8_t *blob) {
  char key[12];
  monthKey(month, key);
  size_t read = prefs.getBytes(key, blob, MONTH_BLOB_SIZE);
  if (read != MONTH_BLOB_SIZE) {
    memset(blob, 0, MONTH_BLOB_SIZE);
    return false;
  }
  return true;
}
}  // namespace

namespace color_store {

void begin() {
  prefs.begin(NVS_NAMESPACE, false);
  g_mask = prefs.getUShort("mask", 0);
  g_curMonth = prefs.getUChar("curMonth", 1);
  if (g_curMonth < 1 || g_curMonth > 12) g_curMonth = 1;
}

void setDay(uint8_t month, uint8_t day, const uint32_t colors[LEDS_PER_DAY]) {
  if (month < 1 || month > 12 || day < 1 || day > MAX_DAYS) return;

  uint8_t blob[MONTH_BLOB_SIZE];
  loadMonth(month, blob);

  uint8_t *p = blob + (size_t)(day - 1) * DAY_BYTES;
  for (uint8_t i = 0; i < LEDS_PER_DAY; ++i) {
    p[i * 3 + 0] = (colors[i] >> 16) & 0xFF;
    p[i * 3 + 1] = (colors[i] >> 8) & 0xFF;
    p[i * 3 + 2] = colors[i] & 0xFF;
  }

  char key[12];
  monthKey(month, key);
  prefs.putBytes(key, blob, MONTH_BLOB_SIZE);

  char pkey[16];
  presentKey(month, pkey);
  uint32_t present = prefs.getUInt(pkey, 0);
  present |= (1UL << (day - 1));
  prefs.putUInt(pkey, present);

  g_mask |= (uint16_t)(1U << (month - 1));
  prefs.putUShort("mask", g_mask);
}

bool getDay(uint8_t month, uint8_t day, DayRgb &out) {
  memset(&out, 0, sizeof(out));
  if (month < 1 || month > 12 || day < 1 || day > MAX_DAYS) return false;

  char pkey[16];
  presentKey(month, pkey);
  uint32_t present = prefs.getUInt(pkey, 0);
  if (!(present & (1UL << (day - 1)))) return false;

  uint8_t blob[MONTH_BLOB_SIZE];
  if (!loadMonth(month, blob)) return false;

  const uint8_t *p = blob + (size_t)(day - 1) * DAY_BYTES;
  for (uint8_t i = 0; i < LEDS_PER_DAY; ++i) {
    out.r[i] = p[i * 3 + 0];
    out.g[i] = p[i * 3 + 1];
    out.b[i] = p[i * 3 + 2];
  }
  out.hasData = true;
  return true;
}

bool monthHasData(uint8_t month) {
  if (month < 1 || month > 12) return false;
  return (g_mask & (1U << (month - 1))) != 0;
}

uint16_t cachedMonthsMask() { return g_mask; }

void setCurrentMonth(uint8_t month) {
  if (month < 1 || month > 12) return;
  g_curMonth = month;
  prefs.putUChar("curMonth", month);
}

uint8_t currentMonth() { return g_curMonth; }

}  // namespace color_store
