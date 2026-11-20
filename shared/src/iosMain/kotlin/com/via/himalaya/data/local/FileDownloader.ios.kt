package com.via.himalaya.data.local

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile

class IosFileDownloader(
    private val httpClient: HttpClient
) : FileDownloader {
    
    private fun getDocumentsDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        return paths.first() as String
    }
    
    override suspend fun writeFile(fileName: String, content: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val filePath = "${getDocumentsDirectory()}/$fileName.json"
                // atomically: written aside and moved into place by Foundation.
                val saved = content.encodeToByteArray().toNSData().writeToFile(filePath, atomically = true)
                println("FileDownloader: File ${if (saved) "saved" else "NOT saved"} to $filePath")
                saved
            } catch (e: Exception) {
                println("FileDownloader: Error saving file: ${e.message}")
                false
            }
        }
    }

    override suspend fun readFile(fileName: String): String? {
        return withContext(Dispatchers.Default) {
            try {
                val documentsDir = getDocumentsDirectory()
                val filePath = "$documentsDir/$fileName.json"
                
                if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
                    println("FileDownloader: File does not exist: $filePath")
                    return@withContext null
                }
                
                val nsData = platform.Foundation.NSData.dataWithContentsOfFile(filePath)
                if (nsData == null) {
                    println("FileDownloader: Failed to read file data: $filePath")
                    return@withContext null
                }
                
                val bytes = ByteArray(nsData.length.toInt())
                nsData.getBytes(bytes.refTo(0), nsData.length)
                val content = bytes.decodeToString()
                
                println("FileDownloader: File read successfully from $filePath")
                content
            } catch (e: Exception) {
                println("FileDownloader: Error reading file: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
    
    override fun fileExists(fileName: String): Boolean {
        val documentsDir = getDocumentsDirectory()
        val filePath = "$documentsDir/$fileName.json"
        return NSFileManager.defaultManager.fileExistsAtPath(filePath)
    }
    
    override fun deleteFile(fileName: String): Boolean {
        return try {
            val documentsDir = getDocumentsDirectory()
            val filePath = "$documentsDir/$fileName.json"
            if (NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
                NSFileManager.defaultManager.removeItemAtPath(filePath, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("FileDownloader: Error deleting file: ${e.message}")
            false
        }
    }
    
    private fun ByteArray.toNSData(): platform.Foundation.NSData {
        return platform.Foundation.NSData.create(
            bytes = this.refTo(0),
            length = this.size.toULong()
        )
    }
}
