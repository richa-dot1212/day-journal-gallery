package com.journalgallery.shared.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the phone <-> ESP32 Wi-Fi protocol. Formal spec: docs/protocol.md.
 * Keep this file and the firmware's JSON handling in lock-step.
 */
object Protocol {
    const val MDNS_SERVICE_TYPE = "_journalgallery._tcp"
    const val PROTOCOL_VERSION = 1
    const val MAX_AUDIO_BYTES = 2_500_000L // ~90s at 24kbps MP3 + headroom
}

/** Body of `POST /month/{m}/day/{n}/color`. */
@Serializable
data class ColorMessage(val colors: List<String>) {
    init {
        require(colors.size == 3) { "expected 3 hex colors" }
    }
}

/** One day inside a batch. */
@Serializable
data class DayColorEntry(val day: Int, val colors: List<String>)

/** Body of `POST /month/{m}/colors`. */
@Serializable
data class MonthColorsBatch(val days: List<DayColorEntry>)

/** Response of `GET /status`. */
@Serializable
data class StatusResponse(
    @SerialName("protocol") val protocol: Int,
    @SerialName("firmware") val firmware: String,
    @SerialName("current_month") val currentMonth: Int,
    @SerialName("cached_months_mask") val cachedMonthsMask: Int,
    @SerialName("sd_free_bytes") val sdFreeBytes: Long,
    @SerialName("sd_total_bytes") val sdTotalBytes: Long,
)

/** Ack returned by color POSTs and the audio upload. */
@Serializable
data class AckResponse(
    val ok: Boolean,
    @SerialName("crc32") val crc32: Long? = null,
    val message: String? = null,
)

/** Frames pushed by the device on `WS /events`. Discriminated by [event]. */
@Serializable
data class DeviceEvent(
    val event: String,
    val month: Int,
    val day: Int? = null,
    @SerialName("needs_sync") val needsSync: Boolean = false,
    val seq: Long = 0,
) {
    companion object {
        const val DAY_SELECTED = "day_selected"
        const val MONTH_SELECTED = "month_selected"
        const val HELLO = "hello"
    }
}
