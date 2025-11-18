package com.ethran.notable.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.db.Notebook
import com.ethran.notable.data.db.Page
import com.ethran.notable.data.db.Stroke
import com.ethran.notable.editor.drawing.drawStroke
import com.ethran.notable.TAG
import io.shipbook.shipbooksdk.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarView(navController: NavController) {
    val context = LocalContext.current
    val appRepository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()

    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDate by remember { mutableStateOf<Date?>(Date()) } // Auto-select today
    var notesForDate by remember { mutableStateOf<List<NotebookOrPage>>(emptyList()) }
    var datesWithActivity by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dailyMemoPageId by remember { mutableStateOf<String?>(null) }
    var dailyMemoBookId by remember { mutableStateOf<String?>(null) }

    // Load dates with activity when month changes
    LaunchedEffect(currentMonth.get(Calendar.MONTH), currentMonth.get(Calendar.YEAR)) {
        datesWithActivity = loadDatesWithActivity(appRepository, currentMonth)
    }

    // Load notes when a date is selected
    LaunchedEffect(selectedDate) {
        if (selectedDate != null) {
            notesForDate = loadNotesForDate(appRepository, selectedDate!!)
            val memoInfo = findDailyMemo(appRepository, selectedDate!!)
            dailyMemoPageId = memoInfo?.first
            dailyMemoBookId = memoInfo?.second
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopBar(navController)

            // Top row: Calendar (left) and Today's Notes (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .padding(horizontal = 8.dp)
            ) {
                // Left side: Calendar
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                ) {
                    // Month navigation
                    MonthNavigationBar(
                        currentMonth = currentMonth,
                        onPreviousMonth = {
                            val newMonth = currentMonth.clone() as Calendar
                            newMonth.add(Calendar.MONTH, -1)
                            currentMonth = newMonth
                        },
                        onNextMonth = {
                            val newMonth = currentMonth.clone() as Calendar
                            newMonth.add(Calendar.MONTH, 1)
                            currentMonth = newMonth
                        }
                    )

                    Divider()

                    // Calendar grid
                    CalendarGrid(
                        currentMonth = currentMonth,
                        selectedDate = selectedDate,
                        datesWithActivity = datesWithActivity,
                        onDateSelected = { date -> selectedDate = date }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right side: Today's Notes
                if (selectedDate != null) {
                    TodaysNotesSection(
                        selectedDate = selectedDate!!,
                        notesForDate = notesForDate,
                        navController = navController,
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight()
                    )
                } else {
                    // Placeholder when no date selected
                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select a date to view notes",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom row: Memo (full width, takes 65% of screen)
            if (selectedDate != null) {
                MemoSection(
                    selectedDate = selectedDate!!,
                    navController = navController,
                    dailyMemoPageId = dailyMemoPageId,
                    dailyMemoBookId = dailyMemoBookId,
                    appRepository = appRepository,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.65f)
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TopBar(navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colors.onBackground
            )
        }

        Text(
            text = "Calendar",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        // Empty spacer to balance the layout
        Spacer(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun MonthNavigationBar(
    currentMonth: Calendar,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Previous Month"
            )
        }

        Text(
            text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonth.time),
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold
        )

        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Next Month"
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    currentMonth: Calendar,
    selectedDate: Date?,
    datesWithActivity: Set<String>,
    onDateSelected: (Date) -> Unit
) {
    Column(modifier = Modifier
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .fillMaxWidth()) {
        // Day headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Calendar days
        val calendar = currentMonth.clone() as Calendar
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val weeks = mutableListOf<List<Int?>>()
        var currentWeek = mutableListOf<Int?>()

        // Add empty cells for days before the first day of month
        repeat(firstDayOfWeek) {
            currentWeek.add(null)
        }

        // Add days of the month
        for (day in 1..daysInMonth) {
            currentWeek.add(day)
            if (currentWeek.size == 7) {
                weeks.add(currentWeek.toList())
                currentWeek = mutableListOf()
            }
        }

        // Add remaining empty cells
        if (currentWeek.isNotEmpty()) {
            while (currentWeek.size < 7) {
                currentWeek.add(null)
            }
            weeks.add(currentWeek.toList())
        }

        weeks.forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                week.forEach { day ->
                    if (day != null) {
                        val date = calendar.clone() as Calendar
                        date.set(Calendar.DAY_OF_MONTH, day)
                        val dateKey = formatDateKey(date.time)
                        val isSelected = selectedDate?.let { formatDateKey(it) == dateKey } ?: false
                        val hasActivity = datesWithActivity.contains(dateKey)

                        DayCell(
                            day = day,
                            isSelected = isSelected,
                            hasActivity = hasActivity,
                            onClick = { onDateSelected(date.time) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    day: Int,
    isSelected: Boolean,
    hasActivity: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.15f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colors.primary else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.caption,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (hasActivity) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .size(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.primary)
                )
            }
        }
    }
}

@Composable
private fun TodaysNotesSection(
    selectedDate: Date,
    notesForDate: List<NotebookOrPage>,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Header
        Text(
            text = "Today's Notes",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(selectedDate),
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Divider()

        Spacer(modifier = Modifier.height(8.dp))

        // Notes list
        if (notesForDate.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(notesForDate) { item ->
                    // Only notebooks now, no standalone pages
                    when (item) {
                        is NotebookOrPage.NotebookItem -> {
                            NotebookListItem(
                                notebook = item.notebook,
                                onClick = {
                                    val pageId = item.notebook.openPageId ?: item.notebook.pageIds.firstOrNull()
                                    if (pageId != null) {
                                        navController.navigate("books/${item.notebook.id}/pages/$pageId")
                                    }
                                }
                            )
                        }
                        is NotebookOrPage.PageItem -> {
                            // Should never happen now since we filter out pages
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No notes edited on this date",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun MemoSection(
    selectedDate: Date,
    navController: NavController,
    dailyMemoPageId: String?,
    dailyMemoBookId: String?,
    appRepository: AppRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var memoPage by remember { mutableStateOf<Page?>(null) }
    var strokes by remember { mutableStateOf<List<Stroke>>(emptyList()) }

    // Load memo page and strokes when date or dailyMemoPageId changes
    LaunchedEffect(selectedDate, dailyMemoPageId) {
        if (dailyMemoPageId != null) {
            withContext(Dispatchers.IO) {
                try {
                    val pageWithStrokes = appRepository.pageRepository.getWithStrokeById(dailyMemoPageId)
                    memoPage = pageWithStrokes.page
                    strokes = pageWithStrokes.strokes
                    Log.d(TAG, "Loaded memo for ${formatDateKey(selectedDate)}: ${strokes.size} strokes")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading memo page: ${e.message}", e)
                    memoPage = null
                    strokes = emptyList()
                }
            }
        } else {
            memoPage = null
            strokes = emptyList()
            Log.d(TAG, "No memo for ${formatDateKey(selectedDate)}")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Header with action button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Memo",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = {
                    scope.launch {
                        if (dailyMemoPageId != null && dailyMemoBookId != null) {
                            // Open existing memo
                            navController.navigate("books/$dailyMemoBookId/pages/$dailyMemoPageId")
                        } else {
                            // Create and open new memo
                            val memoInfo = createDailyMemo(appRepository, selectedDate)
                            if (memoInfo != null) {
                                navController.navigate("books/${memoInfo.second}/pages/${memoInfo.first}")
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text(if (dailyMemoPageId != null) "Open" else "Create")
            }
        }

        Divider()

        Spacer(modifier = Modifier.height(8.dp))

        // Memo content preview card - renders actual strokes
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable {
                    scope.launch {
                        if (dailyMemoPageId != null && dailyMemoBookId != null) {
                            navController.navigate("books/$dailyMemoBookId/pages/$dailyMemoPageId")
                        } else {
                            val memoInfo = createDailyMemo(appRepository, selectedDate)
                            if (memoInfo != null) {
                                navController.navigate("books/${memoInfo.second}/pages/${memoInfo.first}")
                            }
                        }
                    }
                },
            elevation = 2.dp,
            shape = RoundedCornerShape(8.dp),
            backgroundColor = MaterialTheme.colors.surface
        ) {
            if (dailyMemoPageId != null && strokes.isNotEmpty()) {
                // Render actual strokes
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.White)
                ) {
                    // Calculate scale to fit all strokes in view
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // Simple scaling - assumes page is roughly 2000x3000 pixels
                    val scale = minOf(canvasWidth / 2000f, canvasHeight / 3000f)

                    drawIntoCanvas { canvas ->
                        strokes.forEach { stroke ->
                            try {
                                // Scale stroke positions
                                val scaledStroke = stroke.copy(
                                    points = stroke.points.map { point ->
                                        point.copy(
                                            x = point.x * scale,
                                            y = point.y * scale
                                        )
                                    },
                                    size = stroke.size * scale
                                )
                                drawStroke(canvas.nativeCanvas, scaledStroke, Offset.Zero)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error drawing stroke: ${e.message}")
                            }
                        }
                    }
                }
            } else if (dailyMemoPageId != null && strokes.isEmpty()) {
                // Empty memo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Empty memo\nTap to start writing",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // No memo yet - show create prompt
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Memo",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colors.primary.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No memo for this day",
                            style = MaterialTheme.typography.subtitle1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Tap to create a new daily memo",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookListItem(notebook: Notebook, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = notebook.title,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(notebook.updatedAt),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun PageListItem(page: Page, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Page",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(page.updatedAt),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// Data structures
sealed class NotebookOrPage {
    data class NotebookItem(val notebook: Notebook) : NotebookOrPage()
    data class PageItem(val page: Page) : NotebookOrPage()
}

// Helper functions
private fun formatDateKey(date: Date): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
}

private fun isSameDay(date1: Date, date2: Date): Boolean {
    return formatDateKey(date1) == formatDateKey(date2)
}

private suspend fun loadDatesWithActivity(
    appRepository: AppRepository,
    currentMonth: Calendar
): Set<String> = withContext(Dispatchers.IO) {
    try {
        val startOfMonth = currentMonth.clone() as Calendar
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        startOfMonth.set(Calendar.HOUR_OF_DAY, 0)
        startOfMonth.set(Calendar.MINUTE, 0)
        startOfMonth.set(Calendar.SECOND, 0)

        val endOfMonth = currentMonth.clone() as Calendar
        endOfMonth.set(Calendar.DAY_OF_MONTH, currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH))
        endOfMonth.set(Calendar.HOUR_OF_DAY, 23)
        endOfMonth.set(Calendar.MINUTE, 59)
        endOfMonth.set(Calendar.SECOND, 59)

        val dates = mutableSetOf<String>()

        // Only show activity for notebooks (including Daily Memos)
        // Standalone pages are not included
        appRepository.bookRepository.getAll().forEach { notebook ->
            if (notebook.updatedAt.after(startOfMonth.time) && notebook.updatedAt.before(endOfMonth.time)) {
                dates.add(formatDateKey(notebook.updatedAt))
            }
        }

        dates
    } catch (e: Exception) {
        Log.e(TAG, "Error loading dates with activity: ${e.message}", e)
        emptySet()
    }
}

private suspend fun loadNotesForDate(
    appRepository: AppRepository,
    date: Date
): List<NotebookOrPage> = withContext(Dispatchers.IO) {
    try {
        val startOfDay = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        val endOfDay = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        // Show all notebooks (including Daily Memos) that were updated on this date
        // Standalone pages are excluded
        val notebooks = appRepository.bookRepository.getAll()
            .filter { it.updatedAt.after(startOfDay.time) && it.updatedAt.before(endOfDay.time) }
            .map { NotebookOrPage.NotebookItem(it) }

        notebooks.sortedByDescending { it.notebook.updatedAt }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading notes for date: ${e.message}", e)
        emptyList()
    }
}

private suspend fun findDailyMemo(
    appRepository: AppRepository,
    date: Date
): Pair<String, String>? = withContext(Dispatchers.IO) {
    try {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        val memoTitle = "Daily Memo - $dateStr"

        // Check if daily memo already exists
        val existingMemo = appRepository.bookRepository.getAll()
            .find { it.title == memoTitle }

        if (existingMemo != null) {
            // Return the first page and book id of existing memo
            val pageId = existingMemo.pageIds.firstOrNull()
            return@withContext if (pageId != null) Pair(pageId, existingMemo.id) else null
        }

        null
    } catch (e: Exception) {
        Log.e(TAG, "Error finding daily memo: ${e.message}", e)
        null
    }
}

private suspend fun createDailyMemo(
    appRepository: AppRepository,
    date: Date
): Pair<String, String>? = withContext(Dispatchers.IO) {
    try {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        val memoTitle = "Daily Memo - $dateStr"

        // Create new daily memo notebook with proper setup
        val newNotebook = Notebook(
            title = memoTitle,
            createdAt = date,
            updatedAt = date
        )

        // Use the standard create method which automatically creates first page
        appRepository.bookRepository.create(newNotebook)

        // Get the pageIds that were automatically created
        val createdNotebook = appRepository.bookRepository.getById(newNotebook.id)
        val pageId = createdNotebook?.pageIds?.firstOrNull() ?: createdNotebook?.openPageId

        if (pageId != null) {
            Log.d(TAG, "Created daily memo: $memoTitle with page: $pageId")
            Pair(pageId, newNotebook.id)
        } else {
            Log.e(TAG, "Created daily memo but no page found")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error creating daily memo: ${e.message}", e)
        null
    }
}
