package com.journalgallery.shared.data

import com.journalgallery.shared.db.JournalDatabase
import com.journalgallery.shared.domain.DeviceInfo
import kotlinx.datetime.Clock

class PairingStore(db: JournalDatabase) {
    private val q = db.journalQueries

    fun current(): DeviceInfo? = q.selectPairing().executeAsOneOrNull()?.let {
        DeviceInfo(it.service_name, it.host, it.port.toInt())
    }

    fun save(device: DeviceInfo) {
        q.upsertPairing(device.serviceName, device.host, device.port.toLong(), Clock.System.now().toEpochMilliseconds())
    }

    fun clear() = q.clearPairing()
}
