package com.ethran.notable.ui.views

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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.db.Notebook
import com.ethran.notable.data.db.Page
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
    var selectedDate by remember { mutableStateOf<Date?>(null) }
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

            Divider()

            // Day details section
            if (selectedDate != null) {
                DayDetailsSection(
                    selectedDate = selectedDate!!,
                    hasDailyMemo = dailyMemoPageId != null,
                    notesForDate = notesForDate,
                    navController = navController,
                    onOpenDailyMemo = {
                        scope.launch {
                            if (dailyMemoPageId != null && dailyMemoBookId != null) {
                                navController.navigate("books/$dailyMemoBookId/pages/$dailyMemoPageId")
                            } else {
                                // Create daily memo only when button is clicked
                                val memoInfo = createDailyMemo(appRepository, selectedDate!!)
                                if (memoInfo != null) {
                                    dailyMemoPageId = memoInfo.first
                                    dailyMemoBookId = memoInfo.second
                                    navController.navigate("books/${memoInfo.second}/pages/${memoInfo.first}")
                                }
                            }
                        }
                    }
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
        .fillMaxWidth()
        .height(180.dp)) {
        // Day headers
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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
                    .padding(vertical = 1.dp)
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
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colors.primary else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.caption,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (hasActivity) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.primary)
                )
            }
        }
    }
}

@Composable
private fun DayDetailsSection(
    selectedDate: Date,
    hasDailyMemo: Boolean,
    notesForDate: List<NotebookOrPage>,
    navController: NavController,
    onOpenDailyMemo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Date header
        Text(
            text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(selectedDate),
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Daily memo button
        Button(
            onClick = onOpenDailyMemo,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasDailyMemo) "Open Daily Memo" else "Create Daily Memo")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notes edited on this date
        if (notesForDate.isNotEmpty()) {
            Text(
                text = "Notes edited on this date:",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(notesForDate) { item ->
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
                            PageListItem(
                                page = item.page,
                                onClick = {
                                    navController.navigate("pages/${item.page.id}")
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "No notes edited on this date",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
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

        val notebooks = appRepository.bookRepository.getAll()
        val pages = appRepository.pageRepository.getAll()

        val dates = mutableSetOf<String>()

        notebooks.forEach { notebook ->
            if (notebook.updatedAt.after(startOfMonth.time) && notebook.updatedAt.before(endOfMonth.time)) {
                dates.add(formatDateKey(notebook.updatedAt))
            }
        }

        pages.forEach { page ->
            if (page.updatedAt.after(startOfMonth.time) && page.updatedAt.before(endOfMonth.time)) {
                dates.add(formatDateKey(page.updatedAt))
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

        val notebooks = appRepository.bookRepository.getAll()
            .filter { it.updatedAt.after(startOfDay.time) && it.updatedAt.before(endOfDay.time) }
            .filter { !it.title.startsWith("Daily Memo -") }
            .map { NotebookOrPage.NotebookItem(it) }

        val pages = appRepository.pageRepository.getAll()
            .filter { it.updatedAt.after(startOfDay.time) && it.updatedAt.before(endOfDay.time) }
            .map { NotebookOrPage.PageItem(it) }

        (notebooks + pages).sortedByDescending {
            when (it) {
                is NotebookOrPage.NotebookItem -> it.notebook.updatedAt
                is NotebookOrPage.PageItem -> it.page.updatedAt
            }
        }
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

        // Create new daily memo notebook
        val newNotebook = Notebook(
            title = memoTitle,
            createdAt = Date(),
            updatedAt = Date()
        )
        appRepository.bookRepository.create(newNotebook)

        // Create first page
        val newPage = Page(
            notebookId = newNotebook.id,
            createdAt = Date(),
            updatedAt = Date()
        )
        appRepository.pageRepository.create(newPage)

        // Add page to notebook
        appRepository.bookRepository.addPage(newNotebook.id, newPage.id)
        appRepository.bookRepository.setOpenPageId(newNotebook.id, newPage.id)

        Log.d(TAG, "Created daily memo: $memoTitle")
        Pair(newPage.id, newNotebook.id)
    } catch (e: Exception) {
        Log.e(TAG, "Error creating daily memo: ${e.message}", e)
        null
    }
}
