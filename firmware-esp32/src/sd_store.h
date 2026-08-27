#pragma once
#include <Arduino.h>
#include <stdint.h>

// microSD audio storage. Files live at /audio/mMM/day_NN.mp3 (see docs/protocol.md).

namespace sd_store {

bool begin();
bool ready();

// Path builder. `ext` without dot, e.g. "mp3".
String pathFor(uint8_t month, uint8_t day, const char *ext = "mp3");

// Opens a fresh file for writing (truncating). Caller writes chunks then calls finishWrite.
bool beginWrite(uint8_t month, uint8_t day);
bool writeChunk(const uint8_t *data, size_t len);
// Closes the file, computes CRC32 over what was written, returns it (0 on failure).
uint32_t finishWrite();
void abortWrite();

bool exists(uint8_t month, uint8_t day);
uint32_t crc32Of(uint8_t month, uint8_t day);

uint64_t totalBytes();
uint64_t freeBytes();

}  // namespace sd_store
