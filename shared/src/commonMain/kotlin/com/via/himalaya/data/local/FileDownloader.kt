package com.via.himalaya.data.local

interface FileDownloader {
    /**
     * Saves [content] to local storage as [fileName] (without extension),
     * replacing any earlier copy. The caller already has the text from its own
     * request; there used to be a downloadFile(url) here, which fetched every
     * file a second time just to store it.
     * @return true when saved
     */
    suspend fun writeFile(fileName: String, content: String): Boolean
    
    /**
     * Reads a file from local storage
     * @param fileName The name of the file to read (without extension)
     * @return The file content as a string, or null if file doesn't exist or read failed
     */
    suspend fun readFile(fileName: String): String?
    
    /**
     * Checks if a file exists in local storage
     * @param fileName The name of the file to check
     * @return true if file exists, false otherwise
     */
    fun fileExists(fileName: String): Boolean
    
    /**
     * Deletes a file from local storage
     * @param fileName The name of the file to delete
     * @return true if deletion was successful, false otherwise
     */
    fun deleteFile(fileName: String): Boolean
}
