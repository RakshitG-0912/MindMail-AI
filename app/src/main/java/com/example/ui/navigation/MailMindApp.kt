package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EmailItem
import com.example.ui.screens.calendar.CalendarScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.inbox.EmailDetailDialog
import com.example.ui.screens.inbox.InboxScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.tasks.CreateTaskDialog
import com.example.ui.screens.tasks.TasksScreen
import com.example.ui.screens.voice.VoiceAssistantScreen
import com.example.viewmodel.MailMindViewModel

import androidx.compose.material.icons.filled.Devices
import com.example.ui.components.SmartAlarmDialog
import com.example.ui.screens.devices.DevicesScreen

enum class MailMindScreen(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    INBOX("Inbox", Icons.Default.Email),
    TASKS("Tasks", Icons.Default.CheckCircle),
    CALENDAR("Calendar", Icons.Default.CalendarMonth),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MailMindApp(
    viewModel: MailMindViewModel,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf(MailMindScreen.DASHBOARD) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }

    val selectedEmailForDetail by viewModel.selectedEmailForDetail.collectAsState()
    val currentAiAnalysis by viewModel.currentAiAnalysis.collectAsState()
    val isAnalyzingEmail by viewModel.isAnalyzingEmail.collectAsState()
    val duplicateWarning by viewModel.duplicateWarning.collectAsState()
    val activeAlarmReminder by viewModel.activeAlarmReminder.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()

    // Handle back button when not on dashboard
    BackHandler(enabled = currentScreen != MailMindScreen.DASHBOARD) {
        currentScreen = MailMindScreen.DASHBOARD
    }

    // Wire voice assistant navigation requests to app screen state
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.onNavigateRequested = { target ->
            when (target) {
                "INBOX" -> currentScreen = MailMindScreen.INBOX
                "TASKS" -> currentScreen = MailMindScreen.TASKS
                "CALENDAR" -> currentScreen = MailMindScreen.CALENDAR
                "SETTINGS" -> currentScreen = MailMindScreen.SETTINGS
                else -> currentScreen = MailMindScreen.DASHBOARD
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("mailmind_bottom_navigation")
            ) {
                MailMindScreen.values().forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                MailMindScreen.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToInbox = { currentScreen = MailMindScreen.INBOX },
                    onNavigateToTasks = { currentScreen = MailMindScreen.TASKS },
                    onOpenEmailDetail = { email -> viewModel.openEmailDetail(email) },
                    onOpenCreateTask = { showCreateTaskDialog = true }
                )
                MailMindScreen.INBOX -> InboxScreen(
                    viewModel = viewModel,
                    onOpenEmailDetail = { email -> viewModel.openEmailDetail(email) }
                )
                MailMindScreen.TASKS -> TasksScreen(
                    viewModel = viewModel,
                    onOpenCreateTask = { showCreateTaskDialog = true }
                )
                MailMindScreen.CALENDAR -> CalendarScreen(
                    viewModel = viewModel
                )
                MailMindScreen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel
                )
            }
        }

        // Email Detail Dialog with AI Summary, Task Conversion, and Gmail Actions
        if (selectedEmailForDetail != null) {
            EmailDetailDialog(
                email = selectedEmailForDetail!!,
                analysis = currentAiAnalysis,
                isAnalyzing = isAnalyzingEmail,
                onDismiss = { viewModel.closeEmailDetail() },
                onCreateTask = { extractedTask, confirmedDeadline ->
                    viewModel.createTaskFromExtracted(extractedTask, selectedEmailForDetail, confirmedDeadline)
                },
                onAnalyzeRequested = { viewModel.analyzeEmail(selectedEmailForDetail!!) },
                onStarToggle = { isStarred ->
                    viewModel.toggleStarEmail(selectedEmailForDetail!!.id, isStarred)
                },
                onArchive = {
                    viewModel.archiveEmail(selectedEmailForDetail!!.id)
                },
                onTrash = {
                    viewModel.trashEmail(selectedEmailForDetail!!.id)
                },
                onDeletePermanently = {
                    viewModel.deleteEmailPermanently(selectedEmailForDetail!!.id)
                },
                onSendReply = { replyText ->
                    val email = selectedEmailForDetail!!
                    val staged = com.example.ai.AiActionFramework.stageActionForConfirmation(
                        toolName = com.example.model.ToolName.SEND_EMAIL,
                        description = "Send reply to ${email.senderEmail}",
                        spokenPrompt = "Send reply to ${email.senderEmail} with subject 'Re: ${email.subject}'?",
                        parameters = mapOf(
                            "to" to email.senderEmail,
                            "subject" to "Re: ${email.subject}",
                            "body" to replyText,
                            "threadId" to email.threadId
                        )
                    )
                    viewModel.confirmPendingAction()
                }
            )
        }

        // Manual Create Task Dialog
        if (showCreateTaskDialog) {
            CreateTaskDialog(
                duplicateWarning = duplicateWarning,
                onDismiss = {
                    showCreateTaskDialog = false
                    viewModel.dismissDuplicateWarning()
                },
                onSaveTask = { title, desc, deadline, priority, reminderLeadTime, cat ->
                    viewModel.createManualTask(title, desc, deadline, priority, reminderLeadTime, cat)
                }
            )
        }

        // Real-Time Smart Alarm Screen with Dismiss & Snooze
        if (activeAlarmReminder != null) {
            val relatedTask = allTasks.find { it.id == activeAlarmReminder!!.taskId }
            SmartAlarmDialog(
                reminder = activeAlarmReminder!!,
                task = relatedTask,
                onDismiss = { viewModel.dismissActiveAlarm() },
                onCompleteTask = { taskId ->
                    viewModel.toggleTaskComplete(taskId)
                    viewModel.dismissActiveAlarm()
                },
                onSnooze = { minutes ->
                    viewModel.snoozeReminder(activeAlarmReminder!!.id, minutes)
                    viewModel.dismissActiveAlarm()
                }
            )
        }
    }
}
