package com.journalgallery.shared.media

import com.journalgallery.shared.domain.MediaItem

/**
 * Platform gallery access. Android backs this with `MediaStore`; iOS with `PHPhotoLibrary`.
 * The repository layer ([MediaRepository]) does all grouping in common code on top of this.
 */
interface MediaSource {

    /** Whether the app currently holds the permission needed to read media. */
    suspend fun hasPermission(): Boolean

    /**
     * All images and videos visible to the app, in no particular order.
     * Videos carry their metadata here; thumbnails are fetched lazily by the UI via [uri].
     */
    suspend fun listAll(): List<MediaItem>

    /**
     * Decode [item] to a small ARGB pixel buffer (roughly [targetEdge] x [targetEdge], aspect
     * preserved) for color extraction. Returns `null` if the item can't be decoded.
     *
     * For videos this decodes the system-generated thumbnail, never a video frame.
     */
    suspend fun loadPixels(item: MediaItem, targetEdge: Int = 64): PixelBuffer?
}

/** Row-major packed ARGB pixels. */
class PixelBuffer(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(argb.size == width * height) { "argb size ${argb.size} != ${width}x$height" }
    }
}
