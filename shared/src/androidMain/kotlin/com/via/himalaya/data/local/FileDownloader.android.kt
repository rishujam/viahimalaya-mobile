package com.via.himalaya.data.local

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidFileDownloader(
    private val context: Context,
    private val httpClient: HttpClient
) : FileDownloader {
    
    private fun getFilesDir(): File {
        return context.filesDir
    }
    
    override suspend fun writeFile(fileName: String, content: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Written beside the target and renamed over it, so a write cut
                // short never leaves half a file where a whole one was.
                val file = File(getFilesDir(), "$fileName.json")
                val partial = File(getFilesDir(), "$fileName.json.part")
                partial.writeText(content)
                if (!partial.renameTo(file)) {
                    file.delete()
                    if (!partial.renameTo(file)) error("could not move $partial into place")
                }
                println("FileDownloader: File saved to ${file.absolutePath}")
                true
            } catch (e: Exception) {
                println("FileDownloader: Error saving file: ${e.message}")
                false
            }
        }
    }

    override suspend fun readFile(fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(getFilesDir(), "$fileName.json")
                if (!file.exists()) {
                    println("FileDownloader: File does not exist: ${file.absolutePath}")
                    return@withContext null
                }
                
                val content = file.readText()
                println("FileDownloader: File read successfully from ${file.absolutePath}")
                content
            } catch (e: Exception) {
                println("FileDownloader: Error reading file: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
    
    override fun fileExists(fileName: String): Boolean {
        val file = File(getFilesDir(), "$fileName.json")
        return file.exists()
    }
    
    override fun deleteFile(fileName: String): Boolean {
        return try {
            val file = File(getFilesDir(), "$fileName.json")
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            println("FileDownloader: Error deleting file: ${e.message}")
            false
        }
    }
}
