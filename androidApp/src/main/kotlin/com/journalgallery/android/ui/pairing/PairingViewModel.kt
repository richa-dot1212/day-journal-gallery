package com.journalgallery.android.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journalgallery.shared.data.PairingStore
import com.journalgallery.shared.domain.DeviceInfo
import com.journalgallery.shared.domain.DeviceStatus
import com.journalgallery.shared.sync.DeviceDiscovery
import com.journalgallery.shared.sync.DeviceSyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairingUiState(
    val discovered: List<DeviceInfo> = emptyList(),
    val paired: DeviceInfo? = null,
    val status: DeviceStatus? = null,
    val error: String? = null,
)

class PairingViewModel(
    private val discovery: DeviceDiscovery,
    private val pairingStore: PairingStore,
    private val client: DeviceSyncClient,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState(paired = pairingStore.current()))
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    fun startDiscovery() {
        viewModelScope.launch {
            discovery.discover()
                .catch { e -> _state.update { it.copy(error = e.message) } }
                .collect { devices -> _state.update { it.copy(discovered = devices) } }
        }
    }

    fun pair(device: DeviceInfo) {
        pairingStore.save(device)
        _state.update { it.copy(paired = device, error = null) }
        refreshStatus()
    }

    fun unpair() {
        pairingStore.clear()
        _state.update { it.copy(paired = null, status = null) }
    }

    fun refreshStatus() {
        val device = _state.value.paired ?: return
        viewModelScope.launch {
            runCatching { client.status(device) }
                .onSuccess { s -> _state.update { it.copy(status = s, error = null) } }
                .onFailure { e -> _state.update { it.copy(error = "Device unreachable: ${e.message}") } }
        }
    }
}
