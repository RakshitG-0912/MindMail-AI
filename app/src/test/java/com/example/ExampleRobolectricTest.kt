package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiActionFramework
import com.example.ai.GeminiService
import com.example.data.DemoData
import com.example.data.gmail.GmailClient
import com.example.model.AssistantLanguage
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.ToolName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches MailMind AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MailMind AI", appName)
    }

    @Test
    fun `demo emails and tasks are properly generated`() {
        val emails = DemoData.generateInitialEmails()
        val tasks = DemoData.generateInitialTasks()

        assertTrue(emails.isNotEmpty())
        assertTrue(tasks.isNotEmpty())

        val overdue = tasks.any { it.isOverdue() }
        assertTrue("Expected at least one overdue demo task", overdue)
    }

    @Test
    fun `voice assistant understands commands and generates spoken responses in English`() = runBlocking {
        val emails = DemoData.generateInitialEmails()
        val tasks = DemoData.generateInitialTasks()

        // 1. Read important emails
        val emailResp = GeminiService.processVoiceCommand("Read my important emails", tasks, emails)
        assertEquals(GeminiService.VoiceActionType.READ_IMPORTANT_EMAILS, emailResp.actionType)
        assertTrue(emailResp.spokenText.contains("important"))

        // 2. Search emails (placement cell)
        val searchResp = GeminiService.processVoiceCommand("Find all emails from my placement cell", tasks, emails)
        assertEquals(GeminiService.VoiceActionType.SEARCH_EMAILS, searchResp.actionType)
        assertEquals(ToolName.SEARCH_EMAILS, searchResp.toolName)

        // 3. Draft reply
        val draftResp = GeminiService.processVoiceCommand("Reply to this email saying I will attend the meeting", tasks, emails)
        assertEquals(GeminiService.VoiceActionType.CREATE_DRAFT, draftResp.actionType)
        assertTrue(draftResp.spokenText.contains("drafted a reply", ignoreCase = true))

        // 4. Sensitive action: Send email requires confirmation
        val sendResp = GeminiService.processVoiceCommand("Send this email to the recipient", tasks, emails)
        assertEquals(GeminiService.VoiceActionType.STAGE_SEND_EMAIL, sendResp.actionType)
        assertNotNull(sendResp.pendingConfirmation)
        assertTrue(sendResp.spokenText.contains("Confirmation required"))

        // 5. User confirms pending action with affirmative ("yes")
        val confirmResp = GeminiService.processVoiceCommand("yes", tasks, emails, activePendingConfirmation = sendResp.pendingConfirmation)
        assertEquals(GeminiService.VoiceActionType.EXECUTE_CONFIRMED_ACTION, confirmResp.actionType)
        assertTrue(confirmResp.spokenText.contains("Confirmed"))

        // 6. Navigation command
        val navResp = GeminiService.processVoiceCommand("Open inbox", tasks, emails)
        assertEquals(GeminiService.VoiceActionType.NAVIGATE_INBOX, navResp.actionType)
        assertEquals("INBOX", navResp.targetScreenName)

        // 7. Out of domain apology
        val outOfDomainResp = GeminiService.processVoiceCommand("Tell me a joke about dogs", tasks, emails)
        assertEquals(GeminiService.VoiceActionType.OUT_OF_DOMAIN, outOfDomainResp.actionType)
        assertTrue(outOfDomainResp.spokenText.contains("I apologize for the inconvenience"))
        assertTrue(outOfDomainResp.spokenText.contains("outside my domain"))
    }

    @Test
    fun `voice assistant supports Hindi voice commands and responses`() = runBlocking {
        val emails = DemoData.generateInitialEmails()
        val tasks = DemoData.generateInitialTasks()

        // 1. Read important emails in Hindi
        val hindiResp = GeminiService.processVoiceCommand("मेरे ज़रूरी ईमेल पढ़ो", tasks, emails, language = AssistantLanguage.HINDI)
        assertEquals(GeminiService.VoiceActionType.READ_IMPORTANT_EMAILS, hindiResp.actionType)
        assertTrue(hindiResp.spokenText.contains("ईमेल"))

        // 2. Hindi out-of-domain apology
        val hindiOod = GeminiService.processVoiceCommand("आज का मौसम कैसा है?", tasks, emails, language = AssistantLanguage.HINDI)
        assertEquals(GeminiService.VoiceActionType.OUT_OF_DOMAIN, hindiOod.actionType)
        assertTrue(hindiOod.spokenText.contains("असुविधा के लिए क्षमा करें"))
    }

    @Test
    fun `gmail client config is properly initialized`() {
        assertEquals("rakshit2004g@gmail.com", GmailClient.DEFAULT_EMAIL)
        assertTrue(GmailClient.CLIENT_ID.contains("272024622828"))
    }

    @Test
    fun `task item computes remaining time string correctly`() {
        val now = System.currentTimeMillis()
        val futureTask = TaskItem(
            id = "test-future",
            title = "Submit project proposal",
            deadlineMillis = now + (3 * 3600 * 1000L),
            priority = TaskPriority.HIGH
        )
        val timeRemaining = futureTask.getTimeRemainingText()
        assertTrue("Expected 2-3 hours remaining, got: $timeRemaining", timeRemaining.contains("remaining"))

        val overdueTask = TaskItem(
            id = "test-overdue",
            title = "Old task",
            deadlineMillis = now - (2 * 3600 * 1000L),
            priority = TaskPriority.URGENT
        )
        val overdueRemaining = overdueTask.getTimeRemainingText()
        assertTrue("Expected Overdue indicator, got: $overdueRemaining", overdueRemaining.contains("Overdue"))
    }

    @Test
    fun `google auth manager initialization and gmail scope configuration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.auth.GoogleAuthManager.ensureFirebaseInitialized(context)

        assertEquals("https://www.googleapis.com/auth/gmail.readonly", com.example.auth.GoogleAuthManager.GMAIL_SCOPE)
        val clientId = context.getString(R.string.default_web_client_id)
        assertTrue(clientId.contains("272024622828"))

        val scope = context.getString(R.string.gmail_scope)
        assertEquals("https://www.googleapis.com/auth/gmail.readonly", scope)

        val targetEmail = context.getString(R.string.authorized_user_email)
        assertEquals("rakshit2004g@gmail.com", targetEmail)
    }

    @Test
    fun `gmail client credentials lifecycle test`() {
        GmailClient.setCredentials("test_id_token_12345", "rakshit2004g@gmail.com")
        assertTrue(GmailClient.isConnected.value)
        assertEquals("rakshit2004g@gmail.com", GmailClient.connectedEmail.value)
        assertEquals("test_id_token_12345", GmailClient.accessToken.value)

        GmailClient.disconnect()
        assertTrue(!GmailClient.isConnected.value)
    }
}
