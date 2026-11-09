package com.example.data.gmail

import android.util.Base64
import com.example.model.EmailCategory
import com.example.model.EmailItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object GmailClient {

    private const val BASE_URL = "https://gmail.googleapis.com/"
    const val CLIENT_ID = "272024622828-4caa125p58erndhpnreehdcjbb605jfe.apps.googleusercontent.com"
    const val DEFAULT_EMAIL = "rakshit2004g@gmail.com"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val apiService: GmailApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GmailApiService::class.java)

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _connectedEmail = MutableStateFlow(DEFAULT_EMAIL)
    val connectedEmail: StateFlow<String> = _connectedEmail.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun setCredentials(token: String?, email: String = DEFAULT_EMAIL) {
        _accessToken.value = token
        _connectedEmail.value = email
        _isConnected.value = !token.isNullOrBlank()
    }

    fun disconnect() {
        _accessToken.value = null
        _isConnected.value = false
    }

    private fun getAuthHeader(): String {
        val token = _accessToken.value ?: ""
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    suspend fun fetchMessages(
        query: String? = null,
        maxResults: Int = 15
    ): Result<List<EmailItem>> = withContext(Dispatchers.IO) {
        if (!_isConnected.value || _accessToken.value.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Gmail is not connected. Authenticate to access Gmail API."))
        }

        try {
            val listResp = apiService.listMessages(
                authHeader = getAuthHeader(),
                query = query,
                maxResults = maxResults
            )

            if (!listResp.isSuccessful) {
                return@withContext Result.failure(RuntimeException("Gmail API error: ${listResp.code()} ${listResp.message()}"))
            }

            val refs = listResp.body()?.messages ?: emptyList()
            val emails = mutableListOf<EmailItem>()

            for (ref in refs) {
                val detailResp = apiService.getMessage(
                    authHeader = getAuthHeader(),
                    id = ref.id
                )
                if (detailResp.isSuccessful && detailResp.body() != null) {
                    val detail = detailResp.body()!!
                    emails.add(parseMessageDetail(detail))
                }
            }

            Result.success(emails)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markAsRead(messageId: String, isRead: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext Result.failure(IllegalStateException("Gmail not connected"))
        try {
            val req = if (isRead) {
                GmailModifyRequest(removeLabelIds = listOf("UNREAD"))
            } else {
                GmailModifyRequest(addLabelIds = listOf("UNREAD"))
            }
            val resp = apiService.modifyMessage(getAuthHeader(), messageId, req)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Failed to modify read state"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun starEmail(messageId: String, isStarred: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext Result.failure(IllegalStateException("Gmail not connected"))
        try {
            val req = if (isStarred) {
                GmailModifyRequest(addLabelIds = listOf("STARRED"))
            } else {
                GmailModifyRequest(removeLabelIds = listOf("STARRED"))
            }
            val resp = apiService.modifyMessage(getAuthHeader(), messageId, req)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Failed to modify star"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun archiveEmail(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext Result.failure(IllegalStateException("Gmail not connected"))
        try {
            val req = GmailModifyRequest(removeLabelIds = listOf("INBOX"))
            val resp = apiService.modifyMessage(getAuthHeader(), messageId, req)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Failed to archive email"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun trashEmail(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext Result.failure(IllegalStateException("Gmail not connected"))
        try {
            val resp = apiService.trashMessage(getAuthHeader(), messageId)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Failed to trash email"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePermanently(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext Result.failure(IllegalStateException("Gmail not connected"))
        try {
            val resp = apiService.deleteMessagePermanently(getAuthHeader(), messageId)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Failed to permanently delete email"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDraft(to: String, subject: String, body: String, threadId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext Result.failure(IllegalStateException("Gmail not connected"))
        try {
            val rawRfc822 = buildRawRfc822Message(to, subject, body)
            val base64Raw = Base64.encodeToString(rawRfc822.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
            val req = GmailDraftRequest(message = GmailRawMessage(raw = base64Raw, threadId = threadId))
            val resp = apiService.createDraft(getAuthHeader(), req)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!.id)
            } else {
                Result.failure(RuntimeException("Failed to create draft in Gmail"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendEmail(to: String, subject: String, body: String, threadId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        if (!_isConnected.value) return@withContext Result.failure(IllegalStateException("Gmail not connected"))
        try {
            val rawRfc822 = buildRawRfc822Message(to, subject, body)
            val base64Raw = Base64.encodeToString(rawRfc822.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
            val req = GmailRawMessage(raw = base64Raw, threadId = threadId)
            val resp = apiService.sendMessage(getAuthHeader(), req)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!.id)
            } else {
                Result.failure(RuntimeException("Failed to send message via Gmail API"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRawRfc822Message(to: String, subject: String, body: String): String {
        return "To: $to\r\nSubject: $subject\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n$body"
    }

    private fun parseMessageDetail(detail: GmailMessageDetail): EmailItem {
        val headers = detail.payload?.headers ?: emptyList()
        val subject = headers.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: "No Subject"
        val fromHeader = headers.find { it.name.equals("From", ignoreCase = true) }?.value ?: "Unknown Sender"

        var senderName = fromHeader
        var senderEmail = fromHeader
        if (fromHeader.contains("<") && fromHeader.contains(">")) {
            senderName = fromHeader.substringBefore("<").trim().removeSurrounding("\"")
            senderEmail = fromHeader.substringAfter("<").substringBefore(">").trim()
        }

        val snippet = detail.snippet ?: ""
        val timestamp = detail.internalDate?.toLongOrNull() ?: System.currentTimeMillis()
        val labelIds = detail.labelIds ?: emptyList()
        val isRead = !labelIds.contains("UNREAD")
        val isStarred = labelIds.contains("STARRED")
        val isArchived = !labelIds.contains("INBOX")

        // Extract body text if present
        val body = extractBodyText(detail.payload) ?: snippet

        // Categorize based on labels & subject
        val category = when {
            labelIds.contains("CATEGORY_PROMOTIONS") -> EmailCategory.PROMOTIONS
            labelIds.contains("IMPORTANT") || subject.contains("urgent", ignoreCase = true) -> EmailCategory.IMPORTANT
            subject.contains("action required", ignoreCase = true) -> EmailCategory.ACTION_REQUIRED
            subject.contains("deadline", ignoreCase = true) || subject.contains("exam", ignoreCase = true) -> EmailCategory.DEADLINES
            subject.contains("meeting", ignoreCase = true) || subject.contains("sync", ignoreCase = true) -> EmailCategory.MEETINGS
            else -> EmailCategory.PERSONAL
        }

        return EmailItem(
            id = detail.id,
            senderName = senderName.ifBlank { "Sender" },
            senderEmail = senderEmail,
            subject = subject,
            body = body,
            snippet = snippet,
            receivedTimestamp = timestamp,
            category = category,
            isRead = isRead,
            isStarred = isStarred,
            isArchived = isArchived,
            labels = labelIds,
            threadId = detail.threadId
        )
    }

    private fun extractBodyText(payload: GmailPayload?): String? {
        if (payload == null) return null
        payload.body?.data?.let { data ->
            return decodeBase64Safe(data)
        }
        payload.parts?.forEach { part ->
            if (part.mimeType == "text/plain") {
                part.body?.data?.let { data ->
                    return decodeBase64Safe(data)
                }
            }
        }
        return null
    }

    private fun decodeBase64Safe(base64Str: String): String {
        return try {
            val bytes = Base64.decode(base64Str, Base64.URL_SAFE or Base64.NO_WRAP)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            base64Str
        }
    }
}
