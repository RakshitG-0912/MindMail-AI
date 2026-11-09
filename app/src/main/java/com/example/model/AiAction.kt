package com.example.model

enum class AssistantLanguage(val code: String, val displayName: String, val localeTag: String) {
    ENGLISH("en", "English", "en-US"),
    HINDI("hi", "हिन्दी (Hindi)", "hi-IN")
}

enum class ActionStatus {
    EXECUTED,
    PENDING_CONFIRMATION,
    CONFIRMED,
    CANCELLED,
    FAILED
}

enum class ToolName(val displayName: String, val isSensitive: Boolean, val requiresDoubleWarning: Boolean = false) {
    SEARCH_EMAILS("Search Emails", false),
    READ_EMAIL("Read Email", false),
    SUMMARIZE_THREAD("Summarize Thread", false),
    MARK_AS_READ("Mark as Read", false),
    MARK_AS_UNREAD("Mark as Unread", false),
    STAR_EMAIL("Star Email", false),
    ARCHIVE_EMAIL("Archive Email", true),
    APPLY_LABEL("Apply Label", false),
    CREATE_EMAIL_DRAFT("Create Draft", false),
    SEND_EMAIL("Send Email", true),
    DELETE_EMAIL("Delete Email", true, true),
    CREATE_TASK("Create Task", false),
    UPDATE_TASK("Update Task", false),
    COMPLETE_TASK("Complete Task", false),
    CREATE_REMINDER("Create Reminder", false),
    SNOOZE_REMINDER("Snooze Reminder", false),
    GET_PENDING_TASKS("Get Pending Tasks", false),
    GET_UPCOMING_DEADLINES("Get Upcoming Deadlines", false)
}

data class PendingActionConfirmation(
    val actionId: String = java.util.UUID.randomUUID().toString(),
    val toolName: ToolName,
    val description: String,
    val spokenPrompt: String,
    val parameters: Map<String, Any?>,
    val timestamp: Long = System.currentTimeMillis(),
    val requiresDoubleWarning: Boolean = false,
    val doubleWarningGiven: Boolean = false
)

data class AiActionLogItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val toolName: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: ActionStatus,
    val isSensitive: Boolean = false,
    val parametersSummary: String = "",
    val resultSummary: String? = null
)
