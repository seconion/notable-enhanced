package com.ethran.notable.io

import android.content.Context
import android.util.Log
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WebDAV uploader for automatically uploading PDFs to a WebDAV server
 */
object WebDavUploader {
    private const val TAG = "WebDavUploader"

    /**
     * Uploads a PDF file to the configured WebDAV server
     *
     * @param context Android context
     * @param pdfFile The PDF file to upload
     * @param notebookName Optional name of the notebook (used for organizing files on server)
     * @return True if upload was successful, false otherwise
     */
    suspend fun uploadPdf(
        context: Context,
        pdfFile: File,
        notebookName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val settings = GlobalAppSettings.current

            // Check if WebDAV is enabled and configured
            if (!settings.webdavEnabled) {
                Log.d(TAG, "WebDAV upload disabled")
                return@withContext false
            }

            if (settings.webdavUrl.isBlank()) {
                Log.e(TAG, "WebDAV URL not configured")
                return@withContext false
            }

            // Create Sardine client
            val sardine = OkHttpSardine()
            if (settings.webdavUsername.isNotBlank() && settings.webdavPassword.isNotBlank()) {
                sardine.setCredentials(settings.webdavUsername, settings.webdavPassword)
            }

            // Normalize WebDAV URL (ensure it ends with /)
            val baseUrl = if (settings.webdavUrl.endsWith("/")) {
                settings.webdavUrl
            } else {
                settings.webdavUrl + "/"
            }

            // Build remote path - upload directly to Notable folder without subfolders
            // Use sanitized notebook name as the filename if provided
            val fileName = if (notebookName != null) {
                "${sanitizeFileName(notebookName)}.pdf"
            } else {
                pdfFile.name
            }
            val remotePath = "${baseUrl}Notable/${fileName}"

            Log.d(TAG, "Uploading ${fileName} to $remotePath")

            // Create Notable directory if it doesn't exist
            try {
                val notableDir = "${baseUrl}Notable/"
                if (!sardine.exists(notableDir)) {
                    sardine.createDirectory(notableDir)
                    Log.d(TAG, "Created Notable directory")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not create Notable directory (may already exist): ${e.message}")
            }

            // Check if file already exists and delete it (overwrite)
            try {
                if (sardine.exists(remotePath)) {
                    Log.d(TAG, "File already exists, deleting old version: $remotePath")
                    sardine.delete(remotePath)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not check/delete existing file: ${e.message}")
            }

            // Upload the file
            pdfFile.inputStream().use { inputStream ->
                val bytes = inputStream.readBytes()
                sardine.put(remotePath, bytes, "application/pdf")
            }

            Log.i(TAG, "Successfully uploaded ${pdfFile.name}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload PDF: ${e.message}", e)
            false
        }
    }

    /**
     * Tests the WebDAV connection with current settings
     *
     * @return True if connection is successful, throws exception otherwise
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        val settings = GlobalAppSettings.current

        if (settings.webdavUrl.isBlank()) {
            Log.e(TAG, "WebDAV URL not configured")
            throw IllegalArgumentException("WebDAV URL is not configured")
        }

        Log.d(TAG, "Testing WebDAV connection to: ${settings.webdavUrl}")

        val sardine = OkHttpSardine()
        if (settings.webdavUsername.isNotBlank() && settings.webdavPassword.isNotBlank()) {
            Log.d(TAG, "Setting credentials for user: ${settings.webdavUsername}")
            sardine.setCredentials(settings.webdavUsername, settings.webdavPassword)
        } else {
            Log.d(TAG, "No credentials provided, attempting anonymous access")
        }

        // Normalize WebDAV URL (ensure it ends with /)
        val baseUrl = if (settings.webdavUrl.endsWith("/")) {
            settings.webdavUrl
        } else {
            settings.webdavUrl + "/"
        }

        Log.d(TAG, "Listing directory: $baseUrl")
        try {
            val resources = sardine.list(baseUrl, 0)
            Log.i(TAG, "WebDAV connection test successful - found ${resources.size} resources")

            // Upload a test file to verify write permissions
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val testFileName = "notable_connection_test_$timestamp.txt"
            val testFilePath = "${baseUrl}${testFileName}"
            val testContent = """
                Notable WebDAV Connection Test
                ===============================
                Test performed: $timestamp

                If you can see this file, your WebDAV connection is working correctly!
                Notable will upload PDF exports to this location.

                You can safely delete this file.
            """.trimIndent()

            Log.d(TAG, "Uploading test file: $testFilePath")
            sardine.put(testFilePath, testContent.toByteArray(Charsets.UTF_8), "text/plain")
            Log.i(TAG, "Successfully uploaded test file: $testFileName")

            true
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV connection test failed: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
    }

    /**
     * Sanitizes a filename to be safe for WebDAV paths
     */
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
