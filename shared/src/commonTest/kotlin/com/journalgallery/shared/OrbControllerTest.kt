package com.journalgallery.shared

import com.journalgallery.shared.domain.DayBucket
import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.DayKey
import com.journalgallery.shared.domain.MonthBucket
import com.journalgallery.shared.domain.MonthKey
import com.journalgallery.shared.domain.Rgb
import com.journalgallery.shared.orb.DayResolution
import com.journalgallery.shared.orb.OrbConnectionState
import com.journalgallery.shared.orb.OrbEvent
import com.journalgallery.shared.orb.OrbGatt
import com.journalgallery.shared.orb.OrbId
import com.journalgallery.shared.orb.OrbController
import com.journalgallery.shared.orb.OrbRegistry
import com.journalgallery.shared.orb.OrbTransport
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
        MonthBucket(MonthKey(2025, 8), listOf(dayBucket(2025, 8, 7))),
        MonthBucket(MonthKey(2026, 8), listOf(dayBucket(2026, 8, 7))),
        MonthBucket(MonthKey(2026, 3), listOf(dayBucket(2026, 3, 2))),
    )

    private fun dayBucket(y: Int, m: Int, d: Int) = DayBucket(DayKey(y, m, d), emptyList())

    @Test
    fun resolveYear_picksMostRecentLibraryMatch() {
        assertEquals(2026, DayResolution.resolveYear(months(), 8, 7))
        assertEquals(2026, DayResolution.resolveYear(months(), 3, 2))
    }

    @Test
    fun resolveYear_fallsBackToCurrentYearWhenNoMatch() {
        assertEquals(2099, DayResolution.resolveYear(months(), 12, 25, now = { 2099 }))
    }

    @Test
    fun encodeColors_isNineBytesInRgbOrder() {
        val payload = OrbGatt.encodeColors(
            DayColors(listOf(Rgb(1, 2, 3), Rgb(10, 20, 30), Rgb(255, 0, 128))),
        )
        assertEquals(9, payload.size)
        assertEquals(listOf(1, 2, 3, 10, 20, 30, 255, 0, 128), payload.map { it.toInt() and 0xFF })
    }

    @Test
    fun buttonPress_resolvesDay_emitsSelection_andPushesColors() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val transport = FakeTransport()
        val colors = DayColors(listOf(Rgb(9, 9, 9), Rgb(8, 8, 8), Rgb(7, 7, 7)))
        val controller = OrbController(
            transport = transport,
            registry = OrbRegistry(),
            colorsForDay = { colors },
            resolveYear = { m, d -> DayResolution.resolveYear(months(), m, d) },
            scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
            ioDispatcher = dispatcher,
        )
        val selections = mutableListOf<DayKey>()
        backgroundScope.launch(dispatcher) { controller.daySelections.collect { selections += it } }
        controller.start()
        assertTrue(transport.started)

        transport.emitted.emit(OrbEvent.ButtonPressed(OrbId("AA:BB"), month = 8, day = 7))

        assertEquals(listOf(DayKey(2026, 8, 7)), selections)

        // colors for the pressed day were pushed to the connected orb
        val (orb, payload) = transport.writes.first()
        assertEquals(OrbId("AA:BB"), orb)
        assertEquals(OrbGatt.encodeColors(colors).toList(), payload.toList())
    }

    @Test
    fun buttonPress_withGarbageBytes_isIgnored() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val transport = FakeTransport()
        val controller = OrbController(
            transport, OrbRegistry(), colorsForDay = { null },
            resolveYear = { _, _ -> 2026 },
            scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
            ioDispatcher = dispatcher,
        )
        controller.start()
        transport.emitted.emit(OrbEvent.ButtonPressed(OrbId("AA:BB"), month = 99, day = 250))
        // nothing pushed, no crash
        assertTrue(transport.writes.isEmpty())
    }
}
