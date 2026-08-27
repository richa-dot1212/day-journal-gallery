package com.journalgallery.android.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalgallery.shared.color.ColorPipeline
import com.journalgallery.shared.data.ColorCache
import com.journalgallery.shared.domain.ColorSyncState
import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.MonthBucket
import com.journalgallery.shared.domain.MonthKey
import com.journalgallery.shared.media.MediaRepository
import com.journalgallery.shared.work.BackgroundScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryUiState(
    val permissionGranted: Boolean = false,
    val loading: Boolean = false,
    val months: List<MonthBucket> = emptyList(),
    /** dayIso -> aggregated colors, filled progressively by the sweep. */
    val dayColors: Map<String, DayColors> = emptyMap(),
    /** month -> (day -> color sync state). */
    val colorSync: Map<MonthKey, Map<Int, ColorSyncState>> = emptyMap(),
    val sweepProgress: Float? = null,
)

class GalleryViewModel(
    private val repo: MediaRepository,
    private val cache: ColorCache,
    private val pipeline: ColorPipeline,
    private val scheduler: BackgroundScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            pipeline.updates.collect { ready ->
                _state.update { it.copy(dayColors = it.dayColors + (ready.dayIso to ready.colors)) }
            }
        }
        pipeline.onProgress = { p ->
            _state.update { it.copy(sweepProgress = if (p.daysTotal == 0) null else p.daysDone.toFloat() / p.daysTotal) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
        if (granted) load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            repo.refresh()
            val months = repo.months.value
            val existingColors = withContext(Dispatchers.IO) {
                months.flatMap { it.days }.mapNotNull { d ->
                    cache.dayColor(d.day)?.let { d.day.iso() to it }
                }.toMap()
            }
            val sync = withContext(Dispatchers.IO) {
                months.associate { it.month to cache.dayColorSyncStates(it.month) }
            }
            _state.update {
                it.copy(loading = false, months = months, dayColors = existingColors, colorSync = sync)
            }
            scheduler.scheduleColorSweep()
        }
    }

    fun monthColorSync(month: MonthKey): Map<Int, ColorSyncState> = _state.value.colorSync[month].orEmpty()
}
