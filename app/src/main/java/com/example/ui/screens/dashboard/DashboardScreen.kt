package com.example.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EmailCategory
import com.example.model.EmailItem
import com.example.model.TaskItem
import com.example.model.TaskStatus
import com.example.ui.components.EmailCard
import com.example.ui.components.TaskCard
import com.example.ui.components.VoicePulseVisualizer
import com.example.ui.theme.UrgentRed
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.MailMindViewModel
import com.example.voice.VoiceState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MailMindViewModel,
    onNavigateToInbox: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onOpenEmailDetail: (EmailItem) -> Unit,
    onOpenCreateTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.allTasks.collectAsState()
    val emails by viewModel.allEmails.collectAsState()
    val activeReminders by viewModel.activeReminders.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val proactiveAlert by viewModel.proactiveAlert.collectAsState()

    val voiceState by viewModel.voiceState.collectAsState()
    val currentTranscript by viewModel.currentTranscript.collectAsState()
    val voiceHistory by viewModel.voiceHistory.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val isContinuousSession by viewModel.isContinuousSession.collectAsState()
    val inactivitySecondsRemaining by viewModel.inactivitySecondsRemaining.collectAsState()
    val assistantLanguage by viewModel.assistantLanguage.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val actionHistory by viewModel.actionHistory.collectAsState()

    var frontPageChatText by remember { mutableStateOf("") }
    var showActionHistoryDialog by remember { mutableStateOf(false) }

    val overdueTasks = tasks.filter { it.isOverdue() }
    val todayTasks = tasks.filter { it.isDueToday() && it.status != TaskStatus.COMPLETED }
    val upcomingTasks = tasks.filter { it.isUpcoming() && it.status != TaskStatus.COMPLETED }
    val importantEmails = emails.filter { !it.isRead && (it.category == EmailCategory.IMPORTANT || it.category == EmailCategory.ACTION_REQUIRED) }
    val nextReminder = activeReminders.minByOrNull { it.scheduledTimeMillis }

    val cal = Calendar.getInstance()
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val greeting = if (hour < 12) "Good morning" else if (hour < 17) "Good afternoon" else "Good evening"
    val dateFormatted = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(System.currentTimeMillis())

    val quickVoiceCommands = if (assistantLanguage == com.example.model.AssistantLanguage.HINDI) {
        listOf(
            "मेरे ज़रूरी ईमेल पढ़ो",
            "प्लेसमेंट सेल के ईमेल ढूँढो",
            "कल की क्या डेडलाइन हैं?",
            "असाइनमेंट सबमिट करने का कार्य बनाओ",
            "मॉर्निंग ब्रीफिंग दो",
            "प्रमोशनल ईमेल आर्काइव करो",
            "कैलेंडर अनुसूची खोलो"
        )
    } else {
        listOf(
            "Read important emails",
            "Placement cell emails",
            "Find exam schedule",
            "What deadlines tomorrow?",
            "Draft reply to meeting",
            "Submit assignment on Friday",
            "Archive promotions",
            "Give morning briefing"
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dashboard_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. HEADER: Text in ONE SINGLE LINE with the other icons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Single line text: Greeting & formatted date
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "$greeting, Alex",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• $dateFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Icons and Badges in the exact same horizontal line
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Demo / Connected Mode Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isGmailConnected) Color(0xFF608053).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isGmailConnected) "Gmail Active" else "Demo Mode",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isGmailConnected) Color(0xFF608053) else MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }

                        // Top Quick Mic Toggle Button
                        IconButton(
                            onClick = {
                                if (voiceState == VoiceState.LISTENING || isContinuousSession) {
                                    viewModel.stopVoiceSession()
                                } else {
                                    viewModel.startVoiceSession()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (voiceState == VoiceState.LISTENING) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = if (voiceState == VoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Voice Assistant",
                                tint = if (voiceState == VoiceState.LISTENING) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Proactive Alert Banner if present
            if (proactiveAlert != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = proactiveAlert ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { viewModel.dismissProactiveAlert() }, modifier = Modifier.size(24.dp)) {
                                Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 2. FRONT-PAGE AUTONOMOUS AI VOICE ASSISTANT (20s Inactivity Buffer & Autonomous Actions)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("front_page_voice_assistant_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (voiceState == VoiceState.LISTENING) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Top bar: Title + Language Switcher + History Button
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
                                Column {
                                    Text(
                                        text = "MailMind Autonomous AI",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (voiceState == VoiceState.LISTENING) "Listening (${inactivitySecondsRemaining}s buffer)"
                                        else if (voiceState == VoiceState.SPEAKING) "Speaking response..."
                                        else "Autonomous Voice Assistant • Hands-Free",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (voiceState == VoiceState.LISTENING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Language switch button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            val nextLang = if (assistantLanguage == com.example.model.AssistantLanguage.ENGLISH) {
                                                com.example.model.AssistantLanguage.HINDI
                                            } else {
                                                com.example.model.AssistantLanguage.ENGLISH
                                            }
                                            viewModel.setLanguage(nextLang)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (assistantLanguage == com.example.model.AssistantLanguage.HINDI) "🇮🇳 हिन्दी" else "🇺🇸 EN",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp
                                    )
                                }

                                // Action history button
                                IconButton(
                                    onClick = { showActionHistoryDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Action History",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.LISTENING) {
                                    IconButton(
                                        onClick = {
                                            viewModel.stopSpeaking()
                                            viewModel.stopVoiceSession()
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // SENSITIVE PENDING CONFIRMATION BANNER
                        if (pendingConfirmation != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Confirmation Required (${pendingConfirmation!!.toolName.displayName})",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = pendingConfirmation!!.spokenPrompt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.confirmPendingAction() },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Text("Confirm (Say Yes)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { viewModel.cancelPendingAction() },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Text("Cancel (Say No)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Mic with Visualizer button row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier.size(104.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                VoicePulseVisualizer(
                                    voiceState = voiceState,
                                    audioLevel = audioLevel,
                                    size = 104.dp
                                )

                                Box(
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (voiceState == VoiceState.LISTENING) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                                        )
                                        .clickable {
                                            if (voiceState == VoiceState.LISTENING || isContinuousSession) {
                                                viewModel.stopVoiceSession()
                                            } else {
                                                viewModel.startVoiceSession()
                                            }
                                        }
                                        .testTag("constant_listening_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (voiceState == VoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = "Voice Assistant Session",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        // Live transcript & inactivity timer pill
                        if (voiceState == VoiceState.LISTENING) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Inactivity Timer: ${inactivitySecondsRemaining}s",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = currentTranscript.isNotBlank()) {
                            Text(
                                text = "Recognized: \"$currentTranscript\"",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Last Voice Message from Assistant if available
                        val lastAssistantMsg = voiceHistory.filter { !it.isUser }.lastOrNull()
                        if (lastAssistantMsg != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = lastAssistantMsg.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick voice command chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickVoiceCommands.forEach { cmd ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .clickable { viewModel.processVoiceQuery(cmd) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = cmd,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Text Chat Input row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = frontPageChatText,
                                onValueChange = { frontPageChatText = it },
                                placeholder = {
                                    Text(
                                        if (assistantLanguage == com.example.model.AssistantLanguage.HINDI) "या आदेश टाइप करें..."
                                        else "Or type command / query...",
                                        fontSize = 12.sp
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("front_page_chat_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (frontPageChatText.isNotBlank()) {
                                        viewModel.processVoiceQuery(frontPageChatText)
                                        frontPageChatText = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                enabled = frontPageChatText.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // METRIC CARDS GRID (2x2)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Overdue",
                        count = overdueTasks.size.toString(),
                        subtitle = "Needs action",
                        icon = Icons.Default.Warning,
                        accentColor = UrgentRed,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToTasks() }
                    )

                    MetricCard(
                        title = "Due Today",
                        count = todayTasks.size.toString(),
                        subtitle = "Today's deadlines",
                        icon = Icons.Default.Schedule,
                        accentColor = WarningAmber,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToTasks() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Upcoming",
                        count = upcomingTasks.size.toString(),
                        subtitle = "Scheduled",
                        icon = Icons.Default.Alarm,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToTasks() }
                    )

                    MetricCard(
                        title = "Action Inbox",
                        count = importantEmails.size.toString(),
                        subtitle = "Pending review",
                        icon = Icons.Default.Email,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToInbox() }
                    )
                }
            }

            // NEXT SCHEDULED REMINDER CARD
            if (nextReminder != null) {
                item {
                    val reminderTimeStr = SimpleDateFormat("EEE, h:mm a", Locale.getDefault()).format(nextReminder.scheduledTimeMillis)
                    val relatedTask = tasks.find { it.id == nextReminder.taskId }
                    val timeLeftStr = relatedTask?.getTimeRemainingText() ?: ""

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Next Proactive Alarm: $reminderTimeStr",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = nextReminder.taskTitle,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (timeLeftStr.isNotBlank()) {
                                        Text(
                                            text = timeLeftStr,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { viewModel.triggerAlarm(nextReminder) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("preview_alarm_button")
                                ) {
                                    Text("Preview Alarm", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { viewModel.snoozeReminder(nextReminder.id, 30) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Snooze 30m", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            // 3. TODAY'S ACTION ITEMS SECTION (WITH SWIPE GESTURES: Right to Complete, Left to Undo)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Today's Action Items (${todayTasks.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Swipe right to complete • Swipe left to undo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigateToTasks() }
                    )
                }
            }

            if (todayTasks.isEmpty()) {
                item {
                    Text(
                        text = "No pending tasks due today. All caught up!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(todayTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggleComplete = { viewModel.toggleTaskComplete(task.id) },
                        onDelete = { viewModel.deleteTask(task.id) },
                        onReschedule = { onNavigateToTasks() },
                        enableSwipeGesture = true,
                        onSwipeComplete = { viewModel.toggleTaskComplete(task.id) },
                        onSwipeIncomplete = { viewModel.toggleTaskComplete(task.id) }
                    )
                }
            }

            // CRITICAL EMAILS SECTION
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Important Unread Emails (${importantEmails.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Open Inbox",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigateToInbox() }
                    )
                }
            }

            items(importantEmails.take(2), key = { it.id }) { email ->
                EmailCard(
                    email = email,
                    onClick = { onOpenEmailDetail(email) }
                )
            }
        }

        // Quick Add Task Floating Action Button
        FloatingActionButton(
            onClick = onOpenCreateTask,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(56.dp)
                .testTag("quick_add_task_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(26.dp))
        }

        if (showActionHistoryDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showActionHistoryDialog = false }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Autonomous Action History",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { showActionHistoryDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        Text(
                            text = "Audit trail of tools executed by MailMind AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (actionHistory.isEmpty()) {
                            Text(
                                text = "No autonomous actions executed yet in this session.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.heightIn(max = 350.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(actionHistory) { item ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = item.toolName,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = item.status.name,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (item.status == com.example.model.ActionStatus.CONFIRMED || item.status == com.example.model.ActionStatus.EXECUTED)
                                                        Color(0xFF608053) else MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Text(
                                                text = item.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (!item.resultSummary.isNullOrBlank()) {
                                                Text(
                                                    text = item.resultSummary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    count: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = count,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = accentColor
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
