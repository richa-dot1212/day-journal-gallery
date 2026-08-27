package com.journalgallery.shared.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * NOTE (build plan Open Item 1): Android `MediaRecorder` cannot emit MP3. This actual records
 * mono AAC in an `.m4a` container at ~24 kbps. For the DFPlayer Mini path (M7) either:
 *   (a) transcode to MP3 with a bundled LAME encoder before upload, or
 *   (b) switch the firmware audio module to an I2S DAC that decodes AAC.
 * The rest of the app is codec-agnostic — only the file extension and the M7 decision change.
 */
class AndroidAudioRecorder(private val context: Context) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var currentPath: String? = null
    private var startedAtMs: Long = 0

    override var isRecording: Boolean = false
        private set

    override suspend fun start(outputPath: String) = withContext(Dispatchers.IO) {
        stopInternal(discard = true)
        File(outputPath).parentFile?.mkdirs()
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(16_000)
        r.setAudioEncodingBitRate(24_000)
        r.setMaxDuration(AudioEntryLimits.MAX_DURATION_MILLIS.toInt())
        r.setOutputFile(outputPath)
        r.prepare()
        r.start()
        recorder = r
        currentPath = outputPath
        startedAtMs = System.currentTimeMillis()
        isRecording = true
    }

    override suspend fun stop(): RecordingResult? = withContext(Dispatchers.IO) {
        val path = currentPath ?: return@withContext null
        stopInternal(discard = false)
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return@withContext null
        val duration = runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(path)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            } finally {
                mmr.release()
            }
        }.getOrDefault(System.currentTimeMillis() - startedAtMs)
        RecordingResult(path, duration, file.length())
    }

    override fun cancel() {
        stopInternal(discard = true)
    }

    private fun stopInternal(discard: Boolean) {
        recorder?.let { r ->
            runCatching { r.stop() }
            runCatching { r.release() }
        }
        recorder = null
        isRecording = false
        if (discard) {
            currentPath?.let { File(it).delete() }
            currentPath = null
        }
    }
}

/** Kept out of commonMain's AudioEntry to avoid an import cycle in the actual. */
object AudioEntryLimits {
    const val MAX_DURATION_MILLIS: Long = 90_000
}

class AndroidAudioPlayer : AudioPlayer {
    private var player: MediaPlayer? = null

    override var isPlaying: Boolean = false
        private set

    override suspend fun play(path: String) = withContext(Dispatchers.Main) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(path)
            setOnCompletionListener { this@AndroidAudioPlayer.isPlaying = false }
            prepare()
            start()
        }
        isPlaying = true
    }

    override fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        isPlaying = false
    }

    override fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        isPlaying = false
    }

    override fun progress(): Float {
        val p = player ?: return 0f
        val dur = p.duration.takeIf { it > 0 } ?: return 0f
        return (p.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
    }
}
