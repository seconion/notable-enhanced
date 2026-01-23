package com.ethran.notable.utils

import android.content.Context
import android.graphics.Bitmap
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.db.Reminder
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import io.shipbook.shipbooksdk.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiReminders {
    private const val TAG = "AiReminders"

    private val PROMPT = """
        Analyze this handwritten note.
        Extract any tasks, to-do items, or reminders.
        Return ONLY the text of the reminders, one per line.
        Do not include checkboxes or bullet points in the text.
        If there are no clear reminders, return nothing.
    """.trimIndent()

    suspend fun processReminder(context: Context, bitmap: Bitmap): String? {
        val settings = GlobalAppSettings.current

        return when (settings.aiBackend) {
            AppSettings.AiBackend.Gemini -> processWithGemini(context, bitmap, settings)
            AppSettings.AiBackend.Ollama -> processWithOllama(context, bitmap, settings)
        }
    }

    private suspend fun processWithGemini(
        context: Context,
        bitmap: Bitmap,
        settings: AppSettings
    ): String? {
        val apiKey = settings.geminiApiKey
        if (apiKey.isBlank()) {
            Log.e(TAG, "Gemini API Key is missing")
            return "API Key is missing. Please set it in Settings."
        }

        // Create a new bitmap with a white background
        val whiteBgBitmap = createWhiteBackgroundBitmap(bitmap)

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey
        )

        return try {
            val response = withContext(Dispatchers.IO) {
                generativeModel.generateContent(
                    content {
                        image(whiteBgBitmap)
                        text(PROMPT)
                    }
                )
            }

            processResponseText(context, response.text)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ${e.message}", e)
            when {
                e.message?.contains("401") == true -> "Invalid API Key."
                e.message?.contains("Unable to resolve host") == true -> "No Internet connection."
                else -> "Error: ${e.message}"
            }
        }
    }

    private suspend fun processWithOllama(
        context: Context,
        bitmap: Bitmap,
        settings: AppSettings
    ): String? {
        val ollamaUrl = settings.ollamaUrl
        if (ollamaUrl.isBlank()) {
            Log.e(TAG, "Ollama URL is missing")
            return "Ollama URL is missing. Please set it in Settings."
        }

        val model = settings.ollamaModel.ifBlank { "minicpm-v" }

        // Create a new bitmap with a white background
        val whiteBgBitmap = createWhiteBackgroundBitmap(bitmap)

        val result = OllamaClient.generateFromImage(
            baseUrl = ollamaUrl,
            model = model,
            prompt = PROMPT,
            bitmap = whiteBgBitmap
        )

        return result.fold(
            onSuccess = { responseText ->
                processResponseText(context, responseText)
            },
            onFailure = { error ->
                Log.e(TAG, "Ollama error: ${error.message}")
                "Ollama error: ${error.message}"
            }
        )
    }

    private fun createWhiteBackgroundBitmap(bitmap: Bitmap): Bitmap {
        val whiteBgBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(whiteBgBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return whiteBgBitmap
    }

    private fun processResponseText(context: Context, text: String?): String? {
        if (!text.isNullOrBlank()) {
            val repository = AppRepository(context)
            val lines = text.lines().filter { it.isNotBlank() }

            lines.forEach { line ->
                val cleanLine = line.trim().removePrefix("-").removePrefix("*").trim()
                if (cleanLine.isNotEmpty()) {
                    val reminder = Reminder(text = cleanLine)
                    repository.reminderRepository.create(reminder)
                    Log.i(TAG, "Created reminder: $cleanLine")
                }
            }
            return null // Success
        } else {
            Log.w(TAG, "No text generated from image")
            return "No text found in the selection."
        }
    }
}
