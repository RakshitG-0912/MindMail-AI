package com.example.model

enum class TaskPriority(val displayName: String, val level: Int) {
    URGENT("Urgent", 4),
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1)
}

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}

data class TaskItem(
    val id: String,
    val title: String,
    val description: String = "",
    val sourceEmailId: String? = null,
    val sourceEmailSubject: String? = null,
    val sourceEmailSender: String? = null,
    val deadlineMillis: Long? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.PENDING,
    val category: String = "General",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val reminderMinutesBefore: Int = 60, // Default 1 hour before
    val isReminderEnabled: Boolean = true
) {
    fun isOverdue(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (status == TaskStatus.COMPLETED) return false
        return deadlineMillis != null && deadlineMillis < currentTime
    }

    fun isDueToday(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (deadlineMillis == null || status == TaskStatus.COMPLETED) return false
        val calNow = java.util.Calendar.getInstance().apply { timeInMillis = currentTime }
        val calTask = java.util.Calendar.getInstance().apply { timeInMillis = deadlineMillis }
        return calNow.get(java.util.Calendar.YEAR) == calTask.get(java.util.Calendar.YEAR) &&
                calNow.get(java.util.Calendar.DAY_OF_YEAR) == calTask.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun isUpcoming(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (deadlineMillis == null || status == TaskStatus.COMPLETED) return false
        return deadlineMillis > currentTime && !isDueToday(currentTime)
    }

    fun getTimeRemainingText(currentTime: Long = System.currentTimeMillis()): String {
        if (status == TaskStatus.COMPLETED) return "Completed"
        if (deadlineMillis == null) return "No deadline set"

        val diffMillis = deadlineMillis - currentTime
        if (diffMillis < 0) {
            val overdueMillis = -diffMillis
            val hours = (overdueMillis / (3600 * 1000L)).toInt()
            val mins = ((overdueMillis % (3600 * 1000L)) / (60 * 1000L)).toInt()
            val days = (overdueMillis / (24 * 3600 * 1000L)).toInt()
            return when {
                days > 0 -> "Overdue by $days day${if (days > 1) "s" else ""}"
                hours > 0 -> "Overdue by ${hours}h ${mins}m"
                else -> "Overdue by ${mins}m"
            }
        }

        val totalMins = (diffMillis / (60 * 1000L)).toInt()
        val totalHours = (diffMillis / (3600 * 1000L)).toInt()
        val days = (diffMillis / (24 * 3600 * 1000L)).toInt()
        val remainingHours = (totalHours % 24)
        val remainingMins = (totalMins % 60)

        return when {
            days > 1 -> "$days days remaining"
            days == 1 -> "1 day ${remainingHours}h remaining"
            totalHours >= 6 -> "$totalHours hours remaining"
            totalHours >= 1 -> "${totalHours}h ${remainingMins}m remaining"
            totalMins > 0 -> "$totalMins mins remaining"
            else -> "Due in seconds"
        }
    }
}
