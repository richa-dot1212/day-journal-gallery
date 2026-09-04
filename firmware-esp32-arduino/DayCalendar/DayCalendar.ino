// "Day Calendar" POC firmware — 4 orbs, one per day.
//
// Each orb = a strip of LEDS_PER_ORB WS2812B LEDs showing that day's dominant
// colours, plus a button under it. Pressing orb N opens that day's photos in the app.
//
// Scale-up later: bump NUM_ORBS, extend the arrays, and read the extra buttons
// through 74HC165 shift registers instead of direct GPIOs.
//
// BLE contract (identical on the Android side — docs/day-orb.md):
//   Service:            6b1c1500-6a2a-4b1a-9b1e-8f7c2a3d9e10
//   Day-event (notify): 6b1c1501-...  -> orb -> app: 2 bytes [month, day] on button press
//   Day-colour (write): 6b1c1502-...  -> app -> orb: NUM_ORBS * LEDS_PER_ORB * 3 bytes,
//                                        one (R,G,B) per LED, orb 0's LEDs first.

#include <Adafruit_NeoPixel.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ===================== EDIT THESE =====================
#define NUM_ORBS      4
#define LEDS_PER_ORB  3            // LEDs in each orb's little strip
#define ORB_MONTH     9            // September
const uint8_t ORB_DAYS[NUM_ORBS]    = { 1, 2, 3, 4 };      // which day each orb represents
const uint8_t BUTTON_PINS[NUM_ORBS] = { 27, 26, 25, 33 };  // button N -> GND + this pin

#define LED_PIN       5           // DIN of the FIRST LED; strips chain DOUT->DIN
#define BRIGHTNESS    60          // 0-255
// =====================================================

#define NUM_LEDS  (NUM_ORBS * LEDS_PER_ORB)   // 12

#define SERVICE_UUID         "6b1c1500-6a2a-4b1a-9b1e-8f7c2a3d9e10"
#define DAY_EVENT_CHAR_UUID   "6b1c1501-6a2a-4b1a-9b1e-8f7c2a3d9e10"
#define DAY_COLOR_CHAR_UUID   "6b1c1502-6a2a-4b1a-9b1e-8f7c2a3d9e10"

Adafruit_NeoPixel leds(NUM_LEDS, LED_PIN, NEO_GRB + NEO_KHZ800);
BLECharacteristic *dayEventChar;
BLECharacteristic *dayColorChar;
bool deviceConnected = false;

// ledColors[globalLedIndex][rgb]. Placeholder pattern until the app syncs real colours.
uint8_t ledColors[NUM_LEDS][3];

bool lastButton[NUM_ORBS];
uint32_t lastPressMs[NUM_ORBS];

void seedPlaceholder() {
  const uint8_t pal[4][3] = {
    {255, 90, 40}, {255, 190, 60}, {230, 60, 120}, {80, 160, 255},
  };
  for (int o = 0; o < NUM_ORBS; o++)
    for (int l = 0; l < LEDS_PER_ORB; l++) {
      int g = o * LEDS_PER_ORB + l;
      ledColors[g][0] = pal[o][0]; ledColors[g][1] = pal[o][1]; ledColors[g][2] = pal[o][2];
    }
}

void render() {
  for (int i = 0; i < NUM_LEDS; i++)
    leds.setPixelColor(i, leds.Color(ledColors[i][0], ledColors[i][1], ledColors[i][2]));
  leds.show();
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *s) override { deviceConnected = true; Serial.println("[ble] app connected"); }
  void onDisconnect(BLEServer *s) override {
    deviceConnected = false;
    Serial.println("[ble] app disconnected, re-advertising");
    s->getAdvertising()->start();
  }
};

class ColorWriteCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) override {
    String v = c->getValue();                 // ESP32 core 3.x: String
    int triples = v.length() / 3;
    if (triples > NUM_LEDS) triples = NUM_LEDS;
    for (int t = 0; t < triples; t++) {
      ledColors[t][0] = (uint8_t) v[t * 3 + 0];
      ledColors[t][1] = (uint8_t) v[t * 3 + 1];
      ledColors[t][2] = (uint8_t) v[t * 3 + 2];
    }
    render();
    Serial.printf("[color] updated %d LEDs from app\n", triples);
  }
};

void setup() {
  Serial.begin(115200);
  delay(200);

  leds.begin();
  leds.setBrightness(BRIGHTNESS);
  seedPlaceholder();
  render();

  for (int i = 0; i < NUM_ORBS; i++) {
    pinMode(BUTTON_PINS[i], INPUT_PULLUP);
    lastButton[i] = HIGH;
  }

  BLEDevice::init("DayOrb");
  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);

  dayEventChar = service->createCharacteristic(DAY_EVENT_CHAR_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  dayEventChar->addDescriptor(new BLE2902());

  dayColorChar = service->createCharacteristic(DAY_COLOR_CHAR_UUID, BLECharacteristic::PROPERTY_WRITE);
  dayColorChar->setCallbacks(new ColorWriteCallback());

  service->start();

  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);
  adv->start();

  Serial.printf("Day-Calendar: %d orbs x %d LEDs = %d LEDs, month %d, days ",
                NUM_ORBS, LEDS_PER_ORB, NUM_LEDS, ORB_MONTH);
  for (int i = 0; i < NUM_ORBS; i++) Serial.printf("%d ", ORB_DAYS[i]);
  Serial.println("\nAdvertising as 'DayOrb' - waiting for the app...");
}

void flashOrb(int orb, uint32_t color) {
  int base = orb * LEDS_PER_ORB;
  for (int l = 0; l < LEDS_PER_ORB; l++) leds.setPixelColor(base + l, color);
  leds.show();
}

void loop() {
  uint32_t now = millis();
  for (int i = 0; i < NUM_ORBS; i++) {
    bool state = digitalRead(BUTTON_PINS[i]);
    if (state == LOW && lastButton[i] == HIGH && now - lastPressMs[i] > 200) {
      lastPressMs[i] = now;
      uint8_t payload[2] = { (uint8_t) ORB_MONTH, ORB_DAYS[i] };
      if (deviceConnected) {
        dayEventChar->setValue(payload, 2);
        dayEventChar->notify();
        Serial.printf("[event] orb %d -> month %d day %d\n", i, ORB_MONTH, ORB_DAYS[i]);
      } else {
        Serial.printf("[event] orb %d pressed but no app connected\n", i);
      }
      flashOrb(i, leds.Color(255, 255, 255));
      delay(80);
      render();
    }
    lastButton[i] = state;
  }
  delay(10);
}
