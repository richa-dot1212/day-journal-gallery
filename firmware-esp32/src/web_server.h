#pragma once
#include <stdint.h>

// ESPAsyncWebServer: REST color/audio endpoints + WS /events.
// Endpoints (see docs/protocol.md):
//   GET  /status
//   POST /month/{m}/day/{n}/color   {"colors":["#RRGGBB",x3]}
//   POST /month/{m}/colors          {"days":[{"day":n,"colors":[...]}...]}
//   POST /month/{m}/day/{n}/audio   multipart file, header X-CRC32
//   WS   /events

namespace web_server {

void begin();
void tick();  // housekeeping: prune dead WS clients

// Push a raw JSON string to all connected WS clients (used as the app_state EventSink).
void broadcastEvent(const char *json);

}  // namespace web_server
