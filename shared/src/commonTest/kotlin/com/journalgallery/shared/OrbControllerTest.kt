package com.journalgallery.shared

import com.journalgallery.shared.domain.DayBucket
import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.DayKey
import com.journalgallery.shared.domain.MonthBucket
import com.journalgallery.shared.domain.MonthKey
import com.journalgallery.shared.domain.Rgb
import com.journalgallery.shared.orb.DayResolution
import com.journalgallery.shared.orb.OrbConnectionState
import com.journalgallery.shared.orb.OrbController
import com.journalgallery.shared.orb.OrbEvent
import com.journalgallery.shared.orb.OrbGatt
import com.journalgallery.shared.orb.OrbId
import com.journalgallery.shared.orb.OrbRegistry
import com.journalgallery.shared.orb.OrbTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeTransport : OrbTransport {
    val emitted = MutableSharedFlow<OrbEvent>(extraBufferCapacity = 8)
    override val events: Flow<OrbEvent> = emitted
    override val connectedOrbs = MutableStateFlow<Set<OrbId>>(setOf(OrbId("AA:BB")))
    override val connectionState = MutableStateFlow(OrbConnectionState.CONNECTED)
    var started = false
    val writes = mutableListOf<Pair<OrbId, ByteArray>>()
    override fun start() { started = true }
    override fun stop() { started = false }
    override suspend fun pushColors(orb: OrbId, payload: ByteArray): Boolean {
        writes += orb to payload; return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OrbControllerTest {

    private fun months() = listOf(
        MonthBucket(MonthKey(2025, 9), listOf(dayBucket(2025, 9, 2))),
        MonthBucket(MonthKey(2026, 9), listOf(dayBucket(2026, 9, 2), dayBucket(2026, 9, 3))),
    )

    private fun dayBucket(y: Int, m: Int, d: Int) = DayBucket(DayKey(y, m, d), emptyList())

    private fun controller(
        transport: OrbTransport,
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
        colorsForDay: (DayKey) -> DayColors? = { null },
    ) = OrbController(
        transport = transport,
        registry = OrbRegistry(),
        colorsForDay = colorsForDay,
        resolveYear = { m, d -> DayResolution.resolveYear(months(), m, d) },
        scope = scope,
        ioDispatcher = dispatcher,
        calendarMonth = 9,
        calendarDayNumbers = listOf(1, 2, 3, 4, 5),
    )

    @Test
    fun resolveYear_picksMostRecentLibraryMatch() {
        assertEquals(2026, DayResolution.resolveYear(months(), 9, 2))
    }

    @Test
    fun resolveYear_fallsBackToCurrentYearWhenNoMatch() {
        assertEquals(2099, DayResolution.resolveYear(months(), 12, 25, now = { 2099 }))
    }

    @Test
    fun encodeOrbColors_isOneTriplePerOrb_nullsAreOff() {
        val payload = OrbGatt.encodeOrbColors(listOf(Rgb(1, 2, 3), null, Rgb(255, 0, 128)))
        assertEquals(9, payload.size)
        assertEquals(listOf(1, 2, 3, 0, 0, 0, 255, 0, 128), payload.map { it.toInt() and 0xFF })
    }

    @Test
    fun buttonPress_resolvesDay_emitsSelection_andSyncsCalendar() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val transport = FakeTransport()
        val sept2 = DayColors(listOf(Rgb(10, 20, 30), Rgb(1, 1, 1), Rgb(2, 2, 2)))
        val c = controller(
            transport, dispatcher,
            CoroutineScope(backgroundScope.coroutineContext + dispatcher),
            colorsForDay = { if (it == DayKey(2026, 9, 2)) sept2 else null },
        )
        val selections = mutableListOf<DayKey>()
        backgroundScope.launch(dispatcher) { c.daySelections.collect { selections += it } }
        c.start()
        assertTrue(transport.started)

        transport.emitted.emit(OrbEvent.ButtonPressed(OrbId("AA:BB"), month = 9, day = 2))

        assertEquals(listOf(DayKey(2026, 9, 2)), selections)

        // one calendar write: 5 orbs * 3 bytes, orb index 1 (Sept 2) = the day's first color
        val (orb, payload) = transport.writes.last()
        assertEquals(OrbId("AA:BB"), orb)
        assertEquals(15, payload.size)
        assertEquals(listOf(10, 20, 30), payload.slice(3..5).map { it.toInt() and 0xFF })
        assertEquals(listOf(0, 0, 0), payload.slice(0..2).map { it.toInt() and 0xFF }) // Sept 1: no colors
    }

    @Test
    fun onConnect_pushesCalendar() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val transport = FakeTransport()
        val c = controller(transport, dispatcher, CoroutineScope(backgroundScope.coroutineContext + dispatcher))
        c.start()

        transport.emitted.emit(OrbEvent.ConnectionChanged(OrbId("AA:BB"), OrbConnectionState.CONNECTED))

        assertEquals(1, transport.writes.size)
        assertEquals(15, transport.writes.first().second.size)
    }

    @Test
    fun buttonPress_withGarbageBytes_isIgnored() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val transport = FakeTransport()
        val c = controller(transport, dispatcher, CoroutineScope(backgroundScope.coroutineContext + dispatcher))
        c.start()

        transport.emitted.emit(OrbEvent.ButtonPressed(OrbId("AA:BB"), month = 99, day = 250))

        assertTrue(transport.writes.isEmpty())
    }
}
