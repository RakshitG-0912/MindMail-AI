package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.NotificationHistoryItem
import com.example.model.ReminderItem

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val taskTitle: String,
    val scheduledTimeMillis: Long,
    val leadTimeMinutes: Int,
    val isDelivered: Boolean,
    val deliveredAt: Long?,
    val snoozedCount: Int
) {
    fun toReminderItem(): ReminderItem = ReminderItem(
        id = id,
        taskId = taskId,
        taskTitle = taskTitle,
        scheduledTimeMillis = scheduledTimeMillis,
        leadTimeMinutes = leadTimeMinutes,
        isDelivered = isDelivered,
        deliveredAt = deliveredAt,
        snoozedCount = snoozedCount
    )

    companion object {
        fun fromReminderItem(item: ReminderItem): ReminderEntity = ReminderEntity(
            id = item.id,
            taskId = item.taskId,
            taskTitle = item.taskTitle,
            scheduledTimeMillis = item.scheduledTimeMillis,
            leadTimeMinutes = item.leadTimeMinutes,
            isDelivered = item.isDelivered,
            deliveredAt = item.deliveredAt,
            snoozedCount = item.snoozedCount
        )
    }
}

@Entity(tableName = "notifications_history")
data class NotificationHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val taskId: String?,
    val type: String
) {
    fun toNotificationHistoryItem(): NotificationHistoryItem = NotificationHistoryItem(
        id = id,
        title = title,
        message = message,
        timestamp = timestamp,
        taskId = taskId,
        type = type
    )

    companion object {
        fun fromItem(item: NotificationHistoryItem): NotificationHistoryEntity = NotificationHistoryEntity(
            id = item.id,
            title = item.title,
            message = item.message,
            timestamp = item.timestamp,
            taskId = item.taskId,
            type = item.type
        )
    }
}
