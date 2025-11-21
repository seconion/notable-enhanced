package com.ethran.notable.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.db.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun StatsView(navController: NavController) {
    val context = LocalContext.current
    val appRepository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()

    var completedTasks by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    var totalScore by remember { mutableStateOf(0) }

    fun loadStats() {
        scope.launch(Dispatchers.IO) {
            // Fetch ALL reminders that are done
            val allReminders = appRepository.reminderRepository.getAll()
            val done = allReminders.filter { it.isDone }
            
            withContext(Dispatchers.Main) {
                completedTasks = done
                totalScore = done.size
            }
        }
    }

    LaunchedEffect(Unit) {
        loadStats()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Your Journey",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            // Reset stats logic: Delete all done tasks? Or just "archive" them?
                            // For simplicity, let's delete completed tasks to reset the climb.
                            completedTasks.forEach { task ->
                                appRepository.reminderRepository.deleteById(task.id)
                            }
                            loadStats()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Reset Journey", color = Color.White)
                }
            }

            // Mountain View (The Climb)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .padding(16.dp)
                    .background(Color(0xFFF0F4F8), shape = RoundedCornerShape(16.dp))
            ) {
                MountainClimb(totalScore)
            }

            // Monthly Progress Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .padding(16.dp)
            ) {
                ProductivityChart(completedTasks)
            }
        }
    }
}

@Composable
fun MountainClimb(score: Int) {
    val level = score % 100 // 0 to 100 progress per "mountain"
    val mountainColor = Color(0xFF5D4037)
    val skyColor = Color(0xFFE3F2FD)
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        
        // Draw Sky
        drawRect(color = skyColor)
        
        // Draw Mountain Path (Triangle)
        val path = Path().apply {
            moveTo(0f, h)
            lineTo(w / 2, h * 0.2f) // Peak
            lineTo(w, h)
            close()
        }
        drawPath(path = path, color = mountainColor)
        
        // Draw Snow Cap
        val snowPath = Path().apply {
            moveTo(w * 0.35f, h * 0.44f)
            lineTo(w / 2, h * 0.2f)
            lineTo(w * 0.65f, h * 0.44f)
            // Zigzag bottom
            lineTo(w * 0.58f, h * 0.40f)
            lineTo(w * 0.50f, h * 0.45f)
            lineTo(w * 0.42f, h * 0.40f)
            close()
        }
        drawPath(path = snowPath, color = Color.White)

        // Draw "Pixel Person" (Rock Pusher)
        // Calculate position based on 'level' (0-100)
        // We climb the left side: x goes from 0 to w/2, y goes from h to h*0.2
        val progress = (level / 100f).coerceIn(0f, 1f)
        val personX = w * 0.5f * progress
        val personY = h - (h * 0.8f * progress)
        
        // Draw Person (Simple Circle/Stick)
        drawCircle(color = Color.Black, radius = 8.dp.toPx(), center = Offset(personX, personY - 15.dp.toPx()))
        // Body
        drawLine(
            color = Color.Black, 
            start = Offset(personX, personY - 15.dp.toPx()), 
            end = Offset(personX, personY), 
            strokeWidth = 4.dp.toPx()
        )
        
        // Draw the Rock
        val rockSize = 12.dp.toPx()
        drawCircle(
            color = Color.Gray, 
            radius = rockSize, 
            center = Offset(personX + 16.dp.toPx(), personY - 5.dp.toPx())
        )
        
        // Draw Stats Text
        // Using nativeCanvas for text drawing is complex in Compose Canvas without TextMeasurer (available in newer compose)
        // So we overlay Text in the Box instead.
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Score: $score",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Black,
                color = Color(0xFF3E2723)
            )
            Text(
                text = "Elevation: ${level}m / 100m",
                style = MaterialTheme.typography.subtitle1,
                color = Color(0xFF5D4037)
            )
        }
    }
}

@Composable
fun ProductivityChart(tasks: List<Reminder>) {
    // Group by Month (Last 6 months)
    val monthlyCounts = remember(tasks) {
        val calendar = Calendar.getInstance()
        val counts = mutableMapOf<String, Int>()
        val labels = mutableListOf<String>()
        
        // Initialize last 6 months with 0
        for (i in 5 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -i)
            val key = SimpleDateFormat("MMM", Locale.getDefault()).format(c.time)
            counts[key] = 0
            labels.add(key)
        }
        
        tasks.forEach { task ->
            if (task.completedAt != null) { // Use completedAt if available
                val c = Calendar.getInstance()
                c.time = task.completedAt
                val key = SimpleDateFormat("MMM", Locale.getDefault()).format(c.time)
                if (counts.containsKey(key)) {
                    counts[key] = counts[key]!! + 1
                }
            } else if (task.updatedAt != null) { // Fallback to updatedAt
                 val c = Calendar.getInstance()
                c.time = task.updatedAt
                val key = SimpleDateFormat("MMM", Locale.getDefault()).format(c.time)
                if (counts.containsKey(key)) {
                    counts[key] = counts[key]!! + 1
                }
            }
        }
        Pair(labels, counts)
    }

    Column {
        Text("Monthly Progress", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        Canvas(modifier = Modifier.fillMaxSize().padding(bottom = 20.dp)) {
            val w = size.width
            val h = size.height
            val barWidth = w / (monthlyCounts.first.size * 1.5f)
            val maxCount = monthlyCounts.second.values.maxOrNull() ?: 1
            val scale = h / (maxCount + 1).toFloat()
            
            monthlyCounts.first.forEachIndexed { index, month ->
                val count = monthlyCounts.second[month] ?: 0
                val barHeight = count * scale
                val x = index * (w / monthlyCounts.first.size) + (barWidth / 2)
                val y = h - barHeight
                
                // Draw Bar
                drawRect(
                    color = if (index == monthlyCounts.first.lastIndex) Color(0xFF4CAF50) else Color.Gray,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight)
                )
                
                // Note: Drawing text directly on canvas requires nativeCanvas access.
                // For simplicity in this prototype, we omit axis labels on canvas or rely on overlay.
            }
        }
        // Simple Row for labels below canvas?
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            monthlyCounts.first.forEach { month ->
                Text(text = month, style = MaterialTheme.typography.caption)
            }
        }
    }
}
