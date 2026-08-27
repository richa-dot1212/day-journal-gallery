package com.journalgallery.shared.di

import com.journalgallery.shared.audio.AudioPlayer
import com.journalgallery.shared.audio.AudioRecorder
import com.journalgallery.shared.audio.RecordingResult
import com.journalgallery.shared.data.DriverFactory
import com.journalgallery.shared.domain.DeviceInfo
import com.journalgallery.shared.domain.MediaItem
import com.journalgallery.shared.media.MediaSource
import com.journalgallery.shared.media.PixelBuffer
import com.journalgallery.shared.sync.DeviceDiscovery
import com.journalgallery.shared.work.BackgroundScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS bindings. Milestone M10 replaces the media/audio/discovery stubs with PHPhotoLibrary,
 * AVAudioRecorder/AVAudioPlayer, and NSNetServiceBrowser implementations.
 */
actual fun platformModule(): Module = module {
    single { DriverFactory() }
    single<MediaSource> { IosMediaSourceStub() }
    single<AudioRecorder> { IosAudioRecorderStub() }
    single<AudioPlayer> { IosAudioPlayerStub() }
    single<BackgroundScheduler> { IosBackgroundSchedulerStub() }
    single<DeviceDiscovery> { IosDeviceDiscoveryStub() }
}

private class IosMediaSourceStub : MediaSource {
    override suspend fun hasPermission() = false
    override suspend fun listAll(): List<MediaItem> = emptyList()
    override suspend fun loadPixels(item: MediaItem, targetEdge: Int): PixelBuffer? = null
}

private class IosAudioRecorderStub : AudioRecorder {
    override val isRecording = false
    override suspend fun start(outputPath: String) = Unit
    override suspend fun stop(): RecordingResult? = null
    override fun cancel() = Unit
}

private class IosAudioPlayerStub : AudioPlayer {
    override val isPlaying = false
    override suspend fun play(path: String) = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun progress() = 0f
}

private class IosBackgroundSchedulerStub : BackgroundScheduler {
    override fun scheduleColorSweep() = Unit
    override fun scheduleSyncDrain() = Unit
}

private class IosDeviceDiscoveryStub : DeviceDiscovery {
    override fun discover(): Flow<List<DeviceInfo>> = flowOf(emptyList())
}
