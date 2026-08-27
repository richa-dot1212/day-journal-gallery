package com.journalgallery.shared.audio

/** Minimal platform file IO the sync layer needs (read audio for upload, delete on re-record). */
expect object FileBytes {
    fun read(path: String): ByteArray
    fun exists(path: String): Boolean
    fun delete(path: String)
    fun size(path: String): Long
}
