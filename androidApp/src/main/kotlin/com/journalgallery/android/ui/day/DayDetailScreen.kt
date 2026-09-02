package com.journalgallery.android.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.journalgallery.shared.orb.OrbController
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.journalgallery.android.ui.toBrush
import com.journalgallery.shared.domain.AudioSyncState
import com.journalgallery.shared.domain.DayKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    day: DayKey,
    onBack: () -> Unit,
    vm: DayViewModel = koinViewModel { parametersOf(day) },
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Push this day's dominant colors to the orb whenever they're available/updated.
    val orb = koinInject<OrbController>()
    LaunchedEffect(day, state.colors) {
        if (state.colors != null) orb.syncDay(day)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(day.iso()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            state.colors?.let { colors ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(colors.toBrush()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Day gradient",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            AudioSection(state, vm)

            val items = state.bucket?.items.orEmpty()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioSection(state: DayUiState, vm: DayViewModel) {
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }
    fun requestRecord() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startRecording()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Voice journal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        val audio = state.audio
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.recording) {
                Button(onClick = vm::stopRecording) {
                    Icon(Icons.Default.Stop, null); Text(" Stop")
                }
            } else {
                Button(onClick = ::requestRecord) {
                    Icon(Icons.Default.Mic, null)
                    Text(if (audio == null) " Record" else " Re-record")
                }
            }

            if (audio != null && !state.recording) {
                if (state.playing) {
                    OutlinedButton(onClick = vm::stopAudio) { Icon(Icons.Default.Stop, null); Text(" Stop") }
                } else {
                    OutlinedButton(onClick = vm::playAudio) { Icon(Icons.Default.PlayArrow, null); Text(" Play") }
                }
                IconButton(onClick = vm::deleteAudio) { Icon(Icons.Default.Delete, "Delete recording") }
            }
        }

        if (audio != null) {
            Text(
                "${audio.durationMillis / 1000}s · ${audio.sizeBytes / 1024} KB · ${audio.state.label()}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun AudioSyncState.label(): String = when (this) {
    AudioSyncState.NONE -> "none"
    AudioSyncState.PENDING -> "not sent"
    AudioSyncState.SENDING -> "sending…"
    AudioSyncState.SENT -> "sent, awaiting confirm"
    AudioSyncState.CONFIRMED -> "on device ✓"
    AudioSyncState.FAILED -> "transfer failed"
}
