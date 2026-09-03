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
 * The single thing app code talks to for the physical day-calendar. Transport-agnostic;
 * depends only on two small functions so it never reaches into the gallery or the
 * color-extraction pipeline:
 *
 *  - [colorsForDay]  → the existing per-day color cache (`ColorCache::dayColor`)
 *  - [resolveYear]   → maps a bare (month, day) to a full year, using whatever the app
 *                      already knows (most-recent match in the library, else this year)
 *
 * POC hardware: one ESP32 with 4 orbs (September 1-4), each a single LED + button.
 * [syncCalendar] pushes one color per orb in a single write.
 * Scaling to 31: extend [calendarDayNumbers] and add shift registers on the firmware side —
 * nothing here changes.
 */
class OrbController(
    private val transport: OrbTransport,
    private val registry: OrbRegistry,
    private val colorsForDay: (DayKey) -> DayColors?,
    private val resolveYear: (month: Int, day: Int) -> Int,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Month the physical calendar represents. Must match `ORB_MONTH` in the firmware. */
    val calendarMonth: Int = 9,
    /** Day-of-month each orb represents, in orb order. Must match `ORB_DAYS[]` in the firmware. */
    val calendarDayNumbers: List<Int> = listOf(1, 2, 3, 4),
) {
    private val _daySelections = MutableSharedFlow<DayKey>(extraBufferCapacity = 8)

    /** Emits when an orb button is pressed — a fully resolved day, ready to navigate to. */
    val daySelections: SharedFlow<DayKey> = _daySelections

    val connectionState: StateFlow<OrbConnectionState> get() = transport.connectionState

    private val buttonEvents: Flow<OrbEvent.ButtonPressed> =
        transport.events.filterIsInstance<OrbEvent.ButtonPressed>()

    private val connectionEvents: Flow<OrbEvent.ConnectionChanged> =
        transport.events.filterIsInstance<OrbEvent.ConnectionChanged>()

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
                syncCalendar() // refresh all orb colors on interaction
            }
        }
        scope.launch {
            connectionEvents.collect { e ->
                if (e.state == OrbConnectionState.CONNECTED) syncCalendar()
            }
        }
    }

    fun stop() {
        started = false
        transport.stop()
    }

    /**
     * Push one color per orb (the day's most-prominent color) to the calendar in a single
     * write. Days without computed colors go out as off. Safe to call repeatedly.
     */
    suspend fun syncCalendar() {
        val orbs = transport.connectedOrbs.value
        if (orbs.isEmpty()) return

        val payload = withContext(ioDispatcher) {
            val colors = calendarDayNumbers.map { dom ->
                val year = resolveYear(calendarMonth, dom)
                val key = runCatching { DayKey(year, calendarMonth, dom) }.getOrNull()
                key?.let { colorsForDay(it) }?.colors?.firstOrNull()
            }
            OrbGatt.encodeOrbColors(colors)
        }
        for (orb in orbs) transport.pushColors(orb, payload)
    }

    private fun resolve(month: Int, day: Int): DayKey? {
        if (month !in 1..12 || day !in 1..31) return null
        val year = resolveYear(month, day)
        return runCatching { DayKey(year, month, day) }.getOrNull()
    }
}
