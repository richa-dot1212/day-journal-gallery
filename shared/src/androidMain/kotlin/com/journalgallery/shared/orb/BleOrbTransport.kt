package com.journalgallery.shared.orb

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.ArrayDeque
import java.util.UUID

/**
 * BLE-central implementation of [OrbTransport].
 *
 * Proof-of-concept: connects to the first orb it sees advertising [OrbGatt.SERVICE_UUID].
 * The scan filter already restricts by service UUID, so this scales to many orbs by simply
 * not stopping the scan and connecting each distinct device — [connectSingleOrb] is the only
 * POC shortcut, marked below.
 *
 * All GATT calls are funnelled through [opQueue] on [handler]'s thread because Android's stack
 * allows exactly one outstanding GATT operation at a time.
 */
@SuppressLint("MissingPermission") // guarded by hasBlePermissions() before every entry point
class BleOrbTransport(private val context: Context) : OrbTransport {

    private val serviceUuid = UUID.fromString(OrbGatt.SERVICE_UUID)
    private val daySelectedUuid = UUID.fromString(OrbGatt.DAY_SELECTED_UUID)
    private val colorWriteUuid = UUID.fromString(OrbGatt.COLOR_WRITE_UUID)
    private val cccdUuid = UUID.fromString(OrbGatt.CCCD_UUID)

    private val handler = Handler(Looper.getMainLooper())
    private val btManager get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val scanner: BluetoothLeScanner? get() = btManager?.adapter?.bluetoothLeScanner

    private val _events = MutableSharedFlow<OrbEvent>(extraBufferCapacity = 16)
    override val events: Flow<OrbEvent> = _events

    private val _connectedOrbs = MutableStateFlow<Set<OrbId>>(emptySet())
    override val connectedOrbs: StateFlow<Set<OrbId>> = _connectedOrbs.asStateFlow()

    private val _connectionState = MutableStateFlow(OrbConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<OrbConnectionState> = _connectionState.asStateFlow()

    private var wantRunning = false
    private var scanning = false
    private var gatt: BluetoothGatt? = null
    private var orbId: OrbId? = null

    // --- GATT operation queue -------------------------------------------------
    private val opQueue = ArrayDeque<() -> Unit>()
    private var opInFlight = false
    private val pendingColorPayloads = ArrayDeque<ByteArray>()

    // --- public API ---------------------------------------------------------

    override fun start() {
        wantRunning = true
        handler.post { ensureScanningOrConnected() }
    }

    override fun stop() {
        wantRunning = false
        handler.post { teardown() }
    }

    override suspend fun pushColors(orb: OrbId, payload: ByteArray): Boolean {
        handler.post {
            val g = gatt
            if (g != null && orbId == orb && _connectionState.value == OrbConnectionState.CONNECTED) {
                enqueueColorWrite(g, payload)
            } else {
                // Not ready yet — keep only the latest; it flushes once notifications are live.
                pendingColorPayloads.clear()
                pendingColorPayloads.add(payload)
            }
        }
        return true
    }

    // --- scanning ----------------------------------------------------------

    private fun hasBlePermissions(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun ensureScanningOrConnected() {
        if (!wantRunning) return
        if (gatt != null) return
        if (!hasBlePermissions() || btManager?.adapter?.isEnabled != true) {
            _connectionState.value = OrbConnectionState.DISCONNECTED
            // Retry later; the app calls start() again after granting/enabling, and this is a backstop.
            handler.postDelayed({ ensureScanningOrConnected() }, RETRY_MS)
            return
        }
        startScan()
    }

    private fun startScan() {
        val s = scanner ?: return
        if (scanning) return
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching { s.startScan(filters, settings, scanCallback) }
            .onSuccess {
                scanning = true
                _connectionState.value = OrbConnectionState.SCANNING
            }
    }

    private fun stopScan() {
        if (!scanning) return
        runCatching { scanner?.stopScan(scanCallback) }
        scanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            // POC shortcut: take the first orb and stop scanning. For N orbs, keep scanning
            // and connectGatt() each new address instead.
            stopScan()
            connectSingleOrb(device.address)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _connectionState.value = OrbConnectionState.DISCONNECTED
            handler.postDelayed({ ensureScanningOrConnected() }, RETRY_MS)
        }
    }

    // --- connection ------------------------------------------------------

    private fun connectSingleOrb(address: String) {
        val adapter = btManager?.adapter ?: return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return
        orbId = OrbId(address)
        _connectionState.value = OrbConnectionState.CONNECTING
        gatt = device.connectGatt(context, /* autoConnect = */ false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // Bigger MTU so a full calendar color payload (4 orbs x 9 bytes) is one write.
                    handler.post { if (!g.requestMtu(247)) g.discoverServices() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> handler.post { onDisconnected() }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            handler.post { g.discoverServices() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { onDisconnected() }
                return
            }
            val service = g.getService(serviceUuid)
            val notifyChar = service?.getCharacteristic(daySelectedUuid)
            if (notifyChar == null) {
                handler.post { onDisconnected() }
                return
            }
            handler.post { enqueueEnableNotifications(g, notifyChar) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler.post {
                finishOp()
                if (descriptor.uuid == cccdUuid) {
                    val orb = orbId ?: return@post
                    _connectionState.value = OrbConnectionState.CONNECTED
                    _connectedOrbs.update { it + orb }
                    _events.tryEmit(OrbEvent.ConnectionChanged(orb, OrbConnectionState.CONNECTED))
                    flushPendingColors(g)
                }
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handler.post { finishOp() }
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(characteristic.uuid, value)
        }

        // pre-33
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleNotification(characteristic.uuid, characteristic.value ?: ByteArray(0))
        }
    }

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        if (uuid != daySelectedUuid || value.size < 2) return
        val month = value[0].toInt() and 0xFF
        val day = value[1].toInt() and 0xFF
        val orb = orbId ?: return
        _events.tryEmit(OrbEvent.ButtonPressed(orb, month, day))
    }

    private fun onDisconnected() {
        val orb = orbId
        runCatching { gatt?.close() }
        gatt = null
        opQueue.clear()
        opInFlight = false
        if (orb != null) {
            _connectedOrbs.update { it - orb }
            _events.tryEmit(OrbEvent.ConnectionChanged(orb, OrbConnectionState.DISCONNECTED))
        }
        _connectionState.value = OrbConnectionState.DISCONNECTED
        if (wantRunning) handler.postDelayed({ ensureScanningOrConnected() }, RECONNECT_MS)
    }

    private fun teardown() {
        stopScan()
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        orbId = null
        opQueue.clear()
        opInFlight = false
        _connectedOrbs.value = emptySet()
        _connectionState.value = OrbConnectionState.DISCONNECTED
    }

    // --- GATT op queue -------------------------------------------------

    private fun runNextOp() {
        if (opInFlight) return
        val op = opQueue.poll() ?: return
        opInFlight = true
        op()
    }

    private fun finishOp() {
        opInFlight = false
        runNextOp()
    }

    private fun enqueueEnableNotifications(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        opQueue.add {
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(cccdUuid)
            if (cccd == null) {
                finishOp()
                return@add
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }
        runNextOp()
    }

    private fun enqueueColorWrite(g: BluetoothGatt, payload: ByteArray) {
        opQueue.add {
            val ch = g.getService(serviceUuid)?.getCharacteristic(colorWriteUuid)
            if (ch == null) {
                finishOp()
                return@add
            }
            val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, payload, type)
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = type
                @Suppress("DEPRECATION")
                ch.value = payload
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
        }
        runNextOp()
    }

    private fun flushPendingColors(g: BluetoothGatt) {
        while (true) {
            val next = pendingColorPayloads.poll() ?: break
            enqueueColorWrite(g, next)
        }
    }

    companion object {
        private const val RETRY_MS = 4_000L
        private const val RECONNECT_MS = 2_000L
    }
}
