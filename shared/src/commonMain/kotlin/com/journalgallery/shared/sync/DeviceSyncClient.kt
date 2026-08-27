package com.journalgallery.shared.sync

import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.DeviceInfo
import com.journalgallery.shared.domain.DeviceStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/** Builds the platform Ktor engine (OkHttp on Android, Darwin on iOS). */
expect fun defaultHttpClient(): HttpClient

private val protocolJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * HTTP + WebSocket client for a single paired ESP32. Stateless w.r.t. sync bookkeeping —
 * [SyncQueue] owns retry/ordering; this just performs one call at a time.
 */
class DeviceSyncClient(
    private val client: HttpClient = buildClient(),
) {
    suspend fun status(device: DeviceInfo): DeviceStatus {
        val r: StatusResponse = client.get("${device.baseUrl}/status").body()
        return DeviceStatus(
            currentMonth = r.currentMonth,
            cachedMonthsMask = r.cachedMonthsMask,
            sdFreeBytes = r.sdFreeBytes,
            sdTotalBytes = r.sdTotalBytes,
            firmwareVersion = r.firmware,
        )
    }

    suspend fun sendDayColor(device: DeviceInfo, month: Int, day: Int, colors: DayColors): AckResponse =
        client.post("${device.baseUrl}/month/$month/day/$day/color") {
            contentType(ContentType.Application.Json)
            setBody(ColorMessage(colors.colors.map { it.hex() }))
        }.body()

    suspend fun sendMonthBatch(device: DeviceInfo, month: Int, days: List<Pair<Int, DayColors>>): AckResponse =
        client.post("${device.baseUrl}/month/$month/colors") {
            contentType(ContentType.Application.Json)
            setBody(MonthColorsBatch(days.map { (d, dc) -> DayColorEntry(d, dc.colors.map { it.hex() }) }))
        }.body()

    /** @param crc32 client-computed; the device verifies and echoes it in the ack. */
    suspend fun uploadAudio(
        device: DeviceInfo,
        month: Int,
        day: Int,
        fileName: String,
        bytes: ByteArray,
        crc32: Long,
    ): AckResponse =
        client.post("${device.baseUrl}/month/$month/day/$day/audio") {
            header("X-CRC32", crc32.toString())
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file", bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/octet-stream")
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }.body()

    /** Cold flow of device events; completes/throws when the socket closes. */
    fun events(device: DeviceInfo): Flow<DeviceEvent> = flow {
        client.webSocket(device.eventsUrl) {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    runCatching { protocolJson.decodeFromString(DeviceEvent.serializer(), frame.readText()) }
                        .getOrNull()?.let { emit(it) }
                }
            }
        }
    }

    companion object {
        fun buildClient(): HttpClient = defaultHttpClient().config {
            install(ContentNegotiation) { json(protocolJson) }
            install(WebSockets)
        }
    }
}
