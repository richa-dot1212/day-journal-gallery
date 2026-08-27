#include "app_state.h"

#include <Arduino.h>
#include <ArduinoJson.h>

#include "audio_player.h"
#include "color_store.h"
#include "led_controller.h"

namespace {
app_state::EventSink g_sink = nullptr;
uint64_t g_seq = 0;
bool g_currentMonthSynced = false;

void sendEvent(const char *event, uint8_t month, int day, bool needsSync) {
  if (!g_sink) return;
  JsonDocument doc;
  doc["event"] = event;
  doc["month"] = month;
  if (day >= 0) doc["day"] = day;
  doc["needs_sync"] = needsSync;
  doc["seq"] = (double)app_state::nextSeq();
  char buf[160];
  serializeJson(doc, buf, sizeof(buf));
  g_sink(buf);
}
}  // namespace

namespace app_state {

void begin() {
  g_currentMonthSynced = false;
}

void setEventSink(EventSink sink) { g_sink = sink; }

uint64_t nextSeq() { return ++g_seq; }

bool needsSyncForCurrentMonth() {
  return !g_currentMonthSynced || !color_store::monthHasData(color_store::currentMonth());
}

void markCurrentMonthSynced() { g_currentMonthSynced = true; }

void onSliderMonthChanged(uint8_t newMonth) {
  if (newMonth < 1 || newMonth > 12) return;
  if (newMonth == color_store::currentMonth()) return;

  color_store::setCurrentMonth(newMonth);
  g_currentMonthSynced = color_store::monthHasData(newMonth);
  led_controller::renderMonth(newMonth);
  emitMonthSelected(newMonth, needsSyncForCurrentMonth());
}

void onButtonPressed(uint8_t day) {
  const uint8_t month = color_store::currentMonth();
  led_controller::pulseDay(day);
  audio_player::playDay(month, day);
  emitDaySelected(month, day);
}

void emitMonthSelected(uint8_t month, bool needsSync) {
  sendEvent("month_selected", month, -1, needsSync);
}

void emitDaySelected(uint8_t month, uint8_t day) {
  sendEvent("day_selected", month, day, false);
}

void emitHello() {
  sendEvent("hello", color_store::currentMonth(), -1, needsSyncForCurrentMonth());
}

}  // namespace app_state
