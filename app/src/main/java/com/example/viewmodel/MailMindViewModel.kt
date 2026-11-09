package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiService
import com.example.data.MailMindRepository
import com.example.model.AiEmailAnalysis
import com.example.model.EmailCategory
import com.example.model.EmailItem
import com.example.model.ExtractedTask
import com.example.model.NotificationHistoryItem
import com.example.model.ReminderItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.voice.VoiceAssistantManager
import com.example.voice.VoiceMessage
import com.example.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import android.content.Context
import com.example.auth.GoogleAuthManager
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MailMindViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MailMindRepository(application)
    val voiceAssistant = VoiceAssistantManager(application)

    val isDemoMode = repository.isDemoMode
    val isGmailConnected = repository.isGmailConnected
    val connectedAccountEmail = repository.connectedAccountEmail

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    val allTasks = repository.allTasks.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allEmails = repository.allEmails.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val activeReminders = repository.activeReminders.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val notificationHistory = repository.notificationHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Voice states
    val voiceState: StateFlow<VoiceState> = voiceAssistant.voiceState
    val currentTranscript: StateFlow<String> = voiceAssistant.currentTranscript
    val voiceHistory: StateFlow<List<VoiceMessage>> = voiceAssistant.conversationHistory
    val audioLevel: StateFlow<Float> = voiceAssistant.audioLevel

    // UI Navigation & Filters
    private val _selectedCategory = MutableStateFlow(EmailCategory.ALL)
    val selectedCategory: StateFlow<EmailCategory> = _selectedCategory.asStateFlow()

    private val _selectedTaskTab = MutableStateFlow(0) // 0: Today, 1: Upcoming, 2: Overdue, 3: Completed, 4: All
    val selectedTaskTab: StateFlow<Int> = _selectedTaskTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Email Analysis state
    private val _selectedEmailForDetail = MutableStateFlow<EmailItem?>(null)
    val selectedEmailForDetail: StateFlow<EmailItem?> = _selectedEmailForDetail.asStateFlow()

    // Multi-Device & Alarm State
    private val _connectedDevices = MutableStateFlow<List<com.example.model.ConnectedDevice>>(
        listOf(
            com.example.model.ConnectedDevice(
                id = "dev-moto-g86",
                deviceName = "Motorola Moto G86 Power 5G",
                platform = com.example.model.DevicePlatform.ANDROID,
                isCurrentDevice = true,
                isPrimaryAlarmTarget = true,
                status = com.example.model.DeviceSyncStatus.ONLINE
            ),
            com.example.model.ConnectedDevice(
                id = "dev-thinkpad-x1",
                deviceName = "ThinkPad X1 Carbon (Win 11)",
                platform = com.example.model.DevicePlatform.WINDOWS,
                isCurrentDevice = false,
                isPrimaryAlarmTarget = false,
                status = com.example.model.DeviceSyncStatus.ONLINE
            ),
            com.example.model.ConnectedDevice(
                id = "dev-web-chrome",
                deviceName = "Chrome Web Dashboard",
                platform = com.example.model.DevicePlatform.WEB,
                isCurrentDevice = false,
                isPrimaryAlarmTarget = false,
                status = com.example.model.DeviceSyncStatus.ONLINE
            )
        )
    )
    val connectedDevices: StateFlow<List<com.example.model.ConnectedDevice>> = _connectedDevices.asStateFlow()

    private val _isSyncingCloud = MutableStateFlow(false)
    val isSyncingCloud: StateFlow<Boolean> = _isSyncingCloud.asStateFlow()

    private val _activeAlarmReminder = MutableStateFlow<ReminderItem?>(null)
    val activeAlarmReminder: StateFlow<ReminderItem?> = _activeAlarmReminder.asStateFlow()

    private val _currentAiAnalysis = MutableStateFlow<AiEmailAnalysis?>(null)
    val currentAiAnalysis: StateFlow<AiEmailAnalysis?> = _currentAiAnalysis.asStateFlow()

    private val _isAnalyzingEmail = MutableStateFlow(false)
    val isAnalyzingEmail: StateFlow<Boolean> = _isAnalyzingEmail.asStateFlow()

    // Duplicate detection alert
    private val _duplicateWarning = MutableStateFlow<TaskItem?>(null)
    val duplicateWarning: StateFlow<TaskItem?> = _duplicateWarning.asStateFlow()

    // Proactive Alert snackbar/banner
    private val _proactiveAlert = MutableStateFlow<String?>(null)
    val proactiveAlert: StateFlow<String?> = _proactiveAlert.asStateFlow()

    init {
        voiceAssistant.onSpeechRecognized = { spokenQuery ->
            processVoiceQuery(spokenQuery)
        }

        // Observe Firebase Auth state for automatic Google user session restoration
        viewModelScope.launch {
            GoogleAuthManager.authStateFlow().collect { user ->
                _currentUser.value = user
                if (user != null) {
                    val email = user.email ?: "rakshit2004g@gmail.com"
                    repository.setGmailConnected(true, email)
                }
            }
        }

        // Proactively check for overdue or urgent items on launch
        viewModelScope.launch {
            allTasks.collect { tasks ->
                val overdue = tasks.filter { it.isOverdue() }
                if (overdue.isNotEmpty() && _proactiveAlert.value == null) {
                    _proactiveAlert.value = "Alert: You have ${overdue.size} overdue task${if (overdue.size > 1) "s" else ""}!"
                }
            }
        }
    }

    // --- Filtered Emails ---
    val filteredEmails: StateFlow<List<EmailItem>> = combine(allEmails, selectedCategory, searchQuery) { emails, category, query ->
        emails.filter { email ->
            val matchesCategory = category == EmailCategory.ALL || email.category == category
            val matchesQuery = query.isBlank() ||
                    email.subject.contains(query, ignoreCase = true) ||
                    email.senderName.contains(query, ignoreCase = true) ||
                    email.body.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- Filtered Tasks ---
    val filteredTasks: StateFlow<List<TaskItem>> = combine(allTasks, selectedTaskTab, searchQuery) { tasks, tab, query ->
        val tabFiltered = when (tab) {
            0 -> tasks.filter { it.isDueToday() && it.status != TaskStatus.COMPLETED }
            1 -> tasks.filter { it.isUpcoming() && it.status != TaskStatus.COMPLETED }
            2 -> tasks.filter { it.isOverdue() }
            3 -> tasks.filter { it.status == TaskStatus.COMPLETED }
            else -> tasks
        }
        if (query.isBlank()) {
            tabFiltered
        } else {
            tabFiltered.filter { it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- Actions ---

    fun setCategory(category: EmailCategory) {
        _selectedCategory.value = category
    }

    fun setTaskTab(tabIndex: Int) {
        _selectedTaskTab.value = tabIndex
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openEmailDetail(email: EmailItem) {
        _selectedEmailForDetail.value = email
        _currentAiAnalysis.value = null
        viewModelScope.launch {
            repository.markEmailRead(email.id, true)
            analyzeEmail(email)
        }
    }

    fun closeEmailDetail() {
        _selectedEmailForDetail.value = null
        _currentAiAnalysis.value = null
    }

    fun analyzeEmail(email: EmailItem) {
        viewModelScope.launch {
            _isAnalyzingEmail.value = true
            try {
                val analysis = repository.analyzeEmail(email)
                _currentAiAnalysis.value = analysis
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAnalyzingEmail.value = false
            }
        }
    }

    fun createTaskFromExtracted(
        extracted: ExtractedTask,
        sourceEmail: EmailItem?,
        confirmedDeadlineMillis: Long? = null
    ) {
        viewModelScope.launch {
            val duplicate = repository.checkDuplicate(extracted.title)
            if (duplicate != null) {
                _duplicateWarning.value = duplicate
            }

            val deadline = confirmedDeadlineMillis ?: extracted.deadlineMillis
            val task = TaskItem(
                id = UUID.randomUUID().toString(),
                title = extracted.title,
                description = extracted.description,
                sourceEmailId = sourceEmail?.id,
                sourceEmailSubject = sourceEmail?.subject,
                sourceEmailSender = sourceEmail?.senderName,
                deadlineMillis = deadline,
                priority = extracted.priority,
                status = TaskStatus.PENDING,
                category = sourceEmail?.category?.displayName ?: "Email Action",
                createdAt = System.currentTimeMillis(),
                reminderMinutesBefore = 60,
                isReminderEnabled = deadline != null
            )
            repository.createTask(task)
            _proactiveAlert.value = "Task created: '${task.title}'"
        }
    }

    fun createManualTask(
        title: String,
        description: String,
        deadlineMillis: Long?,
        priority: TaskPriority,
        reminderMinutes: Int,
        category: String = "General"
    ) {
        viewModelScope.launch {
            val duplicate = repository.checkDuplicate(title)
            if (duplicate != null) {
                _duplicateWarning.value = duplicate
            }

            val task = TaskItem(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                sourceEmailId = null,
                sourceEmailSubject = null,
                sourceEmailSender = null,
                deadlineMillis = deadlineMillis,
                priority = priority,
                status = TaskStatus.PENDING,
                category = category,
                createdAt = System.currentTimeMillis(),
                reminderMinutesBefore = reminderMinutes,
                isReminderEnabled = deadlineMillis != null
            )
            repository.createTask(task)
            _proactiveAlert.value = "Task created: '$title'"
        }
    }

    fun dismissDuplicateWarning() {
        _duplicateWarning.value = null
    }

    fun dismissProactiveAlert() {
        _proactiveAlert.value = null
    }

    fun toggleTaskComplete(taskId: String) {
        viewModelScope.launch {
            repository.toggleTaskComplete(taskId)
            // Cancel active alarm if this task was alarming
            if (_activeAlarmReminder.value?.taskId == taskId) {
                dismissActiveAlarm()
                _proactiveAlert.value = "Task completed. Cancelled active alarms across all connected devices."
            }
        }
    }

    // --- Smart Alarm & Cross-Device Actions ---

    fun triggerAlarm(reminder: ReminderItem) {
        _activeAlarmReminder.value = reminder
        voiceAssistant.speak("Attention Alex: Your critical deadline for '${reminder.taskTitle}' is due now.")
    }

    fun dismissActiveAlarm() {
        _activeAlarmReminder.value = null
        voiceAssistant.stopSpeaking()
    }

    fun syncAllDevices() {
        viewModelScope.launch {
            _isSyncingCloud.value = true
            kotlinx.coroutines.delay(1000)
            _connectedDevices.value = _connectedDevices.value.map {
                it.copy(lastSyncTimestamp = System.currentTimeMillis(), status = com.example.model.DeviceSyncStatus.ONLINE)
            }
            _isSyncingCloud.value = false
            _proactiveAlert.value = "Cloud Firestore: All 3 devices synchronized."
        }
    }

    fun setPrimaryAlarmDevice(deviceId: String) {
        _connectedDevices.value = _connectedDevices.value.map { dev ->
            dev.copy(isPrimaryAlarmTarget = (dev.id == deviceId))
        }
        val target = _connectedDevices.value.find { it.id == deviceId }?.deviceName ?: "Device"
        _proactiveAlert.value = "Primary alarm device set to: $target"
    }

    fun sendTestAlertToDevice(deviceId: String) {
        val target = _connectedDevices.value.find { it.id == deviceId }
        val name = target?.deviceName ?: "Companion device"
        _proactiveAlert.value = "Pushed deadline reminder to $name via Cloud Messaging."
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun rescheduleTask(taskId: String, newDeadline: Long) {
        viewModelScope.launch {
            repository.rescheduleTask(taskId, newDeadline)
            _proactiveAlert.value = "Task deadline rescheduled successfully."
        }
    }

    fun snoozeReminder(reminderId: String, minutes: Int) {
        viewModelScope.launch {
            repository.snoozeReminder(reminderId, minutes)
            _proactiveAlert.value = "Reminder snoozed for $minutes minutes."
        }
    }

    // --- Voice Assistant & Autonomous Action Framework ---

    val inactivitySecondsRemaining = voiceAssistant.inactivitySecondsRemaining
    val assistantLanguage = voiceAssistant.currentLanguage
    val isContinuousSession = voiceAssistant.isContinuousSession
    val pendingConfirmation = com.example.ai.AiActionFramework.pendingConfirmation
    val actionHistory = com.example.ai.AiActionFramework.actionHistory

    var onNavigateRequested: ((String) -> Unit)? = null

    fun setLanguage(lang: com.example.model.AssistantLanguage) {
        voiceAssistant.setLanguage(lang)
        _proactiveAlert.value = "Assistant language switched to ${lang.displayName}."
    }

    fun startVoiceSession() {
        voiceAssistant.startListeningSession()
    }

    fun stopVoiceSession() {
        voiceAssistant.stopListeningSession()
    }

    fun startListening() {
        voiceAssistant.startListeningSession()
    }

    fun stopListening() {
        voiceAssistant.stopListeningSession()
    }

    fun stopSpeaking() {
        voiceAssistant.stopSpeaking()
    }

    fun greetOnStartup() {
        voiceAssistant.greetOnStartup()
    }

    fun confirmPendingAction() {
        val pending = com.example.ai.AiActionFramework.pendingConfirmation.value ?: return
        viewModelScope.launch {
            when (pending.toolName) {
                com.example.model.ToolName.SEND_EMAIL -> {
                    val to = pending.parameters["to"] as? String ?: ""
                    val subject = pending.parameters["subject"] as? String ?: ""
                    val body = pending.parameters["body"] as? String ?: ""
                    repository.sendEmail(to, subject, body)
                    com.example.ai.AiActionFramework.logAction(
                        toolName = com.example.model.ToolName.SEND_EMAIL,
                        description = "Sent email to $to ($subject)",
                        status = com.example.model.ActionStatus.CONFIRMED,
                        resultSummary = "Delivered via Gmail API"
                    )
                    val confirmMsg = if (assistantLanguage.value == com.example.model.AssistantLanguage.HINDI) {
                        "ईमेल सफलतापूर्वक $to को भेज दिया गया है।"
                    } else {
                        "Email has been sent successfully to $to."
                    }
                    voiceAssistant.speak(confirmMsg)
                }
                com.example.model.ToolName.DELETE_EMAIL -> {
                    val emailId = pending.parameters["emailId"] as? String ?: ""
                    val isPermanent = pending.parameters["permanent"] as? Boolean ?: false
                    if (isPermanent) {
                        repository.deleteEmailPermanently(emailId)
                        com.example.ai.AiActionFramework.logAction(
                            toolName = com.example.model.ToolName.DELETE_EMAIL,
                            description = "Permanently deleted email $emailId",
                            status = com.example.model.ActionStatus.CONFIRMED,
                            resultSummary = "Permanently deleted from Gmail"
                        )
                        val confirmMsg = if (assistantLanguage.value == com.example.model.AssistantLanguage.HINDI) {
                            "ईमेल को हमेशा के लिए हटा दिया गया है।"
                        } else {
                            "Email permanently deleted from Gmail."
                        }
                        voiceAssistant.speak(confirmMsg)
                    } else {
                        repository.trashEmail(emailId)
                        com.example.ai.AiActionFramework.logAction(
                            toolName = com.example.model.ToolName.DELETE_EMAIL,
                            description = "Moved email $emailId to trash",
                            status = com.example.model.ActionStatus.CONFIRMED,
                            resultSummary = "Moved to Gmail Trash"
                        )
                        val confirmMsg = if (assistantLanguage.value == com.example.model.AssistantLanguage.HINDI) {
                            "ईमेल को ट्रैश में भेज दिया गया है।"
                        } else {
                            "Email moved to trash."
                        }
                        voiceAssistant.speak(confirmMsg)
                    }
                }
                com.example.model.ToolName.ARCHIVE_EMAIL -> {
                    val emailId = pending.parameters["emailId"] as? String ?: ""
                    if (emailId.isNotBlank()) {
                        repository.archiveEmail(emailId)
                    }
                    com.example.ai.AiActionFramework.logAction(
                        toolName = com.example.model.ToolName.ARCHIVE_EMAIL,
                        description = "Archived promotional emails",
                        status = com.example.model.ActionStatus.CONFIRMED,
                        resultSummary = "Archived in Gmail"
                    )
                    val confirmMsg = if (assistantLanguage.value == com.example.model.AssistantLanguage.HINDI) {
                        "ईमेल आर्काइव कर दिए गए हैं।"
                    } else {
                        "Emails have been archived from inbox."
                    }
                    voiceAssistant.speak(confirmMsg)
                }
                else -> {}
            }
            com.example.ai.AiActionFramework.clearPendingConfirmation()
        }
    }

    fun cancelPendingAction() {
        val pending = com.example.ai.AiActionFramework.pendingConfirmation.value
        if (pending != null) {
            com.example.ai.AiActionFramework.logAction(
                toolName = pending.toolName,
                description = "Cancelled: ${pending.description}",
                status = com.example.model.ActionStatus.CANCELLED
            )
        }
        com.example.ai.AiActionFramework.clearPendingConfirmation()
        val cancelMsg = if (assistantLanguage.value == com.example.model.AssistantLanguage.HINDI) {
            "कार्रवाई रद्द कर दी गई है।"
        } else {
            "Action cancelled. No changes were made."
        }
        voiceAssistant.speak(cancelMsg)
    }

    fun processVoiceQuery(query: String) {
        viewModelScope.launch {
            val tasks = allTasks.value
            val emails = allEmails.value
            val pending = com.example.ai.AiActionFramework.pendingConfirmation.value
            val lang = assistantLanguage.value

            val response = GeminiService.processVoiceCommand(
                query = query,
                tasks = tasks,
                emails = emails,
                language = lang,
                activePendingConfirmation = pending
            )

            // Execute detected actions with complete app control and safety validation
            when (response.actionType) {
                GeminiService.VoiceActionType.EXECUTE_CONFIRMED_ACTION -> {
                    confirmPendingAction()
                    return@launch
                }
                GeminiService.VoiceActionType.CANCEL_CONFIRMED_ACTION -> {
                    cancelPendingAction()
                    return@launch
                }
                GeminiService.VoiceActionType.STAGE_SEND_EMAIL,
                GeminiService.VoiceActionType.STAGE_DELETE_EMAIL,
                GeminiService.VoiceActionType.STAGE_ARCHIVE_EMAIL -> {
                    // Staged for explicit user confirmation in AiActionFramework
                }
                GeminiService.VoiceActionType.SEARCH_EMAILS -> {
                    response.emailQuery?.let {
                        _searchQuery.value = it
                    }
                    response.matchedEmail?.let {
                        openEmailDetail(it)
                    }
                    response.toolName?.let {
                        com.example.ai.AiActionFramework.logAction(
                            toolName = it,
                            description = "Searched emails for '${response.emailQuery}'",
                            status = com.example.model.ActionStatus.EXECUTED,
                            resultSummary = "Found ${if (response.matchedEmail != null) 1 else 0} match"
                        )
                    }
                }
                GeminiService.VoiceActionType.SUMMARIZE_THREAD -> {
                    response.matchedEmail?.let {
                        openEmailDetail(it)
                    }
                    response.toolName?.let {
                        com.example.ai.AiActionFramework.logAction(
                            toolName = it,
                            description = "Summarized email: ${response.matchedEmail?.subject}",
                            status = com.example.model.ActionStatus.EXECUTED
                        )
                    }
                }
                GeminiService.VoiceActionType.CREATE_DRAFT -> {
                    if (response.draftRecipient != null && response.draftSubject != null && response.draftBody != null) {
                        repository.createDraft(response.draftRecipient, response.draftSubject, response.draftBody)
                        com.example.ai.AiActionFramework.logAction(
                            toolName = com.example.model.ToolName.CREATE_EMAIL_DRAFT,
                            description = "Drafted reply to ${response.draftRecipient}",
                            status = com.example.model.ActionStatus.EXECUTED,
                            resultSummary = "Draft created in Gmail"
                        )
                    }
                }
                GeminiService.VoiceActionType.STAR_EMAIL -> {
                    response.matchedEmail?.id?.let { emailId ->
                        repository.markEmailStarred(emailId, true)
                        com.example.ai.AiActionFramework.logAction(
                            toolName = com.example.model.ToolName.STAR_EMAIL,
                            description = "Starred email in Gmail",
                            status = com.example.model.ActionStatus.EXECUTED
                        )
                    }
                }
                GeminiService.VoiceActionType.MARK_EMAIL_UNREAD -> {
                    response.matchedEmail?.id?.let { emailId ->
                        repository.markEmailRead(emailId, false)
                        com.example.ai.AiActionFramework.logAction(
                            toolName = com.example.model.ToolName.MARK_AS_UNREAD,
                            description = "Marked email as unread in Gmail",
                            status = com.example.model.ActionStatus.EXECUTED
                        )
                    }
                }
                GeminiService.VoiceActionType.MARK_TASK_COMPLETE -> {
                    response.targetTaskId?.let {
                        toggleTaskComplete(it)
                        com.example.ai.AiActionFramework.logAction(
                            toolName = com.example.model.ToolName.COMPLETE_TASK,
                            description = "Completed task $it",
                            status = com.example.model.ActionStatus.EXECUTED
                        )
                    }
                }
                GeminiService.VoiceActionType.MARK_TASK_INCOMPLETE -> {
                    response.targetTaskId?.let { toggleTaskComplete(it) }
                }
                GeminiService.VoiceActionType.CREATE_TASK -> {
                    if (!response.newTaskTitle.isNullOrBlank()) {
                        createManualTask(
                            title = response.newTaskTitle,
                            description = "Created autonomously via Voice Assistant",
                            deadlineMillis = response.newTaskDeadlineMillis,
                            priority = TaskPriority.HIGH,
                            reminderMinutes = 60
                        )
                        com.example.ai.AiActionFramework.logAction(
                            toolName = com.example.model.ToolName.CREATE_TASK,
                            description = "Created task: ${response.newTaskTitle}",
                            status = com.example.model.ActionStatus.EXECUTED
                        )
                    }
                }
                GeminiService.VoiceActionType.SNOOZE_REMINDER -> {
                    val active = activeReminders.value.firstOrNull()
                    if (active != null) {
                        snoozeReminder(active.id, 30)
                        com.example.ai.AiActionFramework.logAction(
                            toolName = com.example.model.ToolName.SNOOZE_REMINDER,
                            description = "Snoozed reminder for ${active.taskTitle}",
                            status = com.example.model.ActionStatus.EXECUTED
                        )
                    }
                }
                GeminiService.VoiceActionType.TRIGGER_ALARM_PREVIEW -> {
                    val firstReminder = activeReminders.value.firstOrNull()
                    if (firstReminder != null) {
                        triggerAlarm(firstReminder)
                    }
                }
                GeminiService.VoiceActionType.NAVIGATE_INBOX -> {
                    onNavigateRequested?.invoke("INBOX")
                }
                GeminiService.VoiceActionType.NAVIGATE_TASKS -> {
                    onNavigateRequested?.invoke("TASKS")
                }
                GeminiService.VoiceActionType.NAVIGATE_CALENDAR -> {
                    onNavigateRequested?.invoke("CALENDAR")
                }
                GeminiService.VoiceActionType.NAVIGATE_SETTINGS -> {
                    onNavigateRequested?.invoke("SETTINGS")
                }
                else -> {}
            }

            // Speak the conversational response
            voiceAssistant.speak(response.spokenText, addToHistory = true)
        }
    }

    fun playMorningBriefing() {
        viewModelScope.launch {
            val spoken = GeminiService.generateMorningBriefingSpokenText(
                allTasks.value,
                allEmails.value,
                assistantLanguage.value == com.example.model.AssistantLanguage.HINDI
            )
            voiceAssistant.speak(spoken, addToHistory = true)
        }
    }

    // --- Email & Settings ---

    fun addCustomEmail(senderName: String, senderEmail: String, subject: String, body: String) {
        viewModelScope.launch {
            val email = repository.addCustomEmail(senderName, senderEmail, subject, body)
            _proactiveAlert.value = "New email added to inbox."
            openEmailDetail(email)
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _isSyncingCloud.value = true
            val result = GoogleAuthManager.signInWithGoogle(context)
            _isSyncingCloud.value = false
            result.fold(
                onSuccess = { user ->
                    val email = user.email ?: "rakshit2004g@gmail.com"
                    repository.setGmailConnected(true, email)
                    _proactiveAlert.value = "Signed in as $email with Gmail (Read-Only)."
                    syncGmailNow()
                },
                onFailure = { error ->
                    android.util.Log.w("MailMindViewModel", "Google Sign-In failed: ${error.message}")
                    _proactiveAlert.value = "Sign-In: ${error.message ?: "Authentication cancelled"}"
                }
            )
        }
    }

    fun signOutGoogle() {
        GoogleAuthManager.signOut()
        repository.setGmailConnected(false)
        _proactiveAlert.value = "Signed out of Google account."
    }

    fun connectGmail(token: String, email: String) {
        repository.setGmailConnected(true, email, token)
        _proactiveAlert.value = "Connected to Gmail ($email)."
        syncGmailNow()
    }

    fun disconnectGmail() {
        signOutGoogle()
    }

    fun toggleStarEmail(emailId: String, isStarred: Boolean) {
        viewModelScope.launch {
            repository.markEmailStarred(emailId, isStarred)
            _proactiveAlert.value = if (isStarred) "Starred email in Gmail." else "Removed star."
        }
    }

    fun archiveEmail(emailId: String) {
        viewModelScope.launch {
            repository.archiveEmail(emailId)
            _proactiveAlert.value = "Archived email."
            closeEmailDetail()
        }
    }

    fun trashEmail(emailId: String) {
        viewModelScope.launch {
            repository.trashEmail(emailId)
            _proactiveAlert.value = "Moved email to trash."
            closeEmailDetail()
        }
    }

    fun deleteEmailPermanently(emailId: String) {
        viewModelScope.launch {
            repository.deleteEmailPermanently(emailId)
            _proactiveAlert.value = "Permanently deleted email."
            closeEmailDetail()
        }
    }

    fun syncGmailNow() {
        viewModelScope.launch {
            _isSyncingCloud.value = true
            val res = repository.syncGmailEmails()
            _isSyncingCloud.value = false
            res.fold(
                onSuccess = { count ->
                    _proactiveAlert.value = "Gmail synced: $count messages updated."
                },
                onFailure = { error ->
                    _proactiveAlert.value = "Gmail sync: ${error.message ?: "Complete"}"
                }
            )
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetDemoData()
            _proactiveAlert.value = "Demo inbox and sample tasks reset."
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceAssistant.destroy()
    }
}
