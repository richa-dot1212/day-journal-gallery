package com.journalgallery.shared.orb

import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.DayKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * BLE/GATT identifiers for the "day orb" companion. Shared so a future Wi-Fi transport,
 * the firmware notes, and tests all agree on them.
 */
object OrbGatt {
    const val SERVICE_UUID = "6b1c1500-6a2a-4b1a-9b1e-8f7c2a3d9e10"

    /** Notify: 2 bytes [month, day] pushed when the orb's physical button is pressed. */
    const val DAY_SELECTED_UUID = "6b1c1501-6a2a-4b1a-9b1e-8f7c2a3d9e10"

    /** Write-only: 9 bytes = 3 RGB triples, the day's dominant colors. */
    const val COLOR_WRITE_UUID = "6b1c1502-6a2a-4b1a-9b1e-8f7c2a3d9e10"

    /** Standard Client Characteristic Configuration Descriptor. */
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    /** Serialize [colors] to the 9-byte on-wire form the orb firmware expects. */
    fun encodeColors(colors: DayColors): ByteArray {
        val out = ByteArray(9)
        colors.colors.forEachIndexed { i, c ->
            out[i * 3] = c.r.toByte()
            out[i * 3 + 1] = c.g.toByte()
            out[i * 3 + 2] = c.b.toByte()
        }
        return out
    }
}

/** Stable identity of a single physical orb (BLE MAC on Android). */
data class OrbId(val value: String)

enum class OrbConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

/** Things a transport reports upward. Transport-agnostic on purpose. */
sealed interface OrbEvent {
    /** The orb's button was pressed. Raw bytes — [OrbController] validates + resolves the year. */
    data class ButtonPressed(val orb: OrbId, val month: Int, val day: Int) : OrbEvent

    data class ConnectionChanged(val orb: OrbId, val state: OrbConnectionState) : OrbEvent
}

/**
 * Moves bytes to/from orbs. BLE today; a Wi-Fi implementation can drop in later without the
 * gallery or color code changing. Knows nothing about [DayKey] or the color pipeline.
 */
interface OrbTransport {
    val events: Flow<OrbEvent>

    /** Currently connected orbs (0 or 1 in the proof-of-concept, up to 31 later). */
    val connectedOrbs: StateFlow<Set<OrbId>>

    /** Aggregate state for simple UI. */
    val connectionState: StateFlow<OrbConnectionState>

    /** Begin scanning + connecting; keep reconnecting until [stop]. Idempotent. */
    fun start()

    fun stop()

    /** Push the 9-byte color payload to one orb. Best-effort; returns false if not deliverable. */
    suspend fun pushColors(orb: OrbId, payload: ByteArray): Boolean
}

/**
 * Which [DayKey] each orb represents. One entry in the proof-of-concept (bound lazily to the
 * day being viewed); pre-populate all 31 for the full board later.
 */
class OrbRegistry {
    private val _bindings = MutableStateFlow<Map<OrbId, DayKey>>(emptyMap())
    val bindings: StateFlow<Map<OrbId, DayKey>> = _bindings.asStateFlow()

    fun bind(orb: OrbId, day: DayKey) = _bindings.update { it + (orb to day) }

    fun unbind(orb: OrbId) = _bindings.update { it - orb }

    fun orbFor(day: DayKey): OrbId? = _bindings.value.entries.firstOrNull { it.value == day }?.key

    fun dayFor(orb: OrbId): DayKey? = _bindings.value[orb]
}
