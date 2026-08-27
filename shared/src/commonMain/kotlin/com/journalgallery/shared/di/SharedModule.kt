package com.journalgallery.shared.di

import com.journalgallery.shared.color.ColorPipeline
import com.journalgallery.shared.data.AudioStore
import com.journalgallery.shared.data.ColorCache
import com.journalgallery.shared.data.PairingStore
import com.journalgallery.shared.data.createDatabase
import com.journalgallery.shared.media.MediaRepository
import com.journalgallery.shared.sync.DeviceSyncClient
import com.journalgallery.shared.sync.SyncQueue
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform module supplies: DriverFactory, MediaSource, AudioRecorder, AudioPlayer,
 * BackgroundScheduler, DeviceDiscovery, and the "compute" CoroutineDispatcher (named "compute").
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
