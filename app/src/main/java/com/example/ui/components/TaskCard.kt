package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.UrgentRed
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TaskCard(
    task: TaskItem,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onReschedule: () -> Unit,
    modifier: Modifier = Modifier,
    enableSwipeGesture: Boolean = true,
    onSwipeComplete: (() -> Unit)? = null,
    onSwipeIncomplete: (() -> Unit)? = null
) {
    val isCompleted = task.status == TaskStatus.COMPLETED
    val isOverdue = task.isOverdue()
    val isDueToday = task.isDueToday()
    val timeRemaining = task.getTimeRemainingText()

    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> UrgentRed
        TaskPriority.HIGH -> WarningAmber
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
        TaskPriority.LOW -> MaterialTheme.colorScheme.tertiary
    }

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isCompleted -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            isOverdue -> UrgentRed.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        },
        label = "border_color"
    )

    // Swipe gesture offset state
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val swipeThreshold = 180f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("task_item_card_${task.id}")
    ) {
        // Background swipe actions reveal
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        offsetX.value > 40f -> SuccessGreen.copy(alpha = 0.85f)
                        offsetX.value < -40f -> WarningAmber.copy(alpha = 0.85f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (offsetX.value > 0) Arrangement.Start else Arrangement.End
        ) {
            if (offsetX.value > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Swipe to Complete",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mark Completed",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            } else if (offsetX.value < 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Mark Incomplete",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Swipe to Reopen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Foreground Card that responds to horizontal drag
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(task.id, enableSwipeGesture) {
                    if (enableSwipeGesture) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (offsetX.value > swipeThreshold) {
                                        // Swiped Right -> Mark Completed
                                        onSwipeComplete?.invoke() ?: onToggleComplete()
                                    } else if (offsetX.value < -swipeThreshold) {
                                        // Swiped Left -> Mark Incomplete
                                        onSwipeIncomplete?.invoke() ?: onToggleComplete()
                                    }
                                    offsetX.animateTo(0f, spring())
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                scope.launch {
                                    val newOffset = (offsetX.value + dragAmount).coerceIn(-300f, 300f)
                                    offsetX.snapTo(newOffset)
                                }
                            }
                        )
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCompleted) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(cardBorderColor))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Top Row: Title, Priority & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (task.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Priority Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(priorityColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = task.priority.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = priorityColor,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // TIME REMAINING COUNTDOWN BADGE (Prominently displayed)
                if (task.deadlineMillis != null) {
                    val remainingBadgeBg = when {
                        isCompleted -> MaterialTheme.colorScheme.surfaceVariant
                        isOverdue -> UrgentRed.copy(alpha = 0.15f)
                        isDueToday -> WarningAmber.copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    }
                    val remainingTextColor = when {
                        isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                        isOverdue -> UrgentRed
                        isDueToday -> WarningAmber
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(remainingBadgeBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = remainingTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = timeRemaining,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = remainingTextColor,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Metadata & Actions Row: Swipe hint, Date, and actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (task.deadlineMillis != null) {
                            val formattedDate = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(task.deadlineMillis)
                            Text(
                                text = "Deadline: $formattedDate",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (task.sourceEmailSubject != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "From Email",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Email",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Quick Actions
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Swipe tip badge
                        Text(
                            text = if (isCompleted) "Swipe ← undo" else "Swipe → done",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        IconButton(
                            onClick = onReschedule,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("reschedule_task_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = "Reschedule",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("delete_task_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
