package com.journalgallery.shared.orb

import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.DayKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single thing app code talks to for orb behaviour. Transport-agnostic; depends only on
 * two small functions so it never reaches into the gallery or the color-extraction pipeline:
 *
 *  - [colorsForDay]  → the existing per-day color cache (`ColorCache::dayColor`)
 *  - [resolveYear]   → maps the orb's bare (month, day) to a full year, using whatever the
 *                      app already knows (most-recent match in the library, else this year)
 *
 * Scales to 31 orbs unchanged: [syncAllBoundDays] pushes every registered binding; the
 * proof-of-concept just uses [syncDay] for the day on screen.
 */
class OrbController(
    private val transport: OrbTransport,
    private val registry: OrbRegistry,
    private val colorsForDay: (DayKey) -> DayColors?,
    private val resolveYear: (month: Int, day: Int) -> Int,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _daySelections = MutableSharedFlow<DayKey>(extraBufferCapacity = 8)

    /** Emits when the orb's button is pressed — a fully resolved day, ready to navigate to. */
    val daySelections: SharedFlow<DayKey> = _daySelections

    val connectionState: StateFlow<OrbConnectionState> get() = transport.connectionState

    val buttonEvents: Flow<OrbEvent.ButtonPressed> =
        transport.events.filterIsInstance<OrbEvent.ButtonPressed>()

    private var started = false

    fun start() {
        if (started) return
        started = true
        transport.start()
        scope.launch {
            buttonEvents.collect { e ->
                val day = resolve(e.month, e.day) ?: return@collect
                registry.bind(e.orb, day)
                _daySelections.emit(day)
                // Pressing the button should also (re)light that orb with the day's colors.
                syncDay(day)
            }
        }
    }

    fun stop() {
        started = false
        transport.stop()
    }

    /**
     * Push [day]'s cached colors to the orb bound to it, or — in the single-orb POC where no
     * explicit binding exists yet — to every connected orb. No-op if colors aren't computed yet.
     */
    suspend fun syncDay(day: DayKey) {
        val colors = withContext(ioDispatcher) { colorsForDay(day) } ?: return
        val payload = OrbGatt.encodeColors(colors)

        val target = registry.orbFor(day)
        if (target != null) {
            transport.pushColors(target, payload)
            return
        }
        for (orb in transport.connectedOrbs.value) {
            transport.pushColors(orb, payload)
            registry.bind(orb, day)
        }
    }

    /** Full-board sync: every registered orb→day binding. Used once 31 orbs are provisioned. */
    suspend fun syncAllBoundDays() {
        for ((orb, day) in registry.bindings.value) {
            val colors = withContext(ioDispatcher) { colorsForDay(day) } ?: continue
            transport.pushColors(orb, OrbGatt.encodeColors(colors))
        }
    }

    private fun resolve(month: Int, day: Int): DayKey? {
        if (month !in 1..12 || day !in 1..31) return null
        val year = resolveYear(month, day)
        return runCatching { DayKey(year, month, day) }.getOrNull()
    }
}
