package com.journalgallery.shared.di

import com.journalgallery.shared.audio.AndroidAudioPlayer
import com.journalgallery.shared.audio.AndroidAudioRecorder
import com.journalgallery.shared.audio.AudioPlayer
import com.journalgallery.shared.audio.AudioRecorder
import com.journalgallery.shared.data.DriverFactory
import com.journalgallery.shared.media.AndroidMediaSource
import com.journalgallery.shared.media.MediaSource
import com.journalgallery.shared.orb.BleOrbTransport
import com.journalgallery.shared.orb.OrbTransport
import com.journalgallery.shared.sync.AndroidDeviceDiscovery
import com.journalgallery.shared.sync.DeviceDiscovery
import com.journalgallery.shared.work.AndroidBackgroundScheduler
import com.journalgallery.shared.work.BackgroundScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { DriverFactory(androidContext()) }
    single<MediaSource> { AndroidMediaSource(androidContext()) }
    single<AudioRecorder> { AndroidAudioRecorder(androidContext()) }
    single<AudioPlayer> { AndroidAudioPlayer() }
    single<BackgroundScheduler> { AndroidBackgroundScheduler(androidContext()) }
    single<DeviceDiscovery> { AndroidDeviceDiscovery(androidContext()) }
    single<OrbTransport> { BleOrbTransport(androidContext()) }
}
