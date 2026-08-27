package com.journalgallery.android.ui.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.journalgallery.shared.media.AndroidMediaSource

/**
 * Requests media-read permission with rationale, then renders [content] once granted.
 * Reports the grant state up via [onPermissionChanged] so the ViewModel can start loading.
 */
@Composable
fun PermissionGate(
    onPermissionChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val perms = remember { AndroidMediaSource.requiredPermissions().toTypedArray() }

    fun granted(): Boolean = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var isGranted by remember { mutableStateOf(granted()) }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        isGranted = result.values.any { it } || granted()
        onPermissionChanged(isGranted)
    }

    LaunchedEffect(Unit) { onPermissionChanged(isGranted) }

    if (isGranted) {
        content()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Day Journal groups your photos and videos by day and month, and blends each day's " +
                "colors into a gradient. It needs permission to read your media to do that.",
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { asked = true; launcher.launch(perms) },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(if (asked) "Grant in Settings / Retry" else "Allow media access")
        }
    }
}
