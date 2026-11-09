package com.example.model

data class ExtractedTask(
    val title: String,
    val description: String,
    val deadlineMillis: Long?,
    val deadlineText: String?,
    val priority: TaskPriority,
    val requiredAction: String,
    val isDateAmbiguous: Boolean = false,
    val ambiguityReason: String? = null
)

data class AiEmailAnalysis(
    val emailId: String,
    val summary: String,
    val detectedCategory: EmailCategory,
    val priority: TaskPriority,
    val extractedTasks: List<ExtractedTask>,
    val keyDates: List<String> = emptyList(),
    val hasActionRequired: Boolean = false,
    val rawJson: String = ""
)
