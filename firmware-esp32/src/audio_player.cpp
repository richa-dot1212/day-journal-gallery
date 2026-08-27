#include "audio_player.h"

#include <Arduino.h>
#include <DFRobotDFPlayerMini.h>
#include <HardwareSerial.h>

#include "config.h"
#include "sd_store.h"

namespace {
HardwareSerial g_serial(2);
DFRobotDFPlayerMini g_df;
bool g_ready = false;
}  // namespace

namespace audio_player {

bool begin() {
  g_serial.begin(9600, SERIAL_8N1, DFPLAYER_RX_PIN, DFPLAYER_TX_PIN);
  // DFPlayer needs ~1s after power-up before it answers.
  delay(1000);
  g_ready = g_df.begin(g_serial, /*isACK=*/true, /*doReset=*/true);
  if (g_ready) {
    g_df.volume(DFPLAYER_VOLUME);
    g_df.EQ(DFPLAYER_EQ_NORMAL);
  }
  return g_ready;
}

bool ready() { return g_ready; }

void playDay(uint8_t month, uint8_t day) {
  if (!g_ready || month < 1 || month > 12 || day < 1 || day > 31) return;
  if (!sd_store::exists(month, day)) return;
  g_df.playFolder(month, day);  // /NN/DDD.mp3
}

void stop() {
  if (g_ready) g_df.stop();
}

}  // namespace audio_player
