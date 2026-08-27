#pragma once
#include <stdint.h>

// Small shared state + a callback registry so modules stay decoupled from web_server.

namespace app_state {

void begin();

// Called by slider module when the debounced month changes.
void onSliderMonthChanged(uint8_t newMonth);

// Called by buttons module on a debounced press (day 1..31).
void onButtonPressed(uint8_t day);

// True until the phone has acknowledged the current month at least once since boot.
bool needsSyncForCurrentMonth();
void markCurrentMonthSynced();

// Event sink -> web_server pushes these on WS /events. Registered at startup.
typedef void (*EventSink)(const char *json);
void setEventSink(EventSink sink);
void emitMonthSelected(uint8_t month, bool needsSync);
void emitDaySelected(uint8_t month, uint8_t day);
void emitHello();

uint64_t nextSeq();

}  // namespace app_state
