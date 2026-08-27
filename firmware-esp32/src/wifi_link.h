#pragma once

// Brings up Wi-Fi (STA if credentials are set, else SoftAP) and advertises the
// mDNS service the phone app discovers.

namespace wifi_link {

void begin();
void tick();
bool connected();
const char *ipString();

}  // namespace wifi_link
