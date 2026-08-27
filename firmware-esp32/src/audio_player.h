#pragma once
#include <stdint.h>

// DFRobot DFPlayer Mini over UART2. Plays SD files by folder/file number:
// /NN/DDD.mp3 -> playFolder(month, day). This matches sd_store's layout exactly.

namespace audio_player {

bool begin();
bool ready();

void playDay(uint8_t month, uint8_t day);  // no-op if the file doesn't exist
void stop();

}  // namespace audio_player
