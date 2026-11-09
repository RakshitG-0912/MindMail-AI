package com.example.model

enum class ReminderLeadTime(val minutes: Int, val label: String) {
    TEN_MINUTES(10, "10 minutes before"),
    ONE_HOUR(60, "1 hour before"),
    ONE_DAY(1440, "1 day before"),
    THREE_DAYS(4320, "3 days before"),
    SEVEN_DAYS(10080, "7 days before")
}

data class ReminderItem(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val scheduledTimeMillis: Long,
    val leadTimeMinutes: Int,
    val isDelivered: Boolean = false,
    val deliveredAt: Long? = null,
    val snoozedCount: Int = 0
)

data class NotificationHistoryItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val taskId: String? = null,
    val type: String = "REMINDER" // REMINDER, VOICE_ALERT, DEADLINE_WARNING
)
