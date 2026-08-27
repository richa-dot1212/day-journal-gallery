package com.journalgallery.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.journalgallery.shared.domain.ColorSyncState

@Composable
fun ColorSyncIndicator(state: ColorSyncState, modifier: Modifier = Modifier) {
    val (icon, tint, desc) = when (state) {
        ColorSyncState.NOT_SENT -> Triple(Icons.Default.CloudQueue, Color(0xFF9E9E9E), "Not sent")
        ColorSyncState.SENDING -> Triple(Icons.Default.CloudUpload, Color(0xFF1976D2), "Sending")
        ColorSyncState.SYNCED -> Triple(Icons.Default.CloudDone, Color(0xFF2E7D32), "Synced")
        ColorSyncState.FAILED -> Triple(Icons.Default.ErrorOutline, Color(0xFFC62828), "Failed")
    }
    Icon(icon, contentDescription = desc, tint = tint, modifier = modifier.size(14.dp))
}

@Composable
fun DeviceReachabilityIcon(reachable: Boolean, modifier: Modifier = Modifier) {
    Icon(
        if (reachable) Icons.Default.CloudDone else Icons.Default.CloudOff,
        contentDescription = if (reachable) "Device online" else "Device offline",
        tint = if (reachable) Color(0xFF2E7D32) else Color(0xFF9E9E9E),
        modifier = modifier.size(18.dp),
    )
}
