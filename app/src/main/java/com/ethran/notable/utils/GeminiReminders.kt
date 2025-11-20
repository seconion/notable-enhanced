package com.ethran.notable.utils

import android.content.Context
import android.graphics.Bitmap
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.db.Reminder
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import io.shipbook.shipbooksdk.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiReminders {
    private const val TAG = "GeminiReminders"

    suspend fun processReminder(context: Context, bitmap: Bitmap): String? {
        val apiKey = GlobalAppSettings.current.geminiApiKey
        if (apiKey.isBlank()) {
            Log.e(TAG, "Gemini API Key is missing")
            return "API Key is missing. Please set it in Settings."
        }

        // Create a new bitmap with a white background
        val whiteBgBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(whiteBgBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey
        )

        val prompt = """
            Analyze this handwritten note. 
            Extract any tasks, to-do items, or reminders.
            Return ONLY the text of the reminders, one per line.
            Do not include checkboxes or bullet points in the text.
            If there are no clear reminders, return nothing.
        """.trimIndent()

        return try {
            val response = withContext(Dispatchers.IO) {
                generativeModel.generateContent(
                    content {
                        image(whiteBgBitmap)
                        text(prompt)
                    }
                )
            }

            val text = response.text
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
                null // Success
            } else {
                Log.w(TAG, "No text generated from image")
                "Gemini found no text in the selection."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ${e.message}", e)
            // Provide user-friendly error messages
            if (e.message?.contains("401") == true) {
                "Invalid API Key."
            } else if (e.message?.contains("Unable to resolve host") == true) {
                "No Internet connection."
            } else {
                "Error: ${e.message}"
            }
        }
    }
}