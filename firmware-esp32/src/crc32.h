#pragma once
#include <stddef.h>
#include <stdint.h>

// Streaming CRC-32 (IEEE, poly 0xEDB88320) matching zlib and the phone app's
// com.journalgallery.shared.util.Crc32.

class Crc32 {
 public:
  Crc32() { reset(); }
  void reset() { crc_ = 0xFFFFFFFFu; }
  void update(const uint8_t *data, size_t len);
  uint32_t finalize() const { return crc_ ^ 0xFFFFFFFFu; }

 private:
  uint32_t crc_;
};

uint32_t crc32_buf(const uint8_t *data, size_t len);
