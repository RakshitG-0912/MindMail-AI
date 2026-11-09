package com.example.ui.screens.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TaskItem
import com.example.model.TaskStatus
import com.example.ui.components.TaskCard
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.UrgentRed
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.MailMindViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: MailMindViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.allTasks.collectAsState()

    var isFullCalendarExpanded by remember { mutableStateOf(false) }
    var selectedCal by remember { mutableStateOf(Calendar.getInstance()) }

    // Start & end of currently selected day
    val selectedDayStart = Calendar.getInstance().apply {
        time = selectedCal.time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val selectedDayEnd = selectedDayStart + (24 * 3600 * 1000L)

    val tasksForSelectedDay = tasks.filter { task ->
        val d = task.deadlineMillis
        d != null && d >= selectedDayStart && d < selectedDayEnd
    }

    val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedCal.time)
    val selectedDayFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(selectedCal.time)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("calendar_screen")
    ) {
        // Month Header & Navigation + Expand Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Calendar Schedule",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = monthYearFormat,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Expand / Collapse Full Month Button
                Button(
                    onClick = { isFullCalendarExpanded = !isFullCalendarExpanded },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFullCalendarExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("toggle_full_calendar_button")
                ) {
                    Icon(
                        imageVector = if (isFullCalendarExpanded) Icons.Default.ExpandLess else Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = if (isFullCalendarExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isFullCalendarExpanded) "Week Strip" else "Full Month",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFullCalendarExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        val newCal = Calendar.getInstance().apply {
                            time = selectedCal.time
                            if (isFullCalendarExpanded) {
                                add(Calendar.MONTH, -1)
                            } else {
                                add(Calendar.DAY_OF_YEAR, -1)
                            }
                        }
                        selectedCal = newCal
                    }
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous")
                }
                IconButton(
                    onClick = {
                        val newCal = Calendar.getInstance().apply {
                            time = selectedCal.time
                            if (isFullCalendarExpanded) {
                                add(Calendar.MONTH, 1)
                            } else {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        selectedCal = newCal
                    }
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // VIEW 1: EXPANDED FULL MONTH CALENDAR GRID
        AnimatedVisibility(visible = isFullCalendarExpanded) {
            FullMonthCalendarGrid(
                currentMonthCal = selectedCal,
                selectedCal = selectedCal,
                tasks = tasks,
                onSelectDate = { newDateCal ->
                    selectedCal = newDateCal
                }
            )
        }

        // VIEW 2: COMPACT 7-DAY STRIP (When collapsed)
        AnimatedVisibility(visible = !isFullCalendarExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                (-3..3).forEach { offset ->
                    val dayCal = Calendar.getInstance().apply {
                        time = selectedCal.time
                        add(Calendar.DAY_OF_YEAR, offset)
                    }
                    val isSelected = offset == 0
                    val dayLetter = SimpleDateFormat("EEE", Locale.getDefault()).format(dayCal.time).take(1)
                    val dayNumber = SimpleDateFormat("d", Locale.getDefault()).format(dayCal.time)

                    val dayStart = Calendar.getInstance().apply {
                        time = dayCal.time
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val dayEnd = dayStart + 86400000L

                    val dayTasks = tasks.filter { it.deadlineMillis != null && it.deadlineMillis >= dayStart && it.deadlineMillis < dayEnd }
                    val hasCompleted = dayTasks.any { it.status == TaskStatus.COMPLETED }
                    val hasOverdue = dayTasks.any { it.isOverdue() }
                    val hasPending = dayTasks.any { it.status != TaskStatus.COMPLETED && !it.isOverdue() }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .clickable {
                                selectedCal = dayCal
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = dayLetter,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dayNumber,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Status dot markers
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (hasOverdue) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) Color.White else UrgentRed))
                            }
                            if (hasPending) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) Color.White else WarningAmber))
                            }
                            if (hasCompleted) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) Color.White else SuccessGreen))
                            }
                            if (dayTasks.isEmpty()) {
                                Spacer(modifier = Modifier.size(5.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Day Label & Task Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedDayFormat,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${tasksForSelectedDay.size} tasks",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Task List for Selected Day
        if (tasksForSelectedDay.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No deadlines on this day",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tasks extracted from emails with deadlines will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(tasksForSelectedDay, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggleComplete = { viewModel.toggleTaskComplete(task.id) },
                        onDelete = { viewModel.deleteTask(task.id) },
                        onReschedule = {}
                    )
                }
            }
        }
    }
}

@Composable
fun FullMonthCalendarGrid(
    currentMonthCal: Calendar,
    selectedCal: Calendar,
    tasks: List<TaskItem>,
    onSelectDate: (Calendar) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("full_month_calendar_grid"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Day of week labels row
            val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                dayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Build month calendar days
            val cal = Calendar.getInstance().apply {
                time = currentMonthCal.time
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0-indexed for Sunday

            val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7

            for (week in 0 until (totalCells / 7)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (dayOfWeek in 0..6) {
                        val cellIndex = week * 7 + dayOfWeek
                        val dayNumber = cellIndex - firstDayOfWeek + 1

                        if (dayNumber in 1..daysInMonth) {
                            val cellCal = Calendar.getInstance().apply {
                                time = currentMonthCal.time
                                set(Calendar.DAY_OF_MONTH, dayNumber)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            val isSelected = cellCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                                    cellCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)

                            val dayStart = cellCal.timeInMillis
                            val dayEnd = dayStart + 86400000L
                            val cellTasks = tasks.filter { it.deadlineMillis != null && it.deadlineMillis >= dayStart && it.deadlineMillis < dayEnd }
                            val hasCompleted = cellTasks.any { it.status == TaskStatus.COMPLETED }
                            val hasOverdue = cellTasks.any { it.isOverdue() }
                            val hasPending = cellTasks.any { it.status != TaskStatus.COMPLETED && !it.isOverdue() }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable { onSelectDate(cellCal) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = dayNumber.toString(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp
                                )

                                // Marking dots for task status on that day
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.height(4.dp)
                                ) {
                                    if (hasOverdue) {
                                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) Color.White else UrgentRed))
                                    }
                                    if (hasPending) {
                                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) Color.White else WarningAmber))
                                    }
                                    if (hasCompleted) {
                                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) Color.White else SuccessGreen))
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(38.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
