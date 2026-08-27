package com.journalgallery.shared.domain

/** A discovered / paired ESP32 companion. */
data class DeviceInfo(
    val serviceName: String,
    val host: String,
    val port: Int,
) {
    val baseUrl: String get() = "http://$host:$port"
    val eventsUrl: String get() = "ws://$host:$port/events"
}

/** Per-day color-sync status shown as a small icon in the grid. */
enum class ColorSyncState { NOT_SENT, SENDING, SYNCED, FAILED }

/** Snapshot of device health from `GET /status`. */
data class DeviceStatus(
    /** 1..12 currently selected by the physical slider. */
    val currentMonth: Int,
    /** Bit i (0-based, month = i+1) set => that month has color data cached in NVS. */
    val cachedMonthsMask: Int,
    val sdFreeBytes: Long,
    val sdTotalBytes: Long,
    val firmwareVersion: String,
) {
    fun isMonthCached(month: Int): Boolean = (cachedMonthsMask shr (month - 1)) and 1 == 1
}
