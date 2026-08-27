#include "sd_store.h"

#include <SD.h>
#include <SPI.h>

#include "config.h"
#include "crc32.h"

// SD layout (single copy, DFPlayer-compatible):
//   /NN/DDD.mp3   NN = month 01..12 (folder), DDD = day 001..031 (file)
// The phone protocol documents this exact layout (docs/protocol.md); there is no
// separate /audio/mMM/day_NN copy.

namespace {
bool g_ready = false;
File g_writeFile;
Crc32 g_writeCrc;
bool g_writing = false;

SPIClass g_spi(VSPI);

void ensureDir(const String &dir) {
  if (!SD.exists(dir)) SD.mkdir(dir);
}

String monthDir(uint8_t month) {
  char buf[8];
  snprintf(buf, sizeof(buf), "/%02u", month);
  return String(buf);
}
}  // namespace

namespace sd_store {

bool begin() {
  g_spi.begin(SD_SCK_PIN, SD_MISO_PIN, SD_MOSI_PIN, SD_CS_PIN);
  g_ready = SD.begin(SD_CS_PIN, g_spi);
  return g_ready;
}

bool ready() { return g_ready; }

String pathFor(uint8_t month, uint8_t day, const char *ext) {
  char buf[24];
  snprintf(buf, sizeof(buf), "/%02u/%03u.%s", month, day, ext);
  return String(buf);
}

bool beginWrite(uint8_t month, uint8_t day) {
  if (!g_ready || g_writing) return false;
  ensureDir(monthDir(month));
  String path = pathFor(month, day);
  if (SD.exists(path)) SD.remove(path);
  g_writeFile = SD.open(path, FILE_WRITE);
  if (!g_writeFile) return false;
  g_writeCrc = Crc32();
  g_writing = true;
  return true;
}

bool writeChunk(const uint8_t *data, size_t len) {
  if (!g_writing) return false;
  size_t w = g_writeFile.write(data, len);
  g_writeCrc.update(data, len);
  return w == len;
}

uint32_t finishWrite() {
  if (!g_writing) return 0;
  g_writeFile.flush();
  g_writeFile.close();
  g_writing = false;
  return g_writeCrc.finalize();
}

void abortWrite() {
  if (!g_writing) return;
  g_writeFile.close();
  g_writing = false;
}

bool exists(uint8_t month, uint8_t day) {
  return g_ready && SD.exists(pathFor(month, day));
}

uint32_t crc32Of(uint8_t month, uint8_t day) {
  if (!exists(month, day)) return 0;
  File f = SD.open(pathFor(month, day), FILE_READ);
  if (!f) return 0;
  Crc32 crc;
  uint8_t buf[512];
  while (f.available()) {
    int n = f.read(buf, sizeof(buf));
    if (n <= 0) break;
    crc.update(buf, (size_t)n);
  }
  f.close();
  return crc.finalize();
}

uint64_t totalBytes() { return g_ready ? SD.totalBytes() : 0; }
uint64_t freeBytes() { return g_ready ? (SD.totalBytes() - SD.usedBytes()) : 0; }

}  // namespace sd_store
