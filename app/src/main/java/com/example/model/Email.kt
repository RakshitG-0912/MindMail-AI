package com.example.model

enum class EmailCategory(val displayName: String, val badgeColorHex: Long) {
    ALL("All", 0xFF64748B),
    IMPORTANT("Important", 0xFFEF4444),
    ACTION_REQUIRED("Action Required", 0xFFF59E0B),
    DEADLINES("Deadlines", 0xFF8B5CF6),
    MEETINGS("Meetings", 0xFF0284C7),
    PERSONAL("Personal", 0xFF10B981),
    PROMOTIONS("Promotions", 0xFF6B7280)
}

data class EmailItem(
    val id: String,
    val senderName: String,
    val senderEmail: String,
    val subject: String,
    val body: String,
    val snippet: String,
    val receivedTimestamp: Long,
    val category: EmailCategory,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val isArchived: Boolean = false,
    val labels: List<String> = emptyList(),
    val threadId: String? = null,
    val hasAttachments: Boolean = false,
    val attachmentNames: List<String> = emptyList(),
    val extractedTaskTitle: String? = null,
    val extractedDeadline: Long? = null,
    val extractedPriority: String? = null
)
