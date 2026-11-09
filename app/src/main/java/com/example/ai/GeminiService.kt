package com.example.ai

import com.example.BuildConfig
import com.example.model.AiEmailAnalysis
import com.example.model.AssistantLanguage
import com.example.model.EmailCategory
import com.example.model.EmailItem
import com.example.model.ExtractedTask
import com.example.model.PendingActionConfirmation
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.ToolName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeEmail(email: EmailItem): AiEmailAnalysis = withContext(Dispatchers.IO) {
        val apiKey: String = try {
            val field = BuildConfig::class.java.getDeclaredField("GEMINI_API_KEY")
            field.isAccessible = true
            field.get(null) as? String ?: ""
        } catch (_: Throwable) {
            ""
        }

        if (!apiKey.isNullOrBlank() && !apiKey.contains("PLACEHOLDER") && !apiKey.contains("MY_GEMINI_API_KEY")) {
            try {
                val result = callGeminiEmailAnalysis(email, apiKey)
                if (result != null) return@withContext result
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fallbackEmailAnalysis(email)
    }

    private fun callGeminiEmailAnalysis(email: EmailItem, apiKey: String): AiEmailAnalysis? {
        val prompt = """
            You are MailMind AI, an autonomous personal productivity and email executive intelligence engine.
            Analyze the following email and return a strictly valid JSON response without markdown backticks:
            
            Sender: ${email.senderName} (${email.senderEmail})
            Subject: ${email.subject}
            Content:
            ${email.body}
            
            JSON schema:
            {
              "summary": "Concise 1-2 sentence executive summary of the email",
              "category": "IMPORTANT" | "ACTION_REQUIRED" | "DEADLINES" | "MEETINGS" | "PERSONAL" | "PROMOTIONS",
              "priority": "URGENT" | "HIGH" | "MEDIUM" | "LOW",
              "hasActionRequired": true | false,
              "tasks": [
                {
                  "title": "Clear action-oriented task title",
                  "description": "Specific details or requirements",
                  "deadlineText": "Detected deadline text or null",
                  "deadlineIsoOffsetHours": integer (hours from now, e.g. 24 for tomorrow, or -1 if no deadline or ambiguous),
                  "priority": "URGENT" | "HIGH" | "MEDIUM" | "LOW",
                  "requiredAction": "Action verb phrase",
                  "isDateAmbiguous": true | false,
                  "ambiguityReason": "Why date is ambiguous if true, else null"
                }
              ],
              "keyDates": ["array of detected date strings"],
              "suggestedReply": "Professional short suggested reply draft if applicable"
            }
            
            CRITICAL RULE: Never invent missing deadlines. If an email says 'soon' or 'sometime next week', mark isDateAmbiguous as true.
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val requestBodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return null

        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null
        val text = parts.getJSONObject(0).optString("text")

        val cleanJsonText = text.replace("```json", "").replace("```", "").trim()
        val parsed = JSONObject(cleanJsonText)

        val summary = parsed.optString("summary", email.snippet)
        val catStr = parsed.optString("category", email.category.name)
        val category = try { EmailCategory.valueOf(catStr) } catch (_: Exception) { EmailCategory.ACTION_REQUIRED }
        val prioStr = parsed.optString("priority", "MEDIUM")
        val priority = try { TaskPriority.valueOf(prioStr) } catch (_: Exception) { TaskPriority.MEDIUM }
        val hasAction = parsed.optBoolean("hasActionRequired", true)

        val tasksArray = parsed.optJSONArray("tasks")
        val tasks = mutableListOf<ExtractedTask>()
        val now = System.currentTimeMillis()

        if (tasksArray != null) {
            for (i in 0 until tasksArray.length()) {
                val tObj = tasksArray.getJSONObject(i)
                val hoursOffset = tObj.optInt("deadlineIsoOffsetHours", -1)
                val deadlineMillis = if (hoursOffset > 0) now + (hoursOffset * 3600 * 1000L) else null
                val taskPrioStr = tObj.optString("priority", "MEDIUM")
                val taskPrio = try { TaskPriority.valueOf(taskPrioStr) } catch (_: Exception) { TaskPriority.MEDIUM }

                tasks.add(
                    ExtractedTask(
                        title = tObj.optString("title", email.subject),
                        description = tObj.optString("description", ""),
                        deadlineMillis = deadlineMillis,
                        deadlineText = tObj.optString("deadlineText", null),
                        priority = taskPrio,
                        requiredAction = tObj.optString("requiredAction", "Review"),
                        isDateAmbiguous = tObj.optBoolean("isDateAmbiguous", false),
                        ambiguityReason = if (tObj.has("ambiguityReason") && !tObj.isNull("ambiguityReason")) tObj.getString("ambiguityReason") else null
                    )
                )
            }
        }

        val keyDatesArray = parsed.optJSONArray("keyDates")
        val keyDates = mutableListOf<String>()
        if (keyDatesArray != null) {
            for (i in 0 until keyDatesArray.length()) {
                keyDates.add(keyDatesArray.getString(i))
            }
        }

        return AiEmailAnalysis(
            emailId = email.id,
            summary = summary,
            detectedCategory = category,
            priority = priority,
            extractedTasks = tasks,
            keyDates = keyDates,
            hasActionRequired = hasAction,
            rawJson = cleanJsonText
        )
    }

    fun fallbackEmailAnalysis(email: EmailItem): AiEmailAnalysis {
        val lowerBody = (email.subject + " " + email.body).lowercase()
        val isUrgent = lowerBody.contains("urgent") || lowerBody.contains("asap") || lowerBody.contains("immediately")
        val isActionReq = lowerBody.contains("action required") || lowerBody.contains("approve") || lowerBody.contains("review") || lowerBody.contains("sign")
        val isDeadline = lowerBody.contains("deadline") || lowerBody.contains("due") || lowerBody.contains("by tomorrow") || lowerBody.contains("by friday")

        val priority = when {
            isUrgent -> TaskPriority.URGENT
            isActionReq -> TaskPriority.HIGH
            isDeadline -> TaskPriority.HIGH
            else -> TaskPriority.MEDIUM
        }

        val category = when {
            email.subject.startsWith("Action Required", ignoreCase = true) || isActionReq -> EmailCategory.ACTION_REQUIRED
            isUrgent || lowerBody.contains("board") -> EmailCategory.IMPORTANT
            isDeadline -> EmailCategory.DEADLINES
            lowerBody.contains("meeting") || lowerBody.contains("retrospective") || lowerBody.contains("sync") -> EmailCategory.MEETINGS
            lowerBody.contains("flight") || lowerBody.contains("hotel") || lowerBody.contains("reservation") -> EmailCategory.PERSONAL
            else -> email.category
        }

        var deadlineMillis: Long? = null
        var deadlineText: String? = null
        var isAmbiguous = false
        var ambiguityReason: String? = null

        val timeRegex = Pattern.compile("(\\d{1,2})(:(\\d{2}))?\\s*(am|pm)", Pattern.CASE_INSENSITIVE)
        val timeMatcher = timeRegex.matcher(lowerBody)
        var parsedHour = 17
        var parsedMin = 0
        if (timeMatcher.find()) {
            val rawHour = timeMatcher.group(1)?.toIntOrNull() ?: 5
            val rawMin = timeMatcher.group(3)?.toIntOrNull() ?: 0
            val ampm = timeMatcher.group(4)?.lowercase() ?: "pm"
            parsedHour = if (ampm == "pm" && rawHour < 12) rawHour + 12 else if (ampm == "am" && rawHour == 12) 0 else rawHour
            parsedMin = rawMin
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parsedHour)
            set(Calendar.MINUTE, parsedMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when {
            lowerBody.contains("tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                deadlineMillis = cal.timeInMillis
                deadlineText = "Tomorrow at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
            }
            lowerBody.contains("friday") -> {
                val todayDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val diffToFriday = (Calendar.FRIDAY - todayDayOfWeek + 7) % 7
                cal.add(Calendar.DAY_OF_YEAR, if (diffToFriday == 0) 7 else diffToFriday)
                deadlineMillis = cal.timeInMillis
                deadlineText = "Friday at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
            }
            lowerBody.contains("wednesday") -> {
                val todayDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val diff = (Calendar.WEDNESDAY - todayDayOfWeek + 7) % 7
                cal.add(Calendar.DAY_OF_YEAR, if (diff == 0) 7 else diff)
                deadlineMillis = cal.timeInMillis
                deadlineText = "Wednesday at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
            }
            lowerBody.contains("thursday") -> {
                val todayDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val diff = (Calendar.THURSDAY - todayDayOfWeek + 7) % 7
                cal.add(Calendar.DAY_OF_YEAR, if (diff == 0) 7 else diff)
                deadlineMillis = cal.timeInMillis
                deadlineText = "Thursday at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
            }
            lowerBody.contains("soon") || lowerBody.contains("sometime next week") || lowerBody.contains("as soon as you can") -> {
                isAmbiguous = true
                ambiguityReason = "Email mentions 'soon / next week' without an exact date. Please confirm deadline."
            }
            else -> {
                if (email.extractedDeadline != null) {
                    deadlineMillis = email.extractedDeadline
                    val fmt = SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault())
                    deadlineText = fmt.format(deadlineMillis)
                }
            }
        }

        val taskTitle = when {
            !email.extractedTaskTitle.isNullOrBlank() -> email.extractedTaskTitle
            email.subject.contains(":") -> email.subject.substringAfter(":").trim()
            else -> email.subject
        }

        val tasks = listOf(
            ExtractedTask(
                title = taskTitle,
                description = email.snippet,
                deadlineMillis = deadlineMillis,
                deadlineText = deadlineText,
                priority = priority,
                requiredAction = if (isActionReq) "Action & Approval" else "Review & Follow-up",
                isDateAmbiguous = isAmbiguous,
                ambiguityReason = ambiguityReason
            )
        )

        return AiEmailAnalysis(
            emailId = email.id,
            summary = "Summary: ${email.snippet} (Sender: ${email.senderName})",
            detectedCategory = category,
            priority = priority,
            extractedTasks = tasks,
            keyDates = listOfNotNull(deadlineText),
            hasActionRequired = isActionReq || isDeadline,
            rawJson = "{}"
        )
    }

    data class VoiceAssistantResponse(
        val spokenText: String,
        val actionType: VoiceActionType = VoiceActionType.NONE,
        val toolName: ToolName? = null,
        val targetTaskId: String? = null,
        val newTaskTitle: String? = null,
        val newTaskDeadlineMillis: Long? = null,
        val targetCategory: EmailCategory? = null,
        val targetScreenName: String? = null,
        val pendingConfirmation: PendingActionConfirmation? = null,
        val draftSubject: String? = null,
        val draftRecipient: String? = null,
        val draftBody: String? = null,
        val emailQuery: String? = null,
        val matchedEmail: EmailItem? = null
    )

    enum class VoiceActionType {
        NONE,
        READ_IMPORTANT_EMAILS,
        READ_LATEST_EMAILS,
        SEARCH_EMAILS,
        SUMMARIZE_THREAD,
        MARK_TASK_COMPLETE,
        MARK_TASK_INCOMPLETE,
        SNOOZE_REMINDER,
        CREATE_TASK,
        GIVE_BRIEFING,
        NAVIGATE_INBOX,
        NAVIGATE_TASKS,
        NAVIGATE_CALENDAR,
        NAVIGATE_SETTINGS,
        CREATE_DRAFT,
        STAGE_SEND_EMAIL,
        STAGE_DELETE_EMAIL,
        STAGE_ARCHIVE_EMAIL,
        EXECUTE_CONFIRMED_ACTION,
        CANCEL_CONFIRMED_ACTION,
        STAR_EMAIL,
        MARK_EMAIL_UNREAD,
        TRIGGER_ALARM_PREVIEW,
        OUT_OF_DOMAIN
    }

    suspend fun processVoiceCommand(
        query: String,
        tasks: List<TaskItem>,
        emails: List<EmailItem>,
        language: AssistantLanguage = AssistantLanguage.ENGLISH,
        activePendingConfirmation: PendingActionConfirmation? = null
    ): VoiceAssistantResponse = withContext(Dispatchers.Default) {
        val q = query.lowercase().trim()
        val isHindi = language == AssistantLanguage.HINDI || containsHindi(query)

        // 0. CHECK PENDING CONFIRMATIONS (Explicit user confirmation flow for sensitive operations)
        if (activePendingConfirmation != null) {
            if (AiActionFramework.isAffirmative(query)) {
                val confirmText = if (isHindi) {
                    "कन्फर्म! मैंने ${activePendingConfirmation.toolName.displayName} की कार्रवाई सफलतापूर्वक पूरी कर दी है।"
                } else {
                    "Confirmed. Executed ${activePendingConfirmation.toolName.displayName} as requested."
                }
                return@withContext VoiceAssistantResponse(
                    spokenText = confirmText,
                    actionType = VoiceActionType.EXECUTE_CONFIRMED_ACTION,
                    toolName = activePendingConfirmation.toolName,
                    pendingConfirmation = activePendingConfirmation
                )
            } else if (AiActionFramework.isNegative(query)) {
                val cancelText = if (isHindi) {
                    "रद्द कर दिया गया। कोई बदलाव नहीं किया गया।"
                } else {
                    "Cancelled. The action was aborted and no changes were made."
                }
                return@withContext VoiceAssistantResponse(
                    spokenText = cancelText,
                    actionType = VoiceActionType.CANCEL_CONFIRMED_ACTION,
                    toolName = activePendingConfirmation.toolName,
                    pendingConfirmation = activePendingConfirmation
                )
            }
        }

        val overdueTasks = tasks.filter { it.isOverdue() }
        val importantEmails = emails.filter { it.category == EmailCategory.IMPORTANT || it.category == EmailCategory.ACTION_REQUIRED }

        // 1. Navigation Commands (Full App Control)
        if (q.contains("go to inbox") || q.contains("open inbox") || q.contains("show emails") || q.contains("view inbox") ||
            q.contains("इनबॉक्स खोलो") || q.contains("ईमेल दिखाओ")) {
            val text = if (isHindi) "स्मार्ट इनबॉक्स खोला जा रहा है।" else "Opening your Smart Inbox now."
            return@withContext VoiceAssistantResponse(
                spokenText = text,
                actionType = VoiceActionType.NAVIGATE_INBOX,
                targetScreenName = "INBOX"
            )
        }
        if (q.contains("go to tasks") || q.contains("open tasks") || q.contains("show tasks") || q.contains("view tasks") ||
            q.contains("टास्क खोलो") || q.contains("कार्य दिखाओ")) {
            val text = if (isHindi) "टास्क डैशबोर्ड खोला जा रहा है।" else "Navigating to your Tasks dashboard."
            return@withContext VoiceAssistantResponse(
                spokenText = text,
                actionType = VoiceActionType.NAVIGATE_TASKS,
                targetScreenName = "TASKS"
            )
        }
        if (q.contains("go to calendar") || q.contains("open calendar") || q.contains("show calendar") || q.contains("view calendar") ||
            q.contains("कैलेंडर खोलो")) {
            val text = if (isHindi) "कैलेंडर अनुसूची खोली जा रही है।" else "Opening your deadline Calendar schedule."
            return@withContext VoiceAssistantResponse(
                spokenText = text,
                actionType = VoiceActionType.NAVIGATE_CALENDAR,
                targetScreenName = "CALENDAR"
            )
        }
        if (q.contains("go to settings") || q.contains("open settings") || q.contains("सेटिंग्स खोलो")) {
            val text = if (isHindi) "सेटिंग्स खोली जा रही हैं।" else "Opening Settings."
            return@withContext VoiceAssistantResponse(
                spokenText = text,
                actionType = VoiceActionType.NAVIGATE_SETTINGS,
                targetScreenName = "SETTINGS"
            )
        }

        // 2. SEARCH & FIND SPECIFIC EMAILS
        // "Find all emails from my placement cell", "Find the email containing my examination schedule"
        if (q.contains("find") || q.contains("search") || q.contains("ढूँढो") || q.contains("सर्च करो")) {
            val searchTerm = when {
                q.contains("placement") -> "placement"
                q.contains("exam") -> "exam"
                q.contains("assignment") -> "assignment"
                q.contains("meeting") -> "meeting"
                q.contains("flight") || q.contains("ticket") -> "flight"
                else -> q.replace("find", "").replace("search", "").replace("all emails from", "").replace("emails containing", "").trim()
            }

            val matches = emails.filter {
                it.subject.contains(searchTerm, ignoreCase = true) ||
                        it.senderName.contains(searchTerm, ignoreCase = true) ||
                        it.body.contains(searchTerm, ignoreCase = true)
            }

            if (matches.isNotEmpty()) {
                val count = matches.size
                val first = matches.first()
                val spoken = if (isHindi) {
                    "मुझे '$searchTerm' से संबंधित $count ईमेल मिले हैं। मुख्य ईमेल '${first.subject}' है प्रेषक ${first.senderName} से।"
                } else {
                    "I found $count email${if (count > 1) "s" else ""} matching '$searchTerm'. The most relevant is '${first.subject}' from ${first.senderName}."
                }
                return@withContext VoiceAssistantResponse(
                    spokenText = spoken,
                    actionType = VoiceActionType.SEARCH_EMAILS,
                    toolName = ToolName.SEARCH_EMAILS,
                    emailQuery = searchTerm,
                    matchedEmail = first
                )
            } else {
                val spoken = if (isHindi) {
                    "मुझे '$searchTerm' से मेल खाने वाला कोई ईमेल नहीं मिला।"
                } else {
                    "I searched your Gmail for '$searchTerm' but found no matching messages."
                }
                return@withContext VoiceAssistantResponse(
                    spokenText = spoken,
                    actionType = VoiceActionType.SEARCH_EMAILS,
                    toolName = ToolName.SEARCH_EMAILS,
                    emailQuery = searchTerm
                )
            }
        }

        // 3. READ LATEST / IMPORTANT EMAILS
        // "Read my latest emails", "Read the important emails I received today"
        if ((q.contains("read") && (q.contains("email") || q.contains("inbox") || q.contains("message"))) ||
            q.contains("ईमेल पढ़ो") || q.contains("ज़रूरी ईमेल")) {
            val count = importantEmails.size
            if (count == 0) {
                val text = if (isHindi) {
                    "आपके पास कोई अपठित महत्वपूर्ण ईमेल नहीं है।"
                } else {
                    "You have no unread important emails. Your inbox is clean!"
                }
                return@withContext VoiceAssistantResponse(
                    spokenText = text,
                    actionType = VoiceActionType.READ_IMPORTANT_EMAILS,
                    toolName = ToolName.READ_EMAIL
                )
            }
            val topEmail = importantEmails.first()
            val spoken = if (isHindi) {
                "आपके पास $count महत्वपूर्ण ईमेल हैं। सबसे ज़रूरी ईमेल ${topEmail.senderName} से है विषय '${topEmail.subject}'। क्या आप इसका कार्य बनाना चाहते हैं?"
            } else {
                "You have $count important emails. The highest priority is from ${topEmail.senderName} regarding '${topEmail.subject}'. Would you like me to extract an actionable task?"
            }
            return@withContext VoiceAssistantResponse(
                spokenText = spoken,
                actionType = VoiceActionType.READ_IMPORTANT_EMAILS,
                toolName = ToolName.READ_EMAIL,
                matchedEmail = topEmail
            )
        }

        // 4. SUMMARIZE CONVERSATION / THREAD
        // "Summarize this entire conversation"
        if (q.contains("summarize") || q.contains("summary") || q.contains("सारांश")) {
            val topEmail = importantEmails.firstOrNull() ?: emails.firstOrNull()
            if (topEmail != null) {
                val spoken = if (isHindi) {
                    "ईमेल सारांश: ${topEmail.subject}। मुख्य विवरण: ${topEmail.snippet}।"
                } else {
                    "Conversation Summary for '${topEmail.subject}': ${topEmail.snippet}. Action required by the sender."
                }
                return@withContext VoiceAssistantResponse(
                    spokenText = spoken,
                    actionType = VoiceActionType.SUMMARIZE_THREAD,
                    toolName = ToolName.SUMMARIZE_THREAD,
                    matchedEmail = topEmail
                )
            }
        }

        // 5. DRAFT & REPLY TO EMAIL
        // "Reply to this email saying I will attend the meeting", "Draft an email requesting an extension"
        if (q.contains("reply") || q.contains("draft") || q.contains("ड्राफ्ट") || q.contains("उत्तर")) {
            val topEmail = importantEmails.firstOrNull() ?: emails.firstOrNull()
            val recipient = topEmail?.senderEmail ?: "placement.cell@university.edu"
            val subject = "Re: " + (topEmail?.subject ?: "Follow-up")

            val body = when {
                q.contains("attend") || q.contains("meeting") -> "Hello, thank you for the invite. I confirm that I will attend the meeting as scheduled. Best regards."
                q.contains("extension") -> "Dear Team, I am writing to respectfully request a short extension on the submission deadline. Thank you for your consideration."
                else -> "Hello, thank you for reaching out. I have reviewed the details and will proceed accordingly."
            }

            val spoken = if (isHindi) {
                "मैंने $recipient के लिए विषय '$subject' के साथ ड्राफ्ट तैयार कर दिया है: '$body'। क्या आप इसे भेजना चाहते हैं?"
            } else {
                "I've drafted a reply to $recipient with subject '$subject'. Content: '$body'. Ready to send whenever you confirm."
            }

            return@withContext VoiceAssistantResponse(
                spokenText = spoken,
                actionType = VoiceActionType.CREATE_DRAFT,
                toolName = ToolName.CREATE_EMAIL_DRAFT,
                draftRecipient = recipient,
                draftSubject = subject,
                draftBody = body,
                matchedEmail = topEmail
            )
        }

        // 6. SENSITIVE: SEND EMAIL (Requires explicit user confirmation)
        // "Send this email to the recipient"
        if (q.contains("send this email") || q.contains("send email") || q.contains("ईमेल भेजो")) {
            val recipient = "placement.cell@university.edu"
            val subject = "Meeting Confirmation"
            val body = "I confirm that I will attend the scheduled meeting. Thank you."

            val spokenPrompt = if (isHindi) {
                "पुष्टि आवश्यक: मैं $recipient को विषय '$subject' के साथ ईमेल भेजने के लिए तैयार हूँ। क्या मैं इसे भेज दूँ?"
            } else {
                "Confirmation required: I am ready to send an email to $recipient with subject '$subject'. Should I proceed and send it now?"
            }

            val staged = AiActionFramework.stageActionForConfirmation(
                toolName = ToolName.SEND_EMAIL,
                description = "Send email to $recipient ($subject)",
                spokenPrompt = spokenPrompt,
                parameters = mapOf("to" to recipient, "subject" to subject, "body" to body)
            )

            return@withContext VoiceAssistantResponse(
                spokenText = spokenPrompt,
                actionType = VoiceActionType.STAGE_SEND_EMAIL,
                toolName = ToolName.SEND_EMAIL,
                pendingConfirmation = staged,
                draftRecipient = recipient,
                draftSubject = subject,
                draftBody = body
            )
        }

        // 7. SENSITIVE: DELETE / PERMANENTLY DELETE EMAIL
        if (q.contains("delete") || q.contains("हटाओ") || q.contains("डिलीट")) {
            val isPermanent = q.contains("permanent") || q.contains("permanently") || q.contains("हमेशा के लिए")
            val target = emails.firstOrNull()
            val targetId = target?.id ?: "msg-001"
            val targetSubject = target?.subject ?: "Current Email"

            val spokenPrompt = if (isPermanent) {
                if (isHindi) {
                    "चेतावनी: विषय '$targetSubject' वाले ईमेल को हमेशा के लिए हटाया जा रहा है। इसे वापस नहीं लाया जा सकेगा। क्या आप निश्चित रूप से हटाना चाहते हैं?"
                } else {
                    "Warning: This will permanently delete the email '$targetSubject' from Gmail and cannot be recovered. Are you absolutely certain you want to proceed?"
                }
            } else {
                if (isHindi) {
                    "पुष्टि आवश्यक: क्या आप विषय '$targetSubject' वाले ईमेल को हटाना चाहते हैं?"
                } else {
                    "Confirmation required: Are you sure you want to delete the email '$targetSubject'?"
                }
            }

            val staged = AiActionFramework.stageActionForConfirmation(
                toolName = ToolName.DELETE_EMAIL,
                description = if (isPermanent) "Permanently delete $targetSubject" else "Trash $targetSubject",
                spokenPrompt = spokenPrompt,
                parameters = mapOf("emailId" to targetId, "permanent" to isPermanent),
                requiresDoubleWarning = isPermanent
            )

            return@withContext VoiceAssistantResponse(
                spokenText = spokenPrompt,
                actionType = VoiceActionType.STAGE_DELETE_EMAIL,
                toolName = ToolName.DELETE_EMAIL,
                pendingConfirmation = staged
            )
        }

        // 8. SENSITIVE: ARCHIVE PROMOTIONAL / BATCH EMAILS
        // "Archive all promotional emails from yesterday"
        if (q.contains("archive") || q.contains("आर्काइव")) {
            val spokenPrompt = if (isHindi) {
                "पुष्टि आवश्यक: क्या आप इनबॉक्स से सभी प्रमोशनल ईमेल आर्काइव करना चाहते हैं?"
            } else {
                "Confirmation required: Would you like me to archive all promotional emails from your inbox?"
            }

            val staged = AiActionFramework.stageActionForConfirmation(
                toolName = ToolName.ARCHIVE_EMAIL,
                description = "Archive promotional emails",
                spokenPrompt = spokenPrompt,
                parameters = mapOf("category" to "PROMOTIONS")
            )

            return@withContext VoiceAssistantResponse(
                spokenText = spokenPrompt,
                actionType = VoiceActionType.STAGE_ARCHIVE_EMAIL,
                toolName = ToolName.ARCHIVE_EMAIL,
                pendingConfirmation = staged
            )
        }

        // 9. STAR / UNREAD ACTIONS
        // "Mark this email as unread", "Star this email"
        if (q.contains("unread") || q.contains("अनरीड")) {
            val topEmail = emails.firstOrNull()
            val spoken = if (isHindi) {
                "ईमेल '${topEmail?.subject ?: "ईमेल"}' को अनरीड मार्क कर दिया गया है।"
            } else {
                "Marked '${topEmail?.subject ?: "email"}' as unread."
            }
            return@withContext VoiceAssistantResponse(
                spokenText = spoken,
                actionType = VoiceActionType.MARK_EMAIL_UNREAD,
                toolName = ToolName.MARK_AS_UNREAD,
                matchedEmail = topEmail
            )
        }
        if (q.contains("star") || q.contains("तारा")) {
            val topEmail = emails.firstOrNull()
            val spoken = if (isHindi) {
                "ईमेल '${topEmail?.subject ?: "ईमेल"}' को स्टार कर दिया गया है।"
            } else {
                "Starred '${topEmail?.subject ?: "email"}' in Gmail."
            }
            return@withContext VoiceAssistantResponse(
                spokenText = spoken,
                actionType = VoiceActionType.STAR_EMAIL,
                toolName = ToolName.STAR_EMAIL,
                matchedEmail = topEmail
            )
        }

        // 10. UNANSWERED IMPORTANT EMAILS / MOST URGENT EMAIL
        // "Do I have any unanswered important emails?", "Which email requires the most urgent response?"
        if (q.contains("unanswered") || q.contains("most urgent") || q.contains("urgent response") || q.contains("अनुत्तरित")) {
            val urgentEmail = importantEmails.firstOrNull()
            if (urgentEmail != null) {
                val spoken = if (isHindi) {
                    "सबसे महत्वपूर्ण अनुत्तरित ईमेल ${urgentEmail.senderName} से है: '${urgentEmail.subject}'। इसमें कार्रवाई की आवश्यकता है।"
                } else {
                    "The most urgent unanswered email is from ${urgentEmail.senderName} regarding '${urgentEmail.subject}'. It requires action."
                }
                return@withContext VoiceAssistantResponse(
                    spokenText = spoken,
                    actionType = VoiceActionType.READ_IMPORTANT_EMAILS,
                    toolName = ToolName.READ_EMAIL,
                    matchedEmail = urgentEmail
                )
            } else {
                val spoken = if (isHindi) {
                    "आपके पास कोई भी ज़रूरी अनुत्तरित ईमेल नहीं है।"
                } else {
                    "You have no unanswered urgent emails right now."
                }
                return@withContext VoiceAssistantResponse(spokenText = spoken)
            }
        }

        // 11. "Which tasks are overdue?" / "overdue tasks"
        if (q.contains("overdue") || (q.contains("late") && q.contains("task")) || q.contains("बकाया")) {
            if (overdueTasks.isEmpty()) {
                val text = if (isHindi) "शानदार! आपका कोई भी कार्य बकाया नहीं है।" else "Great news! You have zero overdue tasks."
                return@withContext VoiceAssistantResponse(
                    spokenText = text,
                    actionType = VoiceActionType.NONE,
                    toolName = ToolName.GET_PENDING_TASKS
                )
            }
            val overdueNames = overdueTasks.take(2).joinToString(", ") { it.title }
            val count = overdueTasks.size
            val text = if (isHindi) {
                "आपके $count कार्य बकाया हैं: $overdueNames। क्या आप अलार्म स्नूज़ करना चाहते हैं?"
            } else {
                "You have $count overdue task${if (count > 1) "s" else ""}: $overdueNames. Proactive alarms have been notified."
            }
            return@withContext VoiceAssistantResponse(
                spokenText = text,
                actionType = VoiceActionType.NONE,
                toolName = ToolName.GET_PENDING_TASKS
            )
        }

        // 12. "What are my pending tasks?" / "What deadlines do I have tomorrow?"
        if (q.contains("tomorrow") || q.contains("upcoming") || q.contains("deadline") || q.contains("pending tasks") ||
            q.contains("कल की डेडलाइन") || q.contains("पेंडिंग टास्क")) {
            val pendingWithDeadlines = tasks.filter { it.status != TaskStatus.COMPLETED && it.deadlineMillis != null }
            if (pendingWithDeadlines.isEmpty()) {
                val text = if (isHindi) "वर्तमान में कोई आगामी डेडलाइन नहीं है।" else "You have no pending deadlines scheduled right now."
                return@withContext VoiceAssistantResponse(
                    spokenText = text,
                    actionType = VoiceActionType.NONE,
                    toolName = ToolName.GET_UPCOMING_DEADLINES
                )
            }
            val next = pendingWithDeadlines.minByOrNull { it.deadlineMillis ?: Long.MAX_VALUE }!!
            val dateStr = SimpleDateFormat("EEEE 'at' h:mm a", Locale.getDefault()).format(next.deadlineMillis)
            val timeLeft = next.getTimeRemainingText()
            val text = if (isHindi) {
                "आपकी अगली मुख्य समय सीमा '${next.title}' है, $dateStr ($timeLeft)। प्राथमिकता ${next.priority.displayName} है।"
            } else {
                "Your next critical deadline is '${next.title}', due $dateStr ($timeLeft). Priority is ${next.priority.displayName}."
            }
            return@withContext VoiceAssistantResponse(
                spokenText = text,
                actionType = VoiceActionType.NONE,
                toolName = ToolName.GET_UPCOMING_DEADLINES,
                targetTaskId = next.id
            )
        }

        // 13. "Mark [task] as completed" / "Complete [task]"
        if (q.contains("mark") && q.contains("complete") || q.contains("done") || q.contains("finish") ||
            q.contains("टास्क पूरा") || q.contains("कार्य पूरा")) {
            val pending = tasks.filter { it.status != TaskStatus.COMPLETED }
            val matched = pending.find { task ->
                val titleTokens = task.title.lowercase().split(" ")
                titleTokens.any { token -> token.length > 3 && q.contains(token) }
            } ?: pending.firstOrNull()

            if (matched != null) {
                val text = if (isHindi) {
                    "मैंने कार्य '${matched.title}' को पूर्ण मार्क कर दिया है।"
                } else {
                    "I've marked '${matched.title}' as completed."
                }
                return@withContext VoiceAssistantResponse(
                    spokenText = text,
                    actionType = VoiceActionType.MARK_TASK_COMPLETE,
                    toolName = ToolName.COMPLETE_TASK,
                    targetTaskId = matched.id
                )
            }
        }

        // 14. "Create a task to submit my assignment on Friday" / "Remind me about this email tomorrow"
        if (q.contains("remind me") || q.contains("create a task") || q.contains("add task") || q.contains("टास्क बनाओ") || q.contains("याद दिलाओ")) {
            val rawTitle = q.replace("remind me about this email", "")
                .replace("remind me to", "")
                .replace("create a task to", "")
                .replace("add task to", "")
                .replace("add task", "")
                .trim()
                .replaceFirstChar { it.uppercase() }

            val title = if (rawTitle.isBlank()) "Follow-up Task" else rawTitle
            val cal = Calendar.getInstance()
            if (q.contains("friday") || q.contains("शुक्रवार")) {
                val diff = (Calendar.FRIDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                cal.add(Calendar.DAY_OF_YEAR, if (diff == 0) 7 else diff)
                cal.set(Calendar.HOUR_OF_DAY, 17)
            } else {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 17)
            }

            val text = if (isHindi) {
                "मैंने '$title' के लिए नया कार्य बना दिया है और 1 दिन, 6 घंटे और 1 घंटे पहले के अलार्म सेट कर दिए हैं।"
            } else {
                "I've created the task '$title' with proactive deadline alarms scheduled at 1 day, 6 hours, and 1 hour remaining."
            }

            return@withContext VoiceAssistantResponse(
                spokenText = text,
                actionType = VoiceActionType.CREATE_TASK,
                toolName = ToolName.CREATE_TASK,
                newTaskTitle = title,
                newTaskDeadlineMillis = cal.timeInMillis
            )
        }

        // 15. "Snooze reminder"
        if (q.contains("snooze") || q.contains("स्नूज़")) {
            val minutes = if (q.contains("10")) 10 else if (q.contains("15")) 15 else if (q.contains("60")) 60 else 30
            val text = if (isHindi) {
                "अलार्म को $minutes मिनट के लिए स्नूज़ कर दिया गया है।"
            } else {
                "Snoozed your upcoming reminder for $minutes minutes."
            }
            return@withContext VoiceAssistantResponse(
                spokenText = text,
                actionType = VoiceActionType.SNOOZE_REMINDER,
                toolName = ToolName.SNOOZE_REMINDER
            )
        }

        // 16. "Morning briefing" / "Brief me"
        if (q.contains("briefing") || q.contains("morning") || q.contains("status") || q.contains("ब्रीफिंग")) {
            val spoken = generateMorningBriefingSpokenText(tasks, emails, isHindi)
            return@withContext VoiceAssistantResponse(
                spokenText = spoken,
                actionType = VoiceActionType.GIVE_BRIEFING
            )
        }

        // 17. Domain boundary check (Strict Out of Domain Rejection & Apology)
        val productivityKeywords = listOf(
            "task", "email", "deadline", "reminder", "schedule", "alarm", "inbox",
            "due", "calendar", "briefing", "snooze", "done", "complete", "priority",
            "action", "urgent", "unread", "filter", "meeting", "mailmind", "draft", "reply", "send", "delete", "archive",
            "कार्य", "ईमेल", "डेडलाइन", "अलार्म", "इनबॉक्स", "कैलेंडर", "ड्राफ्ट"
        )
        val hasProductivityTopic = productivityKeywords.any { q.contains(it) }

        if (!hasProductivityTopic && (
            q.contains("joke") || q.contains("weather") || q.contains("capital") ||
            q.contains("recipe") || q.contains("song") || q.contains("music") ||
            q.contains("movie") || q.contains("pizza") || q.contains("code") ||
            q.contains("python") || q.contains("who is") || q.contains("what is the") ||
            q.contains("चुटकुला") || q.contains("मौसम")
        )) {
            val apology = if (isHindi) {
                "असुविधा के लिए क्षमा करें, लेकिन यह अनुरोध मेलमाइंड उत्पादकता सहायक के रूप में मेरे अधिकार क्षेत्र से बाहर है। मैं आपके ईमेल पढ़ने, कार्यों को प्रबंधित करने और अलार्म शेड्यूल करने में विशेषज्ञ हूँ। मैं आपके ईमेल या शेड्यूल में क्या मदद करूँ?"
            } else {
                "I apologize for the inconvenience, but that request is outside my domain as your MailMind productivity assistant. I am specialized in reading your emails, extracting actionable deadlines, managing your tasks, and scheduling alarms. How can I assist you with your schedule or inbox?"
            }
            return@withContext VoiceAssistantResponse(
                spokenText = apology,
                actionType = VoiceActionType.OUT_OF_DOMAIN
            )
        }

        // Default conversational reply
        val defaultText = if (isHindi) {
            "मैं सुन रहा हूँ। आप मुझसे अपने ईमेल पढ़ने, कार्य बनाने, डेडलाइन चेक करने या सेटिंग्स खोलने के लिए कह सकते हैं।"
        } else {
            "I am listening. You can ask me to read your emails, check deadlines, complete tasks, create drafts, or schedule alarms."
        }
        return@withContext VoiceAssistantResponse(
            spokenText = defaultText,
            actionType = VoiceActionType.NONE
        )
    }

    private fun containsHindi(text: String): Boolean {
        for (char in text) {
            if (char.code in 0x0900..0x097F) return true
        }
        return false
    }

    fun generateMorningBriefingSpokenText(
        tasks: List<TaskItem>,
        emails: List<EmailItem>,
        isHindi: Boolean = false
    ): String {
        val overdue = tasks.count { it.isOverdue() }
        val dueToday = tasks.count { it.isDueToday() }
        val urgentEmails = emails.count { !it.isRead && (it.category == EmailCategory.IMPORTANT || it.category == EmailCategory.ACTION_REQUIRED) }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        if (isHindi) {
            val greeting = if (hour < 12) "सुप्रभात!" else if (hour < 17) "नमस्ते!" else "शुभ संध्या!"
            val sb = StringBuilder("$greeting यह आपकी मेलमाइंड एग्जीक्यूटिव ब्रीफिंग है। ")
            if (overdue > 0) sb.append("आपके $overdue बकाया कार्य हैं। ") else sb.append("कोई बकाया कार्य नहीं है। ")
            if (dueToday > 0) sb.append("आज $dueToday कार्य नियत हैं। ")
            if (urgentEmails > 0) sb.append("इनबॉक्स में $urgentEmails अपठित महत्वपूर्ण ईमेल हैं। ")
            sb.append("सभी कार्यों के लिए 1 दिन, 6 घंटे और 1 घंटे पहले के अलार्म सक्रिय हैं।")
            return sb.toString()
        }

        val greeting = if (hour < 12) "Good morning!" else if (hour < 17) "Good afternoon!" else "Good evening!"
        val sb = StringBuilder("$greeting Here is your MailMind executive briefing. ")

        if (overdue > 0) {
            sb.append("You have $overdue overdue task${if (overdue > 1) "s" else ""} requiring immediate attention. ")
        } else {
            sb.append("You have no overdue items. ")
        }

        if (dueToday > 0) {
            sb.append("You have $dueToday task${if (dueToday > 1) "s" else ""} due today. ")
        }

        if (urgentEmails > 0) {
            sb.append("There are $urgentEmails unread actionable emails in your inbox. ")
        } else {
            sb.append("Your inbox has no pending critical actions. ")
        }

        sb.append("Proactive alarms are set for 1 day, 6 hours, and 1 hour before every deadline.")
        return sb.toString()
    }
}
