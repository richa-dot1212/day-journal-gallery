package com.journalgallery.shared.util

/** Standard CRC-32 (IEEE 802.3, polynomial 0xEDB88320) — matches Arduino's CRC32 lib and zlib. */
object Crc32 {
    private val table: IntArray = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
        c
    }

    fun compute(bytes: ByteArray): Long {
        var crc = 0.inv()
        for (b in bytes) {
            crc = table[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        }
        return (crc.inv().toLong()) and 0xFFFFFFFFL
    }
}
