package com.ethran.notable.editor

import android.content.Context
import com.ethran.notable.TAG
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.io.ExportEngine
import com.ethran.notable.io.ExportFormat
import com.ethran.notable.io.ExportTarget
import com.ethran.notable.io.WebDavUploader
import com.ethran.notable.io.exportToLinkedFileNow
import io.shipbook.shipbooksdk.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val EDITOR_EXIT_DEBOUNCE_MS = 1500L

internal object EditorExitWorkScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingBookJobs = ConcurrentHashMap<String, Job>()

    fun schedule(context: Context, bookId: String?, appRepository: AppRepository) {
        if (bookId == null) return

        pendingBookJobs.remove(bookId)?.cancel()
        pendingBookJobs[bookId] = scope.launch {
            try {
                delay(EDITOR_EXIT_DEBOUNCE_MS)
                exportToLinkedFileNow(context, bookId, appRepository.bookRepository)
                uploadToWebDavIfNeeded(context, bookId, appRepository)
            } finally {
                pendingBookJobs.remove(bookId)
            }
        }
    }

    private suspend fun uploadToWebDavIfNeeded(
        context: Context,
        bookId: String,
        appRepository: AppRepository
    ) {
        val settings = GlobalAppSettings.current
        if (!settings.webdavEnabled) return

        try {
            val book = appRepository.bookRepository.getById(bookId) ?: return
            val pdfPath = ExportEngine(
                context,
                appRepository.pageRepository,
                appRepository.bookRepository
            ).exportAndGetFilePath(
                ExportTarget.Book(bookId),
                ExportFormat.PDF
            ) ?: run {
                Log.e(TAG, "Failed to export PDF for WebDAV upload")
                return
            }

            val pdfFile = File(pdfPath)
            if (!pdfFile.exists()) {
                Log.e(TAG, "PDF file does not exist: $pdfPath")
                return
            }

            val uploadSuccess = WebDavUploader.uploadPdf(
                context,
                pdfFile,
                book.title
            )
            if (uploadSuccess) {
                Log.i(TAG, "Successfully uploaded ${book.title} to WebDAV")
            } else {
                Log.e(TAG, "Failed to upload ${book.title} to WebDAV")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during WebDAV auto-upload: ${e.message}", e)
        }
    }
}
