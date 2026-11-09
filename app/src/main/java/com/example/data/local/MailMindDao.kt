package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MailMindDao {

    // --- Tasks ---
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status != 'COMPLETED' ORDER BY CASE WHEN deadlineMillis IS NULL THEN 1 ELSE 0 END, deadlineMillis ASC")
    fun getPendingTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND status != 'COMPLETED' LIMIT 1")
    suspend fun findDuplicateTask(title: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    // --- Emails ---
    @Query("SELECT * FROM emails ORDER BY receivedTimestamp DESC")
    fun getAllEmails(): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE category = :category ORDER BY receivedTimestamp DESC")
    fun getEmailsByCategory(category: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE id = :emailId LIMIT 1")
    suspend fun getEmailById(emailId: String): EmailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: EmailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Update
    suspend fun updateEmail(email: EmailEntity)

    @Query("UPDATE emails SET isRead = :isRead WHERE id = :emailId")
    suspend fun setEmailRead(emailId: String, isRead: Boolean)

    @Query("UPDATE emails SET isStarred = :isStarred WHERE id = :emailId")
    suspend fun setEmailStarred(emailId: String, isStarred: Boolean)

    @Query("UPDATE emails SET isArchived = :isArchived WHERE id = :emailId")
    suspend fun setEmailArchived(emailId: String, isArchived: Boolean)

    @Query("DELETE FROM emails WHERE id = :emailId")
    suspend fun deleteEmailById(emailId: String)

    @Query("DELETE FROM emails")
    suspend fun clearAllEmails()

    // --- Reminders ---
    @Query("SELECT * FROM reminders WHERE isDelivered = 0 ORDER BY scheduledTimeMillis ASC")
    fun getActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY scheduledTimeMillis DESC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isDelivered = 1, deliveredAt = :timestamp WHERE id = :reminderId")
    suspend fun markReminderDelivered(reminderId: String, timestamp: Long)

    @Query("DELETE FROM reminders WHERE taskId = :taskId")
    suspend fun deleteRemindersForTask(taskId: String)

    // --- Notification History ---
    @Query("SELECT * FROM notifications_history ORDER BY timestamp DESC LIMIT 50")
    fun getNotificationHistory(): Flow<List<NotificationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationHistory(item: NotificationHistoryEntity)

    @Query("DELETE FROM notifications_history")
    suspend fun clearNotificationHistory()
}
