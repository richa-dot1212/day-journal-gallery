package com.journalgallery.android.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onBack: () -> Unit,
    vm: PairingViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.startDiscovery()
        vm.refreshStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ESP32 Companion") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.paired?.let { device ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Paired: ${device.serviceName}", fontWeight = FontWeight.SemiBold)
                        Text("${device.host}:${device.port}", style = MaterialTheme.typography.bodySmall)
                        state.status?.let { s ->
                            Text("Firmware ${s.firmwareVersion} · slider month ${s.currentMonth}")
                            Text(
                                "SD free ${s.sdFreeBytes / 1_000_000} / ${s.sdTotalBytes / 1_000_000} MB",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = vm::refreshStatus) { Text("Refresh") }
                            OutlinedButton(onClick = vm::unpair) { Text("Unpair") }
                        }
                    }
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Text("Discovered devices", fontWeight = FontWeight.SemiBold)
            if (state.discovered.isEmpty()) {
                Text("Searching the local network…", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.discovered, key = { it.serviceName }) { device ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(device.serviceName, fontWeight = FontWeight.Medium)
                            Text("${device.host}:${device.port}", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { vm.pair(device) }, modifier = Modifier.padding(top = 6.dp)) {
                                Text("Pair")
                            }
                        }
                    }
                }
            }
        }
    }
}
