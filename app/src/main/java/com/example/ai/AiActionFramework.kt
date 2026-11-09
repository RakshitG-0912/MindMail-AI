package com.example.ai

import com.example.model.ActionStatus
import com.example.model.AiActionLogItem
import com.example.model.AssistantLanguage
import com.example.model.PendingActionConfirmation
import com.example.model.ToolName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AiActionFramework {

    private val _actionHistory = MutableStateFlow<List<AiActionLogItem>>(emptyList())
    val actionHistory: StateFlow<List<AiActionLogItem>> = _actionHistory.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingActionConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingActionConfirmation?> = _pendingConfirmation.asStateFlow()

    fun logAction(
        toolName: ToolName,
        description: String,
        status: ActionStatus,
        parametersSummary: String = "",
        resultSummary: String? = null
    ) {
        val item = AiActionLogItem(
            toolName = toolName.displayName,
            description = description,
            status = status,
            isSensitive = toolName.isSensitive,
            parametersSummary = parametersSummary,
            resultSummary = resultSummary
        )
        _actionHistory.value = listOf(item) + _actionHistory.value.take(49)
    }

    fun stageActionForConfirmation(
        toolName: ToolName,
        description: String,
        spokenPrompt: String,
        parameters: Map<String, Any?>,
        requiresDoubleWarning: Boolean = false
    ): PendingActionConfirmation {
        val pending = PendingActionConfirmation(
            toolName = toolName,
            description = description,
            spokenPrompt = spokenPrompt,
            parameters = parameters,
            requiresDoubleWarning = requiresDoubleWarning
        )
        _pendingConfirmation.value = pending
        logAction(
            toolName = toolName,
            description = "Awaiting confirmation: $description",
            status = ActionStatus.PENDING_CONFIRMATION,
            parametersSummary = parameters.toString()
        )
        return pending
    }

    fun clearPendingConfirmation() {
        _pendingConfirmation.value = null
    }

    fun isAffirmative(text: String): Boolean {
        val q = text.lowercase().trim()
        val englishAffirm = listOf("yes", "yeah", "yep", "sure", "confirm", "send", "proceed", "do it", "approved", "okay", "ok", "go ahead")
        val hindiAffirm = listOf("हाँ", "हां", "भेज दो", "कन्फर्म", "सहमति", "आगे बढ़ो", "कर दो", "ठीक है")
        return englishAffirm.any { q == it || q.startsWith("$it ") || q.endsWith(" $it") } ||
                hindiAffirm.any { q.contains(it) }
    }

    fun isNegative(text: String): Boolean {
        val q = text.lowercase().trim()
        val englishNeg = listOf("no", "nope", "cancel", "don't", "stop", "abort", "reject", "never mind", "dismiss")
        val hindiNeg = listOf("नहीं", "रद्द करो", "मत भेजो", "कैंसिल", "रुको", "रहने दो")
        return englishNeg.any { q == it || q.startsWith("$it ") || q.endsWith(" $it") } ||
                hindiNeg.any { q.contains(it) }
    }
}
