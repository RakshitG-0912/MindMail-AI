package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.EmailCategory
import com.example.model.EmailItem

@Entity(tableName = "emails")
data class EmailEntity(
    @PrimaryKey val id: String,
    val senderName: String,
    val senderEmail: String,
    val subject: String,
    val body: String,
    val snippet: String,
    val receivedTimestamp: Long,
    val category: String,
    val isRead: Boolean,
    val isStarred: Boolean = false,
    val isArchived: Boolean = false,
    val labelsString: String = "",
    val threadId: String? = null,
    val hasAttachments: Boolean = false,
    val attachmentNamesString: String = "",
    val extractedTaskTitle: String? = null,
    val extractedDeadline: Long? = null,
    val extractedPriority: String? = null
) {
    fun toEmailItem(): EmailItem {
        return EmailItem(
            id = id,
            senderName = senderName,
            senderEmail = senderEmail,
            subject = subject,
            body = body,
            snippet = snippet,
            receivedTimestamp = receivedTimestamp,
            category = try { EmailCategory.valueOf(category) } catch (_: Exception) { EmailCategory.IMPORTANT },
            isRead = isRead,
            isStarred = isStarred,
            isArchived = isArchived,
            labels = if (labelsString.isBlank()) emptyList() else labelsString.split("||"),
            threadId = threadId,
            hasAttachments = hasAttachments,
            attachmentNames = if (attachmentNamesString.isBlank()) emptyList() else attachmentNamesString.split("||"),
            extractedTaskTitle = extractedTaskTitle,
            extractedDeadline = extractedDeadline,
            extractedPriority = extractedPriority
        )
    }

    companion object {
        fun fromEmailItem(item: EmailItem): EmailEntity {
            return EmailEntity(
                id = item.id,
                senderName = item.senderName,
                senderEmail = item.senderEmail,
                subject = item.subject,
                body = item.body,
                snippet = item.snippet,
                receivedTimestamp = item.receivedTimestamp,
                category = item.category.name,
                isRead = item.isRead,
                isStarred = item.isStarred,
                isArchived = item.isArchived,
                labelsString = item.labels.joinToString("||"),
                threadId = item.threadId,
                hasAttachments = item.hasAttachments,
                attachmentNamesString = item.attachmentNames.joinToString("||"),
                extractedTaskTitle = item.extractedTaskTitle,
                extractedDeadline = item.extractedDeadline,
                extractedPriority = item.extractedPriority
            )
        }
    }
}
