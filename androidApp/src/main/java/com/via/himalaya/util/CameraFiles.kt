package com.via.himalaya.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where the phone's camera app writes a photo for us.
 *
 * Only cache/camera is shared (see res/xml/file_paths.xml), and a file there
 * lives only until the picture has been shrunk on Save - AndroidPhotoStore
 * deletes it then. This is the same folder it recognises its own files by.
 */
object CameraFiles {

    private const val DIRECTORY = "camera"

    /**
     * Anything older than this was left behind by a retake or an abandoned form.
     * A camera original is several megabytes, so it is worth clearing. Nothing
     * newer is touched: the previous photo may still be on screen.
     */
    private const val STALE_AFTER_MS = 60 * 60 * 1000L

    /** Must equal android:authorities on the FileProvider in the manifest. */
    fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** A fresh, empty file for the camera to write into, as a Uri it can be given. */
    fun newPhotoUri(context: Context): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val now = System.currentTimeMillis()
        directory.listFiles()
            ?.filter { now - it.lastModified() > STALE_AFTER_MS }
            ?.forEach { it.delete() }
        return FileProvider.getUriForFile(context, authority(context), File(directory, "capture_$now.jpg"))
    }
}
