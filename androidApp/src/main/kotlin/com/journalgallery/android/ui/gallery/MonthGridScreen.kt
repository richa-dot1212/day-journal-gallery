package com.journalgallery.android.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journalgallery.android.ui.components.ColorSyncIndicator
import com.journalgallery.android.ui.permission.PermissionGate
import com.journalgallery.android.ui.toBrush
import com.journalgallery.shared.domain.ColorSyncState
import com.journalgallery.shared.domain.DayColors
import com.journalgallery.shared.domain.DayKey
import com.journalgallery.shared.domain.MonthBucket
import org.koin.androidx.compose.koinViewModel

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthGridScreen(
    onOpenDay: (DayKey) -> Unit,
    onOpenPairing: () -> Unit,
    vm: GalleryViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Day Journal") },
                actions = {
                    IconButton(onClick = onOpenPairing) {
                        Icon(Icons.Default.Memory, contentDescription = "ESP32 device")
                    }
                },
            )
        },
    ) { padding ->
        PermissionGate(onPermissionChanged = vm::onPermissionResult) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                state.sweepProgress?.let { p ->
                    if (p < 1f) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }

                when {
                    state.loading && state.months.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                    state.months.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No photos or videos found.") }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(state.months, key = { "${it.month.year}-${it.month.month}" }) { month ->
                            MonthCalendar(
                                month = month,
                                dayColors = state.dayColors,
                                syncStates = state.colorSync[month.month].orEmpty(),
                                onOpenDay = onOpenDay,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: MonthBucket,
    dayColors: Map<String, DayColors>,
    syncStates: Map<Int, ColorSyncState>,
    onOpenDay: (DayKey) -> Unit,
) {
    val mk = month.month
    val daysWithMedia = remember(month) { month.days.map { it.day.day }.toSet() }

    Column {
        Text(
            "${MONTH_NAMES[mk.month - 1]} ${mk.year}  ·  ${month.itemCount} items",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        )
        (1..mk.lengthInDays).chunked(7).forEach { week ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { dom ->
                    val key = DayKey(mk.year, mk.month, dom)
                    DayCell(
                        modifier = Modifier.weight(1f),
                        dayOfMonth = dom,
                        colors = dayColors[key.iso()],
                        hasMedia = dom in daysWithMedia,
                        syncState = syncStates[dom],
                        onClick = { onOpenDay(key) },
                    )
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier,
    dayOfMonth: Int,
    colors: DayColors?,
    hasMedia: Boolean,
    syncState: ColorSyncState?,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (colors != null) Modifier.background(colors.toBrush())
                else Modifier.background(if (hasMedia) Color(0xFFE0E0E0) else Color(0xFFF5F5F5)),
            )
            .clickable(enabled = hasMedia, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (colors != null) Color.White else Color(0xFF757575),
        )
        if (hasMedia && syncState != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(2.dp)) { ColorSyncIndicator(syncState) }
        }
    }
}
