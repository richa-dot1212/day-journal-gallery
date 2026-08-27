package com.journalgallery.shared.media

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import com.journalgallery.shared.domain.MediaItem
import com.journalgallery.shared.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [MediaSource] backed by Android `MediaStore`. Videos are represented by the system
 * thumbnail (`loadThumbnail` / `MediaStore.Video.Thumbnails`), never by decoding frames.
 */
class AndroidMediaSource(private val context: Context) : MediaSource {

    override suspend fun hasPermission(): Boolean {
        val perms = requiredPermissions()
        return perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    override suspend fun listAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        buildList {
            addAll(queryImages())
            addAll(queryVideos())
        }
    }

    private fun queryImages(): List<MediaItem> {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
        )
        val out = ArrayList<MediaItem>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val orientCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(modCol) * 1000)
                val size = c.getLong(sizeCol)
                val orient = c.getInt(orientCol)
                out += MediaItem(
                    id = "img_$id",
                    uri = ContentUris.withAppendedId(uri, id).toString(),
                    type = MediaType.IMAGE,
                    takenAtEpochMillis = taken,
                    contentHash = "$size:$taken:$orient",
                    widthPx = c.getInt(wCol),
                    heightPx = c.getInt(hCol),
                )
            }
        }
        return out
    }

    private fun queryVideos(): List<MediaItem> {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
        )
        val out = ArrayList<MediaItem>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(modCol) * 1000)
                val size = c.getLong(sizeCol)
                out += MediaItem(
                    id = "vid_$id",
                    uri = ContentUris.withAppendedId(uri, id).toString(),
                    type = MediaType.VIDEO,
                    takenAtEpochMillis = taken,
                    contentHash = "$size:$taken",
                    widthPx = c.getInt(wCol),
                    heightPx = c.getInt(hCol),
                )
            }
        }
        return out
    }

    override suspend fun loadPixels(item: MediaItem, targetEdge: Int): PixelBuffer? =
        withContext(Dispatchers.IO) {
            val bmp = runCatching { decodeSmall(Uri.parse(item.uri), item.type, targetEdge) }.getOrNull()
                ?: return@withContext null
            val w = bmp.width
            val h = bmp.height
            val argb = IntArray(w * h)
            bmp.getPixels(argb, 0, w, 0, 0, w, h)
            bmp.recycle()
            PixelBuffer(w, h, argb)
        }

    private fun decodeSmall(uri: Uri, type: MediaType, targetEdge: Int): Bitmap? {
        val resolver = context.contentResolver
        if (type == MediaType.VIDEO || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runCatching { resolver.loadThumbnail(uri, Size(targetEdge, targetEdge), null) }.getOrNull()
        }
        resolver.openInputStream(uri)?.use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            var sample = 1
            while (bounds.outWidth / sample > targetEdge * 2 || bounds.outHeight / sample > targetEdge * 2) sample *= 2
            resolver.openInputStream(uri)?.use { s2 ->
                return BitmapFactory.decodeStream(s2, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
        return null
    }

    companion object {
        fun requiredPermissions(): List<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO,
                )
            } else {
                listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
    }
}
