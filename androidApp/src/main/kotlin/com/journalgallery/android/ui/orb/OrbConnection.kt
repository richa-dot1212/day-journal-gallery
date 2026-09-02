package com.journalgallery.android.ui.orb

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journalgallery.shared.domain.DayKey
import com.journalgallery.shared.orb.OrbConnectionState
import com.journalgallery.shared.orb.OrbController
import org.koin.compose.koinInject

/** Runtime BLE permissions, matching the app's other `rememberLauncherForActivityResult` flows. */
private fun blePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/**
 * Drop this once, high in the tree (see [AppNavHost]). It:
 *  - requests BLE permissions on first composition,
 *  - starts/stops the shared [OrbController] with the composition lifecycle,
 *  - forwards orb button presses to [onOrbDaySelected] for navigation.
 *
 * Non-blocking: the gallery renders regardless of BLE state.
 */
@Composable
fun OrbConnectionEffect(onOrbDaySelected: (DayKey) -> Unit) {
    val context = LocalContext.current
    val controller = koinInject<OrbController>()
    val perms = remember { blePermissions() }

    fun granted() = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var hasPerms by remember { mutableStateOf(granted()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasPerms = granted() }

    LaunchedEffect(Unit) {
        if (!hasPerms) launcher.launch(perms)
    }

    DisposableEffect(hasPerms) {
        if (hasPerms) controller.start()
        onDispose { }
    }

    LaunchedEffect(Unit) {
        controller.daySelections.collect { onOrbDaySelected(it) }
    }
}

/** Small connection-state label for a top bar, mirroring the ESP32 chip icon usage. */
@Composable
fun rememberOrbConnectionState(): OrbConnectionState {
    val controller = koinInject<OrbController>()
    val state by controller.connectionState.collectAsStateWithLifecycle()
    return state
}
