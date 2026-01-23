package com.ethran.notable.utils

import android.graphics.Bitmap
import android.util.Base64
import io.shipbook.shipbooksdk.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object OllamaClient {
    private const val TAG = "OllamaClient"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class OllamaRequest(
        val model: String,
        val prompt: String,
        val images: List<String>,
        val stream: Boolean = false
    )

    @Serializable
    data class OllamaResponse(
        val model: String = "",
        val response: String = "",
        val done: Boolean = false,
        val error: String? = null
    )

    suspend fun generateFromImage(
        baseUrl: String,
        model: String,
        prompt: String,
        bitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Convert bitmap to base64
            val base64Image = bitmapToBase64(bitmap)

            // Prepare request
            val request = OllamaRequest(
                model = model,
                prompt = prompt,
                images = listOf(base64Image),
                stream = false
            )

            val requestBody = json.encodeToString(request)

            // Make HTTP request
            val url = URL("${baseUrl.trimEnd('/')}/api/generate")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 30000
                readTimeout = 120000 // Vision models can take time
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
            }

            val responseCode = connection.responseCode

            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "Ollama API error: $responseCode - $errorBody")
                return@withContext Result.failure(Exception("HTTP $responseCode: $errorBody"))
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            Log.d(TAG, "Raw response (first 500 chars): ${responseBody.take(500)}")

            // Ollama returns newline-delimited JSON (NDJSON), even with stream=false
            // We need to parse each line and concatenate the responses
            val fullResponse = StringBuilder()
            var lastError: String? = null

            responseBody.lines()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    try {
                        val parsed = json.decodeFromString<OllamaResponse>(line)
                        if (parsed.error != null) {
                            lastError = parsed.error
                        } else {
                            fullResponse.append(parsed.response)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse line: ${line.take(100)}, error: ${e.message}")
                    }
                }

            if (lastError != null) {
                Log.e(TAG, "Ollama error: $lastError")
                return@withContext Result.failure(Exception(lastError))
            }

            val result = fullResponse.toString()
            if (result.isBlank()) {
                return@withContext Result.failure(Exception("Empty response from Ollama"))
            }

            Log.i(TAG, "Ollama response received: ${result.take(100)}...")
            Result.success(result)

        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Unknown host: ${e.message}")
            Result.failure(Exception("Cannot reach Ollama server. Check the URL."))
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Connection refused: ${e.message}")
            Result.failure(Exception("Connection refused. Is Ollama running?"))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout: ${e.message}")
            Result.failure(Exception("Request timed out. The model may be loading."))
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Ollama: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun testConnection(baseUrl: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${baseUrl.trimEnd('/')}/api/tags")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP $responseCode"))
            }

            val responseBody = connection.inputStream.bufferedReader().readText()

            // Parse models from response
            val modelsRegex = """"name"\s*:\s*"([^"]+)"""".toRegex()
            val models = modelsRegex.findAll(responseBody)
                .map { it.groupValues[1] }
                .toList()

            Result.success(models)

        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
