// "Day Calendar" POC firmware — 5 orbs, one per day.
//
// Each orb = one WS2812B LED showing that day's single dominant colour, plus a
// button under it. Pressing orb N opens that day's photos in the phone app.
//
// Scale-up later: bump NUM_ORBS, extend the arrays, and read the extra buttons
// through 74HC165 shift registers instead of direct GPIOs.
//
// BLE contract (identical on the Android side — docs/day-orb.md):
//   Service:            6b1c1500-6a2a-4b1a-9b1e-8f7c2a3d9e10
//   Day-event (notify): 6b1c1501-...  -> orb -> app: 2 bytes [month, day] on button press
//   Day-colour (write): 6b1c1502-...  -> app -> orb: NUM_ORBS * 3 bytes = one (R,G,B) per orb,
//                                        in orb order (orb 0 = ORB_DAYS[0], ...)

#include <Adafruit_NeoPixel.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ===================== EDIT THESE =====================
#define NUM_ORBS   5
#define ORB_MONTH  9            // September
const uint8_t ORB_DAYS[NUM_ORBS]   = { 1, 2, 3, 4, 5 };        // which day each orb represents
const uint8_t BUTTON_PINS[NUM_ORBS] = { 27, 26, 25, 33, 32 };  // button N -> GND + this pin

#define LED_PIN     5          // WS2812B DIN (one LED per orb, in orb order)
#define BRIGHTNESS  60         // 0-255
// =====================================================

#define SERVICE_UUID         "6b1c1500-6a2a-4b1a-9b1e-8f7c2a3d9e10"
#define DAY_EVENT_CHAR_UUID   "6b1c1501-6a2a-4b1a-9b1e-8f7c2a3d9e10"
#define DAY_COLOR_CHAR_UUID   "6b1c1502-6a2a-4b1a-9b1e-8f7c2a3d9e10"

Adafruit_NeoPixel orbs(NUM_ORBS, LED_PIN, NEO_GRB + NEO_KHZ800);
BLECharacteristic *dayEventChar;
BLECharacteristic *dayColorChar;
bool deviceConnected = false;

// Placeholder colours until the app syncs the real ones.
uint8_t orbColors[NUM_ORBS][3] = {
  {255,  90,  40},
  {255, 190,  60},
  {230,  60, 120},
  { 80, 160, 255},
  {120, 220, 120},
};

bool lastButton[NUM_ORBS];
uint32_t lastPressMs[NUM_ORBS];

void renderOrbs() {
  for (int i = 0; i < NUM_ORBS; i++) {
    orbs.setPixelColor(i, orbs.Color(orbColors[i][0], orbColors[i][1], orbColors[i][2]));
  }
  orbs.show();
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
    int n = v.length() / 3;
    if (n > NUM_ORBS) n = NUM_ORBS;
    for (int i = 0; i < n; i++) {
      orbColors[i][0] = (uint8_t) v[i * 3 + 0];
      orbColors[i][1] = (uint8_t) v[i * 3 + 1];
      orbColors[i][2] = (uint8_t) v[i * 3 + 2];
    }
    renderOrbs();
    Serial.printf("[color] updated %d orbs from app\n", n);
  }
};

void setup() {
  Serial.begin(115200);
  delay(200);

  orbs.begin();
  orbs.setBrightness(BRIGHTNESS);
  renderOrbs();

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

  Serial.printf("Day-Calendar: %d orbs, month %d, days ", NUM_ORBS, ORB_MONTH);
  for (int i = 0; i < NUM_ORBS; i++) Serial.printf("%d ", ORB_DAYS[i]);
  Serial.println("\nAdvertising as 'DayOrb' - waiting for the app...");
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
      // quick white flash on the pressed orb
      orbs.setPixelColor(i, orbs.Color(255, 255, 255));
      orbs.show();
      delay(80);
      renderOrbs();
    }
    lastButton[i] = state;
  }
  delay(10);
}
