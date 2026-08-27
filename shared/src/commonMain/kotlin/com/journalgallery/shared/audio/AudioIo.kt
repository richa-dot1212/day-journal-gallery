package com.journalgallery.shared.audio

/**
 * Records a single voice memo to a compressed mono file.
 *
 * Target encoding (build plan Open Item 1): low-bitrate MP3 (~24 kbps mono) so the same file
 * plays on the ESP32's DFPlayer Mini without transcoding. Android provides this via a bundled
 * LAME encoder wrapper; if that proves impractical the actual falls back to AAC/.m4a and the
 * firmware audio path switches to an I2S DAC.
 */
interface AudioRecorder {
    val isRecording: Boolean

    /** @param outputPath absolute path the actual will create/overwrite. */
    suspend fun start(outputPath: String)

    /** @return the finished recording's metadata, or null if nothing was captured. */
    suspend fun stop(): RecordingResult?

    fun cancel()
}

data class RecordingResult(val path: String, val durationMillis: Long, val sizeBytes: Long)

/** Plays back a day's local recording. Independent of the ESP32. */
interface AudioPlayer {
    val isPlaying: Boolean
    suspend fun play(path: String)
    fun pause()
    fun stop()
    /** 0f..1f playback progress, updated while playing. */
    fun progress(): Float
}
