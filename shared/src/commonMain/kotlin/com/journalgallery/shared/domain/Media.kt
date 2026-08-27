package com.journalgallery.shared.domain

/** The kind of a gallery item. Videos are represented by their system thumbnail. */
enum class MediaType { IMAGE, VIDEO }

/**
 * One item in the device gallery, platform-agnostic.
 *
 * @param id stable platform media-store id (Android MediaStore `_ID`, iOS `PHAsset.localIdentifier`).
 * @param uri opaque platform locator the UI hands back to the image loader.
 * @param takenAtEpochMillis capture time (falls back to file modified time when EXIF is absent).
 * @param contentHash cheap change-detector (size + date + orientation); used to invalidate the
 *        color cache without re-hashing full pixels.
 */
data class MediaItem(
    val id: String,
    val uri: String,
    val type: MediaType,
    val takenAtEpochMillis: Long,
    val contentHash: String,
    val widthPx: Int,
    val heightPx: Int,
)

/** All media for a single day, sorted newest-first. */
data class DayBucket(
    val day: DayKey,
    val items: List<MediaItem>,
)

/** All days (that contain media) for a single month, sorted ascending by day. */
data class MonthBucket(
    val month: MonthKey,
    val days: List<DayBucket>,
) {
    val itemCount: Int get() = days.sumOf { it.items.size }
}
