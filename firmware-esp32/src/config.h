#pragma once
#include <stdint.h>

// ---------------------------------------------------------------------------
// Compile-time configuration. Adjust pins to your wiring (see docs/esp32-wiring.md).
// ---------------------------------------------------------------------------

#define FW_VERSION            "0.1.0"
#define PROTOCOL_VERSION      1

// ---- Wi-Fi ---------------------------------------------------------------
// If STA credentials are blank the device stays in SoftAP mode.
#define WIFI_STA_SSID         ""
#define WIFI_STA_PASS         ""
#define WIFI_AP_SSID          "DayJournal-ESP32"
#define WIFI_AP_PASS          "journal123"      // >= 8 chars
#define MDNS_HOSTNAME         "dayjournal"       // -> dayjournal.local
#define MDNS_SERVICE_TYPE     "_journalgallery"  // advertised as _journalgallery._tcp
#define HTTP_PORT             80

// ---- WS2812B LEDs -------------------------------------------------------
#define LED_PIN               13
#define LEDS_PER_DAY          3
#define MAX_DAYS              31
#define NUM_LEDS              (LEDS_PER_DAY * MAX_DAYS)   // 93
#define LED_MAX_BRIGHTNESS    64      // 0-255; power-budget cap for 93 LEDs
#define LED_COLOR_ORDER       GRB

// ---- Month slider (potentiometer on ADC) ------------------------------
#define SLIDER_ADC_PIN        34      // input-only ADC1 pin
#define SLIDER_SAMPLES        16      // rolling average window
#define SLIDER_HYSTERESIS     40      // ADC counts of dead-band at each boundary
#define SLIDER_POLL_MS        50

// ---- 74HC165 button shift-register chain -----------------------------
#define BTN_PL_PIN            25      // parallel load (active low)
#define BTN_CP_PIN            26      // clock
#define BTN_Q7_PIN            27      // serial data in
#define BTN_CHIP_COUNT        4       // 4 * 8 = 32 inputs, 31 used
#define BTN_DEBOUNCE_MS       30
#define BTN_POLL_MS           15

// ---- microSD (SPI) ---------------------------------------------------
#define SD_CS_PIN             5
#define SD_SCK_PIN            18
#define SD_MISO_PIN           19
#define SD_MOSI_PIN           23
#define AUDIO_DIR             "/audio"
#define MAX_AUDIO_BYTES       2500000UL

// ---- DFPlayer Mini (UART2) -----------------------------------------
#define DFPLAYER_RX_PIN       16      // ESP32 RX  <- DFPlayer TX
#define DFPLAYER_TX_PIN       17      // ESP32 TX  -> DFPlayer RX
#define DFPLAYER_VOLUME       22      // 0-30

// ---- NVS ----------------------------------------------------------
#define NVS_NAMESPACE         "journal"
