package com.example.ui.screens.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AiEmailAnalysis
import com.example.model.EmailItem
import com.example.model.ExtractedTask
import com.example.model.TaskPriority
import com.example.ui.theme.UrgentRed
import com.example.ui.theme.WarningAmber
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.OutlinedTextField
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EmailDetailDialog(
    email: EmailItem,
    analysis: AiEmailAnalysis?,
    isAnalyzing: Boolean,
    onDismiss: () -> Unit,
    onCreateTask: (ExtractedTask, Long?) -> Unit,
    onAnalyzeRequested: () -> Unit,
    onStarToggle: ((Boolean) -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onTrash: (() -> Unit)? = null,
    onDeletePermanently: (() -> Unit)? = null,
    onSendReply: ((String) -> Unit)? = null
) {
    var selectedConfirmedDeadline by remember { mutableStateOf<Long?>(null) }
    var showDatePickerConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isPermanentDeleteMode by remember { mutableStateOf(false) }
    var showReplyComposer by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("Thank you for reaching out. I have reviewed the details and will take action accordingly.") }
    var showSendConfirmDialog by remember { mutableStateOf(false) }
    var isStarred by remember { mutableStateOf(email.isStarred) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("email_detail_modal"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Actions and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Email Intelligence",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Action Icons: Star, Archive, Trash, Close
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {
                            isStarred = !isStarred
                            onStarToggle?.invoke(isStarred)
                        }) {
                            Icon(
                                imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Star Email",
                                tint = if (isStarred) Color(0xFFD4A373) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { onArchive?.invoke() }) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = "Archive Email",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Email",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_email_detail_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Delete Confirmation Dialog
                if (showDeleteConfirmDialog) {
                    Dialog(onDismissRequest = { showDeleteConfirmDialog = false }) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (isPermanentDeleteMode) "⚠️ Permanent Deletion Warning" else "Confirm Delete Email",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isPermanentDeleteMode)
                                        "Warning: This will permanently remove '${email.subject}' from Gmail. This operation is irreversible. Are you sure you want to proceed?"
                                    else
                                        "Are you sure you want to move '${email.subject}' to trash?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (isPermanentDeleteMode) {
                                                onDeletePermanently?.invoke()
                                            } else {
                                                onTrash?.invoke()
                                            }
                                            showDeleteConfirmDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isPermanentDeleteMode) "Permanently Delete" else "Move to Trash", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = {
                                            isPermanentDeleteMode = !isPermanentDeleteMode
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isPermanentDeleteMode) "Switch to Trash" else "Make Permanent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }

                // Send Reply Confirmation Dialog
                if (showSendConfirmDialog) {
                    Dialog(onDismissRequest = { showSendConfirmDialog = false }) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Confirm Send Email",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Recipient: ${email.senderEmail}\nSubject: Re: ${email.subject}\n\nBody Preview:\n\"$replyText\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onSendReply?.invoke(replyText)
                                            showSendConfirmDialog = false
                                            showReplyComposer = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Confirm & Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { showSendConfirmDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Email Subject & Sender info
                    Text(
                        text = email.subject,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "From: ${email.senderName} (${email.senderEmail})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val formattedDate = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
                                .format(email.receivedTimestamp)
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Category Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(email.category.badgeColorHex).copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = email.category.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(email.category.badgeColorHex)
                            )
                        }
                    }

                    // Attachments preview if present
                    if (email.hasAttachments && email.attachmentNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Attachments: ${email.attachmentNames.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // AI INTELLIGENCE SUMMARY SECTION
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "MailMind AI Summary & Actions",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (analysis != null) {
                                Text(
                                    text = analysis.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Extracted Tasks List
                                if (analysis.extractedTasks.isNotEmpty()) {
                                    Text(
                                        text = "Actionable Tasks Identified:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    analysis.extractedTasks.forEach { task ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = task.title,
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(
                                                                when (task.priority) {
                                                                    TaskPriority.URGENT -> UrgentRed.copy(alpha = 0.2f)
                                                                    TaskPriority.HIGH -> WarningAmber.copy(alpha = 0.2f)
                                                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                                }
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = task.priority.displayName,
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = when (task.priority) {
                                                                TaskPriority.URGENT -> UrgentRed
                                                                TaskPriority.HIGH -> WarningAmber
                                                                else -> MaterialTheme.colorScheme.primary
                                                            },
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }

                                                if (task.deadlineText != null) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Deadline: ${task.deadlineText}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                // Ambiguous Date confirmation warning
                                                if (task.isDateAmbiguous) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(WarningAmber.copy(alpha = 0.15f))
                                                            .padding(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.WarningAmber,
                                                            contentDescription = null,
                                                            tint = WarningAmber,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = task.ambiguityReason ?: "Date is ambiguous. Please select confirmed deadline.",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = WarningAmber
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    // Quick date confirmation chips
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        val now = System.currentTimeMillis()
                                                        Button(
                                                            onClick = {
                                                                selectedConfirmedDeadline = now + (24 * 3600 * 1000L)
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = if (selectedConfirmedDeadline != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                            )
                                                        ) {
                                                            Text("Tomorrow 5 PM", fontSize = 11.sp)
                                                        }
                                                        Button(
                                                            onClick = {
                                                                selectedConfirmedDeadline = now + (3 * 24 * 3600 * 1000L)
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                            )
                                                        ) {
                                                            Text("In 3 Days", fontSize = 11.sp)
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))

                                                // 1-Tap Convert to Task button
                                                Button(
                                                    onClick = {
                                                        onCreateTask(task, selectedConfirmedDeadline)
                                                        onDismiss()
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("create_task_from_email_button"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Convert to Actionable Task",
                                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (!isAnalyzing) {
                                Text(
                                    text = "Ready to analyze this email for deadlines, priorities, and actionable tasks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onAnalyzeRequested,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Run MailMind AI Analysis")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Original Email Body
                    Text(
                        text = "Original Message",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = email.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}
