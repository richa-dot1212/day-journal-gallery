#include "wifi_link.h"

#include <Arduino.h>
#include <ESPmDNS.h>
#include <WiFi.h>

#include "config.h"

namespace {
bool g_apMode = false;
char g_ip[20] = "0.0.0.0";
uint32_t g_lastRetry = 0;
bool g_mdnsUp = false;

void startMdns() {
  if (g_mdnsUp) return;
  if (MDNS.begin(MDNS_HOSTNAME)) {
    MDNS.addService(MDNS_SERVICE_TYPE, "tcp", HTTP_PORT);
    MDNS.addServiceTxt(MDNS_SERVICE_TYPE, "tcp", "fw", FW_VERSION);
    g_mdnsUp = true;
  }
}
}  // namespace

namespace wifi_link {

void begin() {
  const bool haveSta = strlen(WIFI_STA_SSID) > 0;
  if (haveSta) {
    WiFi.mode(WIFI_STA);
    WiFi.setSleep(false);
    WiFi.begin(WIFI_STA_SSID, WIFI_STA_PASS);
    uint32_t start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < 12000) delay(200);
  }

  if (WiFi.status() == WL_CONNECTED) {
    g_apMode = false;
    strncpy(g_ip, WiFi.localIP().toString().c_str(), sizeof(g_ip) - 1);
  } else {
    g_apMode = true;
    WiFi.mode(WIFI_AP);
    WiFi.softAP(WIFI_AP_SSID, WIFI_AP_PASS);
    strncpy(g_ip, WiFi.softAPIP().toString().c_str(), sizeof(g_ip) - 1);
  }
  startMdns();
}

void tick() {
  if (g_apMode) return;
  if (WiFi.status() == WL_CONNECTED) return;
  if (millis() - g_lastRetry < 5000) return;
  g_lastRetry = millis();
  WiFi.reconnect();
  if (WiFi.status() == WL_CONNECTED) {
    strncpy(g_ip, WiFi.localIP().toString().c_str(), sizeof(g_ip) - 1);
    startMdns();
  }
}

bool connected() { return g_apMode || WiFi.status() == WL_CONNECTED; }
const char *ipString() { return g_ip; }

}  // namespace wifi_link
