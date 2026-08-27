package com.journalgallery.shared

import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.MediaItem
import com.journalgallery.shared.domain.MediaType
import com.journalgallery.shared.domain.MonthKey
import com.journalgallery.shared.domain.Rgb
import com.journalgallery.shared.media.MediaGrouping
import com.journalgallery.shared.sync.DeviceEvent
import com.journalgallery.shared.sync.MonthColorsBatch
import com.journalgallery.shared.util.Crc32
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolAndUtilTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun rgbHexRoundTrips() {
        val c = Rgb(0x0A, 0xB3, 0xFF)
        assertEquals("#0ab3ff", c.hex())
        assertEquals(c, Rgb.fromHex(c.hex()))
    }

    @Test
    fun monthKeyLeapYear() {
        assertEquals(29, MonthKey(2024, 2).lengthInDays)
        assertEquals(28, MonthKey(2026, 2).lengthInDays)
        assertEquals(31, MonthKey(2026, 8).lengthInDays)
    }

    @Test
    fun crc32MatchesKnownVector() {
        // CRC32 of ASCII "123456789" is 0xCBF43926.
        assertEquals(0xCBF43926L, Crc32.compute("123456789".encodeToByteArray()))
    }

    @Test
    fun deviceEventDeserializes() {
        val e = json.decodeFromString(
            DeviceEvent.serializer(),
            """{"event":"day_selected","month":8,"day":7,"seq":42}""",
        )
        assertEquals(DeviceEvent.DAY_SELECTED, e.event)
        assertEquals(8, e.month)
        assertEquals(7, e.day)
    }

    @Test
    fun monthBatchSerializes() {
        val batch = MonthColorsBatch(
            listOf(
                com.journalgallery.shared.sync.DayColorEntry(1, DayColors(listOf(Rgb(1, 2, 3), Rgb(4, 5, 6), Rgb(7, 8, 9))).colors.map { it.hex() }),
            ),
        )
        val text = json.encodeToString(MonthColorsBatch.serializer(), batch)
        assertTrue(text.contains("\"day\":1"))
    }

    @Test
    fun groupingBucketsByLocalDay() {
        val items = listOf(
            item("a", 1_700_000_000_000),
            item("b", 1_700_000_050_000),
            item("c", 1_800_000_000_000),
        )
        val months = MediaGrouping.groupByMonth(items)
        val total = months.sumOf { m -> m.days.sumOf { it.items.size } }
        assertEquals(3, total)
    }

    private fun item(id: String, ts: Long) =
        MediaItem(id, "uri://$id", MediaType.IMAGE, ts, "h", 100, 100)
}
