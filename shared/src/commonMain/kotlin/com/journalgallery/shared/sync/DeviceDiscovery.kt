package com.journalgallery.shared.sync

import com.journalgallery.shared.domain.DeviceInfo
import kotlinx.coroutines.flow.Flow

/**
 * mDNS discovery of ESP32 companions advertising [Protocol.MDNS_SERVICE_TYPE].
 * Android: NsdManager. iOS: NSNetServiceBrowser.
 */
interface DeviceDiscovery {
    /** Emits the current set of resolved devices; updates as devices come and go. */
    fun discover(): Flow<List<DeviceInfo>>
}
