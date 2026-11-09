package com.example.data

import com.example.model.EmailCategory
import com.example.model.EmailItem
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import java.util.UUID

object DemoData {

    fun generateInitialEmails(): List<EmailItem> {
        val now = System.currentTimeMillis()
        val oneHour = 3600 * 1000L
        val oneDay = 24 * oneHour

        return listOf(
            EmailItem(
                id = "email-1",
                senderName = "Sarah Jenkins",
                senderEmail = "s.jenkins@innovatetech.io",
                subject = "Action Required: Review Release Candidate & Approve Deployment",
                body = """
                    Hi Alex,
                    
                    Our QA test suite passed 100% on the latest Android release candidate build (v2.4.0).
                    
                    Could you please do a final sanity check and approve the Play Store release rollout by tomorrow at 5:00 PM? 
                    
                    Key items to verify:
                    1. Google Sign-In and Credential Manager token refresh
                    2. In-app voice recognition latency
                    3. Offline task sync queue
                    
                    If you detect any critical issues, please alert me immediately in Slack.
                    
                    Thanks,
                    Sarah
                """.trimIndent(),
                snippet = "Could you please do a final sanity check and approve the Play Store release rollout by tomorrow at 5:00 PM?",
                receivedTimestamp = now - (2 * oneHour),
                category = EmailCategory.ACTION_REQUIRED,
                isRead = false,
                hasAttachments = true,
                attachmentNames = listOf("release_notes_v2.4.pdf", "qa_test_report.xlsx"),
                extractedTaskTitle = "Approve Play Store Release Rollout v2.4",
                extractedDeadline = now + (20 * oneHour),
                extractedPriority = TaskPriority.URGENT.name
            ),
            EmailItem(
                id = "email-2",
                senderName = "David Henderson",
                senderEmail = "d.henderson@ventures.com",
                subject = "Board of Directors Deck: Submit Department OKRs",
                body = """
                    Hi Alex,
                    
                    The quarterly Board of Directors meeting has been scheduled for next Tuesday at 10:00 AM.
                    
                    Please submit your finalized Q3 performance slides and engineering OKRs by Friday at 4:00 PM. We need time over the weekend to consolidate the master presentation.
                    
                    Let me know if you need any additional revenue or analytics data from my team.
                    
                    Best regards,
                    David Henderson
                    Managing Director
                """.trimIndent(),
                snippet = "Please submit your finalized Q3 performance slides and engineering OKRs by Friday at 4:00 PM.",
                receivedTimestamp = now - (5 * oneHour),
                category = EmailCategory.IMPORTANT,
                isRead = false,
                hasAttachments = true,
                attachmentNames = listOf("board_template_q3.pptx"),
                extractedTaskTitle = "Submit Q3 Board Deck & OKR Slides",
                extractedDeadline = now + (2 * oneDay + 4 * oneHour),
                extractedPriority = TaskPriority.HIGH.name
            ),
            EmailItem(
                id = "email-3",
                senderName = "AWS Billing Alerts",
                senderEmail = "no-reply@amazon.com",
                subject = "Cloud Infrastructure Invoice #INV-8891 Due Soon",
                body = """
                    Dear Customer,
                    
                    Your monthly AWS cloud compute invoice of $1,420.50 is due on Wednesday. 
                    
                    Please verify your corporate credit card billing details or authorize payment before 11:59 PM to ensure uninterrupted production server uptime.
                    
                    View your invoice details at https://console.aws.amazon.com/billing.
                """.trimIndent(),
                snippet = "Your monthly AWS cloud compute invoice of $1,420.50 is due on Wednesday. Authorize payment before 11:59 PM.",
                receivedTimestamp = now - (12 * oneHour),
                category = EmailCategory.DEADLINES,
                isRead = false,
                hasAttachments = true,
                attachmentNames = listOf("invoice_8891.pdf"),
                extractedTaskTitle = "Pay AWS Cloud Infrastructure Invoice",
                extractedDeadline = now + (1 * oneDay + 8 * oneHour),
                extractedPriority = TaskPriority.URGENT.name
            ),
            EmailItem(
                id = "email-4",
                senderName = "Elena Rostova",
                senderEmail = "elena.r@designcraft.org",
                subject = "UX Sprint Retrospective & Mobile Navigation Review",
                body = """
                    Hey Alex!
                    
                    Just a quick reminder about our bi-weekly UX retrospective tomorrow at 2:30 PM.
                    
                    Please prepare your feedback notes on the new gesture-based floating voice assistant trigger and the adaptive dark theme tokens.
                    
                    Meeting Link: https://meet.google.com/xyz-qwe-asd
                    
                    See you there!
                """.trimIndent(),
                snippet = "Reminder about our bi-weekly UX retrospective tomorrow at 2:30 PM. Bring feedback on voice assistant UI.",
                receivedTimestamp = now - (18 * oneHour),
                category = EmailCategory.MEETINGS,
                isRead = true,
                hasAttachments = false,
                attachmentNames = emptyList(),
                extractedTaskTitle = "Prepare UX Retrospective Notes",
                extractedDeadline = now + (18 * oneHour),
                extractedPriority = TaskPriority.MEDIUM.name
            ),
            EmailItem(
                id = "email-5",
                senderName = "Marcus Vance",
                senderEmail = "m.vance@legalpartners.com",
                subject = "Client Partnership Agreement: Action Required for Countersignature",
                body = """
                    Alex,
                    
                    Acme Corp has formally signed off on the strategic integration agreement. 
                    
                    The document is pending your final executive signature via DocuSign. Please complete this by Thursday at 2:00 PM so their legal team can release the API credentials.
                    
                    Feel free to reach out if any clauses need adjustment.
                """.trimIndent(),
                snippet = "Document is pending your final executive signature. Complete by Thursday at 2:00 PM.",
                receivedTimestamp = now - (24 * oneHour),
                category = EmailCategory.ACTION_REQUIRED,
                isRead = true,
                hasAttachments = true,
                attachmentNames = listOf("Acme_Partnership_Agreement_v3.pdf"),
                extractedTaskTitle = "Sign Acme Corp Partnership Agreement",
                extractedDeadline = now + (3 * oneDay),
                extractedPriority = TaskPriority.HIGH.name
            ),
            EmailItem(
                id = "email-6",
                senderName = "Delta Air Lines",
                senderEmail = "ticketreceipt@delta.com",
                subject = "Flight Confirmation: San Francisco (SFO) - Friday 8:15 AM",
                body = """
                    Your flight reservation is confirmed.
                    
                    Flight: DL 1422
                    Departing: JFK 8:15 AM -> Arriving SFO 11:45 AM
                    Confirmation Code: H72XYZ
                    
                    Online check-in opens 24 hours prior to departure. Baggage allowance: 1 carry-on + 1 personal item.
                """.trimIndent(),
                snippet = "Flight DL 1422 departing JFK at 8:15 AM. Check-in opens 24 hours prior.",
                receivedTimestamp = now - (28 * oneHour),
                category = EmailCategory.PERSONAL,
                isRead = true,
                hasAttachments = false,
                attachmentNames = emptyList(),
                extractedTaskTitle = "Check-in for Delta Flight DL 1422",
                extractedDeadline = now + (4 * oneDay),
                extractedPriority = TaskPriority.LOW.name
            ),
            EmailItem(
                id = "email-7",
                senderName = "Android Weekly",
                senderEmail = "newsletter@androidweekly.net",
                subject = "Issue #642: Jetpack Compose 1.8 & Gemini Multimodal in Android",
                body = """
                    Welcome to issue #642!
                    
                    Highlights this week:
                    - Exploring Predictive Back animations in Navigation 3
                    - On-device speech intelligence with Android 15
                    - High performance Room indexing for large databases
                """.trimIndent(),
                snippet = "Highlights: Jetpack Compose 1.8, On-device speech, Predictive back animations.",
                receivedTimestamp = now - (36 * oneHour),
                category = EmailCategory.PROMOTIONS,
                isRead = true,
                hasAttachments = false,
                attachmentNames = emptyList(),
                extractedTaskTitle = null,
                extractedDeadline = null,
                extractedPriority = null
            )
        )
    }

    fun generateInitialTasks(): List<TaskItem> {
        val now = System.currentTimeMillis()
        val oneHour = 3600 * 1000L
        val oneDay = 24 * oneHour

        return listOf(
            TaskItem(
                id = "task-1",
                title = "Approve Play Store Release Rollout v2.4",
                description = "Verify Google Sign-In, speech latency, and offline sync before giving final greenlight.",
                sourceEmailId = "email-1",
                sourceEmailSubject = "Action Required: Review Release Candidate & Approve Deployment",
                sourceEmailSender = "Sarah Jenkins",
                deadlineMillis = now + (3 * oneHour), // Due in 3 hours today!
                priority = TaskPriority.URGENT,
                status = TaskStatus.PENDING,
                category = "Engineering",
                createdAt = now - (2 * oneHour),
                reminderMinutesBefore = 60,
                isReminderEnabled = true
            ),
            TaskItem(
                id = "task-2",
                title = "Review Cloud Infrastructure Invoice with Finance",
                description = "Check monthly compute costs against AWS budget limits.",
                sourceEmailId = "email-3",
                sourceEmailSubject = "Cloud Infrastructure Invoice #INV-8891 Due Soon",
                sourceEmailSender = "AWS Billing Alerts",
                deadlineMillis = now - (2 * oneHour), // Overdue by 2 hours!
                priority = TaskPriority.URGENT,
                status = TaskStatus.PENDING,
                category = "Finance",
                createdAt = now - (14 * oneHour),
                reminderMinutesBefore = 10,
                isReminderEnabled = true
            ),
            TaskItem(
                id = "task-3",
                title = "Prepare UX Retrospective Notes",
                description = "Gather feedback on voice assistant floating launcher and dark theme contrast.",
                sourceEmailId = "email-4",
                sourceEmailSubject = "UX Sprint Retrospective & Mobile Navigation Review",
                sourceEmailSender = "Elena Rostova",
                deadlineMillis = now + (8 * oneHour), // Today evening!
                priority = TaskPriority.MEDIUM,
                status = TaskStatus.PENDING,
                category = "Design",
                createdAt = now - (18 * oneHour),
                reminderMinutesBefore = 60,
                isReminderEnabled = true
            ),
            TaskItem(
                id = "task-4",
                title = "Submit Q3 Board Deck & OKR Slides",
                description = "Consolidate department metrics and engineering roadmaps for David Henderson.",
                sourceEmailId = "email-2",
                sourceEmailSubject = "Board of Directors Deck: Submit Department OKRs",
                sourceEmailSender = "David Henderson",
                deadlineMillis = now + (2 * oneDay), // Upcoming!
                priority = TaskPriority.HIGH,
                status = TaskStatus.PENDING,
                category = "Executive",
                createdAt = now - (5 * oneHour),
                reminderMinutesBefore = 1440,
                isReminderEnabled = true
            ),
            TaskItem(
                id = "task-5",
                title = "Sign Acme Corp Partnership Agreement",
                description = "Countersign legal agreement via DocuSign to authorize API credential exchange.",
                sourceEmailId = "email-5",
                sourceEmailSubject = "Client Partnership Agreement: Action Required for Countersignature",
                sourceEmailSender = "Marcus Vance",
                deadlineMillis = now + (3 * oneDay), // Upcoming!
                priority = TaskPriority.HIGH,
                status = TaskStatus.PENDING,
                category = "Legal",
                createdAt = now - (24 * oneHour),
                reminderMinutesBefore = 1440,
                isReminderEnabled = true
            ),
            TaskItem(
                id = "task-6",
                title = "Setup Staging Database Indexing",
                description = "Completed database migration and added composite index on tasks table.",
                sourceEmailId = null,
                sourceEmailSubject = null,
                sourceEmailSender = null,
                deadlineMillis = now - (24 * oneHour),
                priority = TaskPriority.LOW,
                status = TaskStatus.COMPLETED,
                category = "Engineering",
                createdAt = now - (36 * oneHour),
                completedAt = now - (4 * oneHour),
                reminderMinutesBefore = 60,
                isReminderEnabled = false
            )
        )
    }
}
