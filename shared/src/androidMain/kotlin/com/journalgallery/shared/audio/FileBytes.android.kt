package com.journalgallery.shared.audio

import java.io.File

actual object FileBytes {
    actual fun read(path: String): ByteArray = File(path).readBytes()
    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun delete(path: String) { File(path).delete() }
    actual fun size(path: String): Long = File(path).length()
}
