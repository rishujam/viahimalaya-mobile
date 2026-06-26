package com.via.himalaya.data.local

interface FileDownloader {
    /**
     * Downloads a file from the given URL and saves it to local storage
     * @param url The URL to download from
     * @param fileName The name to save the file as (without extension)
     * @return The local file path where the file was saved, or null if failed
     */
    suspend fun downloadFile(url: String, fileName: String): String?
    
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
