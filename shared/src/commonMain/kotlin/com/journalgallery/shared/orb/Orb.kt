package com.journalgallery.shared.orb

import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.DayKey
import com.journalgallery.shared.domain.Rgb
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

    /**
     * Write-only. One RGB triple per orb, in orb order (orb 0 = first configured day).
     * Single-orb POC sent 9 bytes (3 colors for 3 LEDs); the calendar sends
     * `orbCount * 3` bytes (one color per orb). The firmware maps triple i onto LED i.
     */
    const val COLOR_WRITE_UUID = "6b1c1502-6a2a-4b1a-9b1e-8f7c2a3d9e10"

    /** Standard Client Characteristic Configuration Descriptor. */
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    /** Legacy single-orb form: 9 bytes = 3 RGB triples of one day's dominant colors. */
    fun encodeColors(colors: DayColors): ByteArray {
        val out = ByteArray(9)
        colors.colors.forEachIndexed { i, c ->
            out[i * 3] = c.r.toByte()
            out[i * 3 + 1] = c.g.toByte()
            out[i * 3 + 2] = c.b.toByte()
        }
        return out
    }

    /** One color per orb (single-LED orbs), `colors.size * 3` bytes. Nulls render off. */
    fun encodeOrbColors(colors: List<Rgb?>): ByteArray {
        val out = ByteArray(colors.size * 3)
        colors.forEachIndexed { i, c ->
            out[i * 3] = (c?.r ?: 0).toByte()
            out[i * 3 + 1] = (c?.g ?: 0).toByte()
            out[i * 3 + 2] = (c?.b ?: 0).toByte()
        }
        return out
    }

    /**
     * Calendar form for 3-LED orbs: each orb gets that day's 3 colors, `days.size * 9` bytes,
     * in orb order. A null day (no colors computed) sends 9 zero bytes = that orb off.
     */
    fun encodeCalendarColors(days: List<DayColors?>): ByteArray {
        val out = ByteArray(days.size * 9)
        days.forEachIndexed { i, dc ->
            val base = i * 9
            dc?.colors?.forEachIndexed { j, c ->
                out[base + j * 3] = c.r.toByte()
                out[base + j * 3 + 1] = c.g.toByte()
                out[base + j * 3 + 2] = c.b.toByte()
            }
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
