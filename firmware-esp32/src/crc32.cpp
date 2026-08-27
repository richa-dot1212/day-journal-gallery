#include "crc32.h"

namespace {
uint32_t g_table[256];
bool g_init = false;

void ensureTable() {
  if (g_init) return;
  for (uint32_t n = 0; n < 256; ++n) {
    uint32_t c = n;
    for (int k = 0; k < 8; ++k) c = (c & 1) ? (0xEDB88320u ^ (c >> 1)) : (c >> 1);
    g_table[n] = c;
  }
  g_init = true;
}
}  // namespace

void Crc32::update(const uint8_t *data, size_t len) {
  ensureTable();
  uint32_t c = crc_;
  for (size_t i = 0; i < len; ++i) {
    c = g_table[(c ^ data[i]) & 0xFF] ^ (c >> 8);
  }
  crc_ = c;
}

uint32_t crc32_buf(const uint8_t *data, size_t len) {
  Crc32 c;
  c.update(data, len);
  return c.finalize();
}
