package com.journalgallery.shared.di

import com.journalgallery.shared.color.ColorPipeline
import com.journalgallery.shared.data.AudioStore
import com.journalgallery.shared.data.ColorCache
import com.journalgallery.shared.data.PairingStore
import com.journalgallery.shared.data.createDatabase
import com.journalgallery.shared.media.MediaRepository
import com.journalgallery.shared.orb.DayResolution
import com.journalgallery.shared.orb.OrbController
import com.journalgallery.shared.orb.OrbRegistry
import com.journalgallery.shared.sync.DeviceSyncClient
import com.journalgallery.shared.sync.SyncQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform module supplies: DriverFactory, MediaSource, AudioRecorder, AudioPlayer,
 * BackgroundScheduler, DeviceDiscovery, and OrbTransport (BLE on Android).
 */
expect fun platformModule(): Module

val sharedModule: Module = module {
    single { createDatabase(get()) }
    single { ColorCache(get()) }
    single { AudioStore(get()) }
    single { PairingStore(get()) }
    single { MediaRepository(get()) }
    single { DeviceSyncClient() }
    single { SyncQueue(get(), get(), get(), get()) }

    // --- Day-orb companion (BLE now, Wi-Fi later; 1 orb now, up to 31 later) ---
    single { OrbRegistry() }
    single {
        val cache: ColorCache = get()
        val repo: MediaRepository = get()
        OrbController(
            transport = get(),
            registry = get(),
            colorsForDay = { day -> cache.dayColor(day) },
            resolveYear = { m, d -> DayResolution.resolveYear(repo.months.value, m, d) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single {
        ColorPipeline(
            repo = get(),
            source = get(),
            cache = get(),
            computeDispatcher = Dispatchers.Default,
        )
    }
}

fun allSharedModules(): List<Module> = listOf(platformModule(), sharedModule)
