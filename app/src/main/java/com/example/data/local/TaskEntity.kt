package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val sourceEmailId: String?,
    val sourceEmailSubject: String?,
    val sourceEmailSender: String?,
    val deadlineMillis: Long?,
    val priority: String, // URGENT, HIGH, MEDIUM, LOW
    val status: String,   // PENDING, IN_PROGRESS, COMPLETED
    val category: String,
    val createdAt: Long,
    val completedAt: Long?,
    val reminderMinutesBefore: Int,
    val isReminderEnabled: Boolean
) {
    fun toTaskItem(): TaskItem {
        return TaskItem(
            id = id,
            title = title,
            description = description,
            sourceEmailId = sourceEmailId,
            sourceEmailSubject = sourceEmailSubject,
            sourceEmailSender = sourceEmailSender,
            deadlineMillis = deadlineMillis,
            priority = try { TaskPriority.valueOf(priority) } catch (_: Exception) { TaskPriority.MEDIUM },
            status = try { TaskStatus.valueOf(status) } catch (_: Exception) { TaskStatus.PENDING },
            category = category,
            createdAt = createdAt,
            completedAt = completedAt,
            reminderMinutesBefore = reminderMinutesBefore,
            isReminderEnabled = isReminderEnabled
        )
    }

    companion object {
        fun fromTaskItem(task: TaskItem): TaskEntity {
            return TaskEntity(
                id = task.id,
                title = task.title,
                description = task.description,
                sourceEmailId = task.sourceEmailId,
                sourceEmailSubject = task.sourceEmailSubject,
                sourceEmailSender = task.sourceEmailSender,
                deadlineMillis = task.deadlineMillis,
                priority = task.priority.name,
                status = task.status.name,
                category = task.category,
                createdAt = task.createdAt,
                completedAt = task.completedAt,
                reminderMinutesBefore = task.reminderMinutesBefore,
                isReminderEnabled = task.isReminderEnabled
            )
        }
    }
}
