package com.journalgallery.android.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalgallery.shared.audio.AudioPlayer
import com.journalgallery.shared.audio.AudioRecorder
import com.journalgallery.shared.audio.FileBytes
import com.journalgallery.shared.data.AudioStore
import com.journalgallery.shared.data.ColorCache
import com.journalgallery.shared.domain.AudioEntry
import com.journalgallery.shared.domain.AudioSyncState
import com.journalgallery.shared.domain.DayBucket
import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.DayKey
import com.journalgallery.android.di.AudioPaths
import com.journalgallery.shared.media.MediaRepository
import com.journalgallery.shared.util.Crc32
import com.journalgallery.shared.work.BackgroundScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

data class DayUiState(
    val day: DayKey,
    val bucket: DayBucket? = null,
    val colors: DayColors? = null,
    val audio: AudioEntry? = null,
    val recording: Boolean = false,
    val playing: Boolean = false,
)

class DayViewModel(
    private val day: DayKey,
    private val repo: MediaRepository,
    private val colorCache: ColorCache,
    private val audioStore: AudioStore,
    private val recorder: AudioRecorder,
    private val player: AudioPlayer,
    private val scheduler: BackgroundScheduler,
    private val audioPaths: AudioPaths,
) : ViewModel() {

    private val _state = MutableStateFlow(DayUiState(day))
    val state: StateFlow<DayUiState> = _state.asStateFlow()

    private val audioDir: String get() = audioPaths.dir

    init {
        viewModelScope.launch {
            val bucket = repo.month(day.monthKey)?.days?.firstOrNull { it.day == day }
            val colors = withContext(Dispatchers.IO) { colorCache.dayColor(day) }
            val audio = withContext(Dispatchers.IO) { audioStore.entry(day) }
            _state.update { it.copy(bucket = bucket, colors = colors, audio = audio) }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            val path = "$audioDir/${day.iso()}.m4a"
            recorder.start(path)
            _state.update { it.copy(recording = true) }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            val result = recorder.stop()
            _state.update { it.copy(recording = false) }
            if (result != null) {
                val bytes = withContext(Dispatchers.IO) { FileBytes.read(result.path) }
                val entry = AudioEntry(
                    day = day,
                    localPath = result.path,
                    durationMillis = result.durationMillis,
                    sizeBytes = result.sizeBytes,
                    crc32 = withContext(Dispatchers.Default) { Crc32.compute(bytes) },
                    state = AudioSyncState.PENDING,
                    updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                )
                withContext(Dispatchers.IO) { audioStore.put(entry) }
                _state.update { it.copy(audio = entry) }
                scheduler.scheduleSyncDrain()
            }
        }
    }

    fun playAudio() {
        val path = _state.value.audio?.localPath ?: return
        viewModelScope.launch {
            player.play(path)
            _state.update { it.copy(playing = true) }
        }
    }

    fun stopAudio() {
        player.stop()
        _state.update { it.copy(playing = false) }
    }

    fun deleteAudio() {
        viewModelScope.launch {
            _state.value.audio?.let { entry ->
                withContext(Dispatchers.IO) {
                    FileBytes.delete(entry.localPath)
                    audioStore.delete(day)
                }
            }
            _state.update { it.copy(audio = null) }
        }
    }

    override fun onCleared() {
        player.stop()
        if (recorder.isRecording) recorder.cancel()
    }
}
