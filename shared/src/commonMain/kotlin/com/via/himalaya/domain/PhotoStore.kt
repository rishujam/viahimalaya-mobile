package com.via.himalaya.domain

/**
 * Where the photos of places the user added live on the phone.
 *
 * An interface because the work is platform work - decoding, shrinking and
 * writing an image - and because the repository that uploads them should not
 * know where they are kept. Android is the only implementation, like
 * [LocationEmitter].
 */
interface PhotoStore {

    /**
     * Shrinks the picture at [sourceUri] and writes it to private storage under
     * [id], returning the path of the copy - or null if it could not be read or
     * shrunk.
     *
     * Always re-encodes, never copies the original: the original carries the
     * camera's EXIF block, and with it the position the photo was taken at.
     */
    suspend fun prepare(sourceUri: String, id: String): String?

    /** The bytes of a file returned by [prepare], or null if it is gone. */
    suspend fun read(path: String): ByteArray?

    suspend fun delete(path: String)
}
