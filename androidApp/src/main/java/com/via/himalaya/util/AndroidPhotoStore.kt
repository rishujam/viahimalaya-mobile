package com.via.himalaya.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.via.himalaya.domain.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks the photos of places users add, and keeps them in app-private storage
 * until they have been sent.
 */
class AndroidPhotoStore(private val context: Context) : PhotoStore {

    private companion object {
        const val TAG = "AndroidPhotoStore"
        const val DIRECTORY = "custom_poi_photos"

        /** Plenty to read a campsite from, and a fraction of a 12 MP original. */
        const val MAX_EDGE_PX = 1600

        /** A shrunk photo should land well under this; the server's ceiling is 2 MB. */
        const val TARGET_BYTES = 1_000_000

        /** Tried in order until the picture fits, so a busy scene gets a lower quality, not a failure. */
        val QUALITY_STEPS = intArrayOf(85, 75, 65, 55)
    }

    override suspend fun prepare(sourceUri: String, id: String): String? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceUri)
        try {
            val decoded = decode(uri) ?: return@withContext null
            val bitmap = fitInside(decoded)
            val bytes = encode(bitmap)
            if (bitmap !== decoded) decoded.recycle()
            bitmap.recycle()
            if (bytes.isEmpty()) return@withContext null

            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            val target = File(directory, "$id.jpg")
            target.writeBytes(bytes)

            discardIfCameraFile(uri)
            target.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not prepare the photo", e)
            null
        } catch (e: OutOfMemoryError) {
            // A very large image on a small phone. Not worth a crash: the user is
            // told it could not be read and can choose another.
            Log.w(TAG, "Out of memory preparing the photo", e)
            null
        }
    }

    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            File(path).takeIf { it.isFile }?.readBytes()
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) {
            runCatching { File(path).delete() }
        }
    }

    /**
     * Decoded through Coil, which is already in the app: it samples while it
     * decodes, so a 50 MP original is never held in memory at full size, and it
     * turns the picture upright according to the EXIF orientation. That second
     * part matters because the re-encode below drops EXIF, and a picture that
     * relied on it would come out sideways.
     */
    private suspend fun decode(uri: Uri): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(MAX_EDGE_PX)
            .scale(Scale.FIT)
            // Bitmap.compress cannot read hardware bitmaps.
            .allowHardware(false)
            // A one-off read; nothing should be kept for it.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        return (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }

    /** Decoding samples in powers of two, so the result can still be up to 2x the limit. */
    private fun fitInside(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE_PX) return bitmap
        val scale = MAX_EDGE_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    /**
     * Writing a Bitmap as a JPEG writes the pixels and nothing else - no EXIF,
     * so no camera model and no GPS position. The server removes any such block
     * again, but the aim is that it never leaves the phone.
     */
    private fun encode(bitmap: Bitmap): ByteArray {
        var bytes = ByteArray(0)
        for (quality in QUALITY_STEPS) {
            val out = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) return ByteArray(0)
            bytes = out.toByteArray()
            if (bytes.size <= TARGET_BYTES) break
        }
        return bytes
    }

    /** The camera's full-size original has done its job once the copy exists. */
    private fun discardIfCameraFile(uri: Uri) {
        if (uri.authority == CameraFiles.authority(context)) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }
}
