package com.journalgallery.shared.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual object FileBytes {
    actual fun read(path: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(path) ?: return ByteArray(0)
        val out = ByteArray(data.length.toInt())
        if (out.isNotEmpty()) {
            out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        }
        return out
    }

    actual fun exists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

    actual fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    actual fun size(path: String): Long {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return 0
        val n = attrs["NSFileSize"] as? platform.Foundation.NSNumber
        return n?.longLongValue ?: 0
    }
}
