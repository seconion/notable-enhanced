package com.ethran.notable.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import com.ethran.notable.SCREEN_HEIGHT
import com.ethran.notable.SCREEN_WIDTH
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.db.Image
import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.getBackgroundType
import com.ethran.notable.editor.drawing.drawBg
import com.ethran.notable.editor.drawing.drawImage
import com.ethran.notable.editor.drawing.drawStroke
import com.ethran.notable.io.WebDavUploader
import io.shipbook.shipbooksdk.Log
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiMarkdownExporter {
    private const val TAG = "AiMarkdownExporter"

    private val markdownPrompt = """
You are a handwriting-to-Markdown converter. Analyze this handwritten note image and convert it to well-formatted Markdown.

RECOGNITION RULES:
1. HIGHLIGHTS: Text with colored background or underlines -> **bold**
2. BULLET POINTS: Hand-drawn dots, dashes, arrows -> - item or * item
3. NUMBERED LISTS: Numbers (1, 2, 3) -> 1. item, 2. item
4. TABLES: Grid patterns or aligned columns -> | Col1 | Col2 | with separators
5. HEADERS: Large or emphasized text -> # Header or ## Subheader
6. CHECKBOXES: Empty box -> - [ ], Checked box -> - [x]
7. INDENTATION: Preserve hierarchy with proper nesting

CRITICAL: Return ONLY the raw Markdown content. Do NOT:
- Wrap in code fences (no ```)
- Add notes, explanations, or commentary
- Include phrases like "Note:", "Here is", etc.
Just output the pure Markdown text, nothing else.
""".trimIndent()

    suspend fun exportPageToMarkdown(
        context: Context,
        pageId: String,
        notebookTitle: String?
    ): String = withContext(Dispatchers.IO) {
        val settings = GlobalAppSettings.current
        if (!settings.webdavEnabled) {
            return@withContext "WebDAV is not enabled. Configure it in Settings."
        }
        if (settings.ollamaUrl.isBlank()) {
            return@withContext "Ollama URL not configured. Set it in Settings."
        }

        val repository = AppRepository(context.applicationContext)

        try {
            val bitmap = renderPageBitmap(context.applicationContext, repository, pageId)
            val rawContent = try {
                OllamaClient.generateFromImage(
                    baseUrl = settings.ollamaUrl,
                    model = settings.ollamaModel.ifBlank { "minicpm-v" },
                    prompt = markdownPrompt,
                    bitmap = bitmap
                ).getOrElse { error ->
                    return@withContext when {
                        error.message?.contains("timed out", ignoreCase = true) == true ->
                            "Request timed out. The model may still be loading."
                        else -> "AI conversion failed: ${error.message}"
                    }
                }
            } finally {
                bitmap.recycle()
            }

            if (rawContent.isBlank()) {
                return@withContext "AI returned empty content. The page may be blank."
            }

            val markdownContent = stripCodeFences(rawContent)
            if (markdownContent.isBlank()) {
                return@withContext "AI returned empty content. The page may be blank."
            }

            val fileName = generateFileName(repository, pageId, notebookTitle)
            val uploadSuccess = WebDavUploader.uploadMarkdown(
                context = context.applicationContext,
                markdownContent = markdownContent,
                fileName = fileName
            )

            if (uploadSuccess) "Exported: $fileName" else "Conversion succeeded but upload failed."
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}", e)
            "Export failed: ${e.message}"
        }
    }

    private fun renderPageBitmap(
        context: Context,
        repository: AppRepository,
        pageId: String
    ): Bitmap {
        val (page, strokes) = repository.pageRepository.getWithStrokeById(pageId)
        val (_, images) = repository.pageRepository.getWithImageById(pageId)
        val (contentWidth, contentHeight) = computeContentDimensions(strokes, images)

        val bitmap = createBitmap(contentWidth, contentHeight)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawBg(context, canvas, page.getBackgroundType(), page.background)
        images.forEach { drawImage(context, canvas, it, Offset.Zero) }
        strokes.forEach { drawStroke(canvas, it, Offset.Zero) }
        return bitmap
    }

    private fun computeContentDimensions(
        strokes: List<Stroke>,
        images: List<Image>
    ): Pair<Int, Int> {
        if (strokes.isEmpty() && images.isEmpty()) {
            return SCREEN_WIDTH to SCREEN_HEIGHT
        }

        val strokeBottom = strokes.maxOfOrNull { it.bottom.toInt() } ?: 0
        val strokeRight = strokes.maxOfOrNull { it.right.toInt() } ?: 0
        val imageBottom = images.maxOfOrNull { it.y + it.height } ?: 0
        val imageRight = images.maxOfOrNull { it.x + it.width } ?: 0

        val rawHeight = maxOf(strokeBottom, imageBottom) + 50
        val rawWidth = maxOf(strokeRight, imageRight) + 50
        return rawWidth.coerceAtLeast(SCREEN_WIDTH) to rawHeight.coerceAtLeast(SCREEN_HEIGHT)
    }

    private fun generateFileName(
        repository: AppRepository,
        pageId: String,
        notebookTitle: String?
    ): String {
        val page = repository.pageRepository.getById(pageId)
        val notebookId = page?.notebookId

        if (notebookId == null || notebookTitle.isNullOrBlank()) {
            val timestamp = ZonedDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
            return "quickpage_$timestamp.md"
        }

        val book = repository.bookRepository.getById(notebookId)
        val pageCount = book?.pageIds?.size ?: 0
        val pageIndex = book?.pageIds?.indexOf(pageId)?.takeIf { it >= 0 }?.plus(1)

        val baseName = sanitizeFileName(notebookTitle)
        return if (pageCount > 1 && pageIndex != null) {
            "${baseName}_p$pageIndex.md"
        } else {
            "$baseName.md"
        }
    }

    private fun stripCodeFences(content: String): String {
        val trimmed = content.trim()
        val codeFencePattern = Regex(
            """^```(?:markdown|md)?\s*\n([\s\S]*?)\n```""",
            RegexOption.IGNORE_CASE
        )

        val match = codeFencePattern.find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        val notePattern = Regex("""\n*\(Note:[\s\S]*$""", RegexOption.IGNORE_CASE)
        return trimmed.replace(notePattern, "").trim()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
