package com.via.himalaya.data.local

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
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
    
    override suspend fun downloadFile(url: String, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Download the file using Ktor
                val response = httpClient.get(url)
                
                if (response.status.value != 200) {
                    println("FileDownloader: Failed to download file. Status: ${response.status}")
                    return@withContext null
                }
                
                // Create the file in internal storage
                val file = File(getFilesDir(), "$fileName.json")
                
                // Write the response body to the file
                response.bodyAsChannel().toInputStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                println("FileDownloader: File downloaded successfully to ${file.absolutePath}")
                file.absolutePath
            } catch (e: Exception) {
                println("FileDownloader: Error downloading file: ${e.message}")
                e.printStackTrace()
                null
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
