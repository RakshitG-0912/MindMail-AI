package com.example.data

import android.content.Context
import com.example.ai.GeminiService
import com.example.data.local.EmailEntity
import com.example.data.local.MailMindDatabase
import com.example.data.local.NotificationHistoryEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.TaskEntity
import com.example.model.AiEmailAnalysis
import com.example.model.EmailCategory
import com.example.model.EmailItem
import com.example.model.NotificationHistoryItem
import com.example.model.ReminderItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.reminder.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MailMindRepository(
    private val context: Context,
    private val database: MailMindDatabase = MailMindDatabase.getDatabase(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val dao = database.mailMindDao()

    private val _isDemoMode = MutableStateFlow(true)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _isGmailConnected = MutableStateFlow(false)
    val isGmailConnected: StateFlow<Boolean> = _isGmailConnected.asStateFlow()

    private val _connectedAccountEmail = MutableStateFlow("alex.morgan.tech@gmail.com")
    val connectedAccountEmail: StateFlow<String> = _connectedAccountEmail.asStateFlow()

    val allTasks: Flow<List<TaskItem>> = dao.getAllTasks().map { list -> list.map { it.toTaskItem() } }
    val pendingTasks: Flow<List<TaskItem>> = dao.getPendingTasks().map { list -> list.map { it.toTaskItem() } }
    val completedTasks: Flow<List<TaskItem>> = dao.getCompletedTasks().map { list -> list.map { it.toTaskItem() } }

    val allEmails: Flow<List<EmailItem>> = dao.getAllEmails().map { list -> list.map { it.toEmailItem() } }
    val activeReminders: Flow<List<ReminderItem>> = dao.getActiveReminders().map { list -> list.map { it.toReminderItem() } }
    val notificationHistory: Flow<List<NotificationHistoryItem>> = dao.getNotificationHistory().map { list -> list.map { it.toNotificationHistoryItem() } }

    init {
        scope.launch {
            // Seed database if empty
            val existingEmails = dao.getAllEmails().first()
            if (existingEmails.isEmpty()) {
                seedInitialData()
            }
        }
    }

    suspend fun seedInitialData() = withContext(Dispatchers.IO) {
        val initialEmails = DemoData.generateInitialEmails().map { EmailEntity.fromEmailItem(it) }
        val initialTasks = DemoData.generateInitialTasks().map { TaskEntity.fromTaskItem(it) }
        dao.insertEmails(initialEmails)
        dao.insertTasks(initialTasks)

        // Seed sample reminder
        val firstTask = initialTasks.firstOrNull()
        if (firstTask != null && firstTask.deadlineMillis != null) {
            val reminder = ReminderItem(
                id = UUID.randomUUID().toString(),
                taskId = firstTask.id,
                taskTitle = firstTask.title,
                scheduledTimeMillis = firstTask.deadlineMillis - (firstTask.reminderMinutesBefore * 60 * 1000L),
                leadTimeMinutes = firstTask.reminderMinutesBefore
            )
            dao.insertReminder(ReminderEntity.fromReminderItem(reminder))
        }

        // Seed sample initial history
        dao.insertNotificationHistory(
            NotificationHistoryEntity(
                id = UUID.randomUUID().toString(),
                title = "Welcome to MailMind AI",
                message = "AI Email Intelligence and Proactive Deadline Engine is active.",
                timestamp = System.currentTimeMillis() - 3600000,
                taskId = null,
                type = "SYSTEM"
            )
        )
    }

    suspend fun resetDemoData() = withContext(Dispatchers.IO) {
        dao.clearAllEmails()
        dao.clearAllTasks()
        seedInitialData()
    }

    // --- Task Operations ---

    suspend fun checkDuplicate(title: String): TaskItem? = withContext(Dispatchers.IO) {
        dao.findDuplicateTask(title)?.toTaskItem()
    }

    suspend fun createTask(task: TaskItem): String = withContext(Dispatchers.IO) {
        dao.insertTask(TaskEntity.fromTaskItem(task))

        // Schedule proactive alarms for 1 day, 6 hours, and 1 hour remaining
        if (task.isReminderEnabled && task.deadlineMillis != null) {
            val now = System.currentTimeMillis()
            val leadTimes = listOf(
                1440 to (task.deadlineMillis - 24 * 3600 * 1000L), // 1 day
                360 to (task.deadlineMillis - 6 * 3600 * 1000L),   // 6 hours
                60 to (task.deadlineMillis - 3600 * 1000L)         // 1 hour
            )
            leadTimes.forEach { (leadMin, schedTime) ->
                if (schedTime > now) {
                    val reminder = ReminderItem(
                        id = UUID.randomUUID().toString(),
                        taskId = task.id,
                        taskTitle = task.title,
                        scheduledTimeMillis = schedTime,
                        leadTimeMinutes = leadMin
                    )
                    dao.insertReminder(ReminderEntity.fromReminderItem(reminder))
                }
            }
        }

        task.id
    }

    suspend fun updateTask(task: TaskItem) = withContext(Dispatchers.IO) {
        dao.updateTask(TaskEntity.fromTaskItem(task))
    }

    suspend fun toggleTaskComplete(taskId: String) = withContext(Dispatchers.IO) {
        val task = dao.getTaskById(taskId)?.toTaskItem() ?: return@withContext
        val newStatus = if (task.status == TaskStatus.COMPLETED) TaskStatus.PENDING else TaskStatus.COMPLETED
        val completedAt = if (newStatus == TaskStatus.COMPLETED) System.currentTimeMillis() else null
        val updated = task.copy(status = newStatus, completedAt = completedAt)
        dao.updateTask(TaskEntity.fromTaskItem(updated))
    }

    suspend fun rescheduleTask(taskId: String, newDeadlineMillis: Long) = withContext(Dispatchers.IO) {
        val task = dao.getTaskById(taskId)?.toTaskItem() ?: return@withContext
        val updated = task.copy(deadlineMillis = newDeadlineMillis)
        dao.updateTask(TaskEntity.fromTaskItem(updated))

        // Update reminders for 1 day, 6 hours, and 1 hour
        dao.deleteRemindersForTask(taskId)
        if (updated.isReminderEnabled) {
            val now = System.currentTimeMillis()
            val leadTimes = listOf(
                1440 to (newDeadlineMillis - 24 * 3600 * 1000L),
                360 to (newDeadlineMillis - 6 * 3600 * 1000L),
                60 to (newDeadlineMillis - 3600 * 1000L)
            )
            leadTimes.forEach { (leadMin, schedTime) ->
                if (schedTime > now) {
                    val reminder = ReminderItem(
                        id = UUID.randomUUID().toString(),
                        taskId = taskId,
                        taskTitle = updated.title,
                        scheduledTimeMillis = schedTime,
                        leadTimeMinutes = leadMin
                    )
                    dao.insertReminder(ReminderEntity.fromReminderItem(reminder))
                }
            }
        }
    }

    suspend fun deleteTask(taskId: String) = withContext(Dispatchers.IO) {
        dao.deleteTaskById(taskId)
        dao.deleteRemindersForTask(taskId)
    }

    // --- Email Operations ---

    suspend fun analyzeEmail(email: EmailItem): AiEmailAnalysis = withContext(Dispatchers.IO) {
        val analysis = GeminiService.analyzeEmail(email)
        // Mark as read
        dao.setEmailRead(email.id, true)
        analysis
    }

    suspend fun addCustomEmail(
        senderName: String,
        senderEmail: String,
        subject: String,
        body: String,
        category: EmailCategory = EmailCategory.IMPORTANT
    ): EmailItem = withContext(Dispatchers.IO) {
        val email = EmailItem(
            id = "custom-" + UUID.randomUUID().toString().take(8),
            senderName = senderName,
            senderEmail = senderEmail,
            subject = subject,
            body = body,
            snippet = if (body.length > 90) body.take(90) + "..." else body,
            receivedTimestamp = System.currentTimeMillis(),
            category = category,
            isRead = false,
            hasAttachments = false
        )
        dao.insertEmail(EmailEntity.fromEmailItem(email))
        email
    }

    suspend fun markEmailRead(emailId: String, isRead: Boolean) = withContext(Dispatchers.IO) {
        dao.setEmailRead(emailId, isRead)
    }

    // --- Reminder & Alert Operations ---

    suspend fun snoozeReminder(reminderId: String, additionalMinutes: Int) = withContext(Dispatchers.IO) {
        val reminders = dao.getAllReminders().first()
        val reminder = reminders.find { it.id == reminderId } ?: return@withContext
        val updated = reminder.copy(
            scheduledTimeMillis = System.currentTimeMillis() + (additionalMinutes * 60 * 1000L),
            snoozedCount = reminder.snoozedCount + 1,
            isDelivered = false
        )
        dao.updateReminder(updated)

        // Log notification
        dao.insertNotificationHistory(
            NotificationHistoryEntity(
                id = UUID.randomUUID().toString(),
                title = "Reminder Snoozed",
                message = "Snoozed '${reminder.taskTitle}' by $additionalMinutes minutes.",
                timestamp = System.currentTimeMillis(),
                taskId = reminder.taskId,
                type = "SNOOZE"
            )
        )
    }

    suspend fun deliverReminder(reminderId: String, taskTitle: String, taskId: String?) = withContext(Dispatchers.IO) {
        dao.markReminderDelivered(reminderId, System.currentTimeMillis())
        NotificationHelper.showDeadlineAlert(
            context = context,
            notificationId = reminderId.hashCode(),
            title = "Deadline Reminder: $taskTitle",
            message = "Your scheduled deadline is approaching. Tap to view task details.",
            taskId = taskId
        )
        dao.insertNotificationHistory(
            NotificationHistoryEntity(
                id = UUID.randomUUID().toString(),
                title = "Deadline Alert Delivered",
                message = "Alert delivered for '$taskTitle'.",
                timestamp = System.currentTimeMillis(),
                taskId = taskId,
                type = "DELIVERED"
            )
        )
    }

    // --- Gmail Integration Operations ---

    fun setGmailConnected(connected: Boolean, email: String = "rakshit2004g@gmail.com", token: String? = null) {
        _isGmailConnected.value = connected
        _isDemoMode.value = !connected
        _connectedAccountEmail.value = email
        com.example.data.gmail.GmailClient.setCredentials(token, email)
        if (connected) {
            scope.launch {
                syncGmailEmails()
            }
        }
    }

    suspend fun syncGmailEmails(query: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val result = com.example.data.gmail.GmailClient.fetchMessages(query = query, maxResults = 20)
        result.fold(
            onSuccess = { emails ->
                if (emails.isNotEmpty()) {
                    val entities = emails.map { EmailEntity.fromEmailItem(it) }
                    dao.insertEmails(entities)
                }
                Result.success(emails.size)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    suspend fun markEmailStarred(emailId: String, isStarred: Boolean) = withContext(Dispatchers.IO) {
        dao.setEmailStarred(emailId, isStarred)
        com.example.data.gmail.GmailClient.starEmail(emailId, isStarred)
    }

    suspend fun archiveEmail(emailId: String) = withContext(Dispatchers.IO) {
        dao.setEmailArchived(emailId, true)
        com.example.data.gmail.GmailClient.archiveEmail(emailId)
    }

    suspend fun trashEmail(emailId: String) = withContext(Dispatchers.IO) {
        dao.deleteEmailById(emailId)
        com.example.data.gmail.GmailClient.trashEmail(emailId)
    }

    suspend fun deleteEmailPermanently(emailId: String) = withContext(Dispatchers.IO) {
        dao.deleteEmailById(emailId)
        com.example.data.gmail.GmailClient.deletePermanently(emailId)
    }

    suspend fun createDraft(to: String, subject: String, body: String, threadId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        com.example.data.gmail.GmailClient.createDraft(to, subject, body, threadId)
    }

    suspend fun sendEmail(to: String, subject: String, body: String, threadId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        com.example.data.gmail.GmailClient.sendEmail(to, subject, body, threadId)
    }

    fun setDemoMode(isDemo: Boolean) {
        _isDemoMode.value = isDemo
    }
}
