package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.model.AssistantLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    WAITING_CONFIRMATION,
    ERROR
}

data class VoiceMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class VoiceAssistantManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<VoiceMessage>>(emptyList())
    val conversationHistory: StateFlow<List<VoiceMessage>> = _conversationHistory.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // 20-Second Inactivity Buffer State
    private val _inactivitySecondsRemaining = MutableStateFlow(20)
    val inactivitySecondsRemaining: StateFlow<Int> = _inactivitySecondsRemaining.asStateFlow()

    private val _currentLanguage = MutableStateFlow(AssistantLanguage.ENGLISH)
    val currentLanguage: StateFlow<AssistantLanguage> = _currentLanguage.asStateFlow()

    private val _isContinuousSession = MutableStateFlow(false)
    val isContinuousSession: StateFlow<Boolean> = _isContinuousSession.asStateFlow()

    var onSpeechRecognized: ((String) -> Unit)? = null

    private var inactivityJob: Job? = null
    private var hasSpokenGreeting = false

    init {
        textToSpeech = TextToSpeech(context.applicationContext, this)
        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = VoiceState.LISTENING
                        resetInactivityTimer()
                    }

                    override fun onBeginningOfSpeech() {
                        _voiceState.value = VoiceState.LISTENING
                        resetInactivityTimer()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val level = (rmsdB / 10f).coerceIn(0f, 1f)
                        _audioLevel.value = level
                        if (level > 0.15f) {
                            resetInactivityTimer()
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _voiceState.value = VoiceState.PROCESSING
                    }

                    override fun onError(error: Int) {
                        _audioLevel.value = 0f
                        // If error is no speech match or timeout, keep listening if session is active
                        if (_isContinuousSession.value && _inactivitySecondsRemaining.value > 1) {
                            scope.launch {
                                delay(300)
                                if (_isContinuousSession.value) {
                                    startListeningInternal()
                                }
                            }
                        } else {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        _voiceState.value = VoiceState.PROCESSING
                        _currentTranscript.value = text
                        resetInactivityTimer()

                        if (text.isNotBlank()) {
                            addMessage(text, isUser = true)
                            onSpeechRecognized?.invoke(text)
                        } else {
                            if (_isContinuousSession.value && _inactivitySecondsRemaining.value > 0) {
                                startListeningInternal()
                            } else {
                                _voiceState.value = VoiceState.IDLE
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let {
                            _currentTranscript.value = it
                            resetInactivityTimer()
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            updateTtsLanguage(_currentLanguage.value)
            textToSpeech?.setPitch(1.0f)
            textToSpeech?.setSpeechRate(1.05f)
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _voiceState.value = VoiceState.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    // When assistant finishes speaking in an active session, automatically resume listening
                    scope.launch(Dispatchers.Main) {
                        if (_isContinuousSession.value) {
                            delay(400)
                            _inactivitySecondsRemaining.value = 20
                            startListeningInternal()
                            startInactivityCountdown()
                        } else {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _voiceState.value = VoiceState.IDLE
                }
            })
        }
    }

    fun setLanguage(language: AssistantLanguage) {
        _currentLanguage.value = language
        updateTtsLanguage(language)
    }

    private fun updateTtsLanguage(language: AssistantLanguage) {
        textToSpeech?.let { tts ->
            val locale = if (language == AssistantLanguage.HINDI) Locale("hi", "IN") else Locale.US
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }
        }
    }

    /**
     * Greets the user on app startup and starts a 20-second inactivity listening session.
     */
    fun greetOnStartup() {
        if (hasSpokenGreeting) return
        hasSpokenGreeting = true

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val isHindi = _currentLanguage.value == AssistantLanguage.HINDI

        val greetingText = if (isHindi) {
            val timeWord = if (hour < 12) "सुप्रभात" else if (hour < 17) "नमस्ते" else "शुभ संध्या"
            "$timeWord! मैं मेलमाइंड एआई हूँ, आपका पर्सनल एग्जीक्यूटिव असिस्टेंट। आज मैं आपके ईमेल या शेड्यूल में क्या मदद करूँ?"
        } else {
            val timeWord = if (hour < 12) "Good morning" else if (hour < 17) "Good afternoon" else "Good evening"
            "$timeWord! I'm MailMind AI, your executive assistant. How can I assist you with your emails or schedule today?"
        }

        _isContinuousSession.value = true
        _inactivitySecondsRemaining.value = 20
        speak(greetingText, addToHistory = true)
    }

    fun startListeningSession() {
        _isContinuousSession.value = true
        _inactivitySecondsRemaining.value = 20
        stopSpeaking()
        startListeningInternal()
        startInactivityCountdown()
    }

    private fun startListeningInternal() {
        _currentTranscript.value = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val langTag = if (_currentLanguage.value == AssistantLanguage.HINDI) "hi-IN" else "en-US"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
        try {
            speechRecognizer?.startListening(intent)
            _voiceState.value = VoiceState.LISTENING
        } catch (_: Exception) {
            _voiceState.value = VoiceState.ERROR
        }
    }

    private fun startInactivityCountdown() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch(Dispatchers.Main) {
            while (isActive && _inactivitySecondsRemaining.value > 0) {
                delay(1000L)
                if (_voiceState.value == VoiceState.LISTENING || _voiceState.value == VoiceState.PROCESSING) {
                    _inactivitySecondsRemaining.value = (_inactivitySecondsRemaining.value - 1).coerceAtLeast(0)
                }
            }
            // Inactivity buffer expired!
            if (_inactivitySecondsRemaining.value == 0) {
                stopListeningSession(reason = "Inactivity timeout (20s buffer)")
            }
        }
    }

    fun resetInactivityTimer() {
        _inactivitySecondsRemaining.value = 20
    }

    fun stopListeningSession(reason: String = "User requested stop") {
        inactivityJob?.cancel()
        _isContinuousSession.value = false
        _inactivitySecondsRemaining.value = 0
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _voiceState.value = VoiceState.IDLE
        _audioLevel.value = 0f
    }

    fun speak(text: String, addToHistory: Boolean = true) {
        if (addToHistory) {
            addMessage(text, isUser = false)
        }
        if (!isTtsInitialized || textToSpeech == null) {
            _voiceState.value = VoiceState.IDLE
            return
        }
        _voiceState.value = VoiceState.SPEAKING
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MailMindSpeechUtterance")
    }

    fun stopSpeaking() {
        if (textToSpeech?.isSpeaking == true) {
            textToSpeech?.stop()
        }
        _voiceState.value = VoiceState.IDLE
    }

    fun setSpeechSettings(pitch: Float, rate: Float) {
        textToSpeech?.setPitch(pitch)
        textToSpeech?.setSpeechRate(rate)
    }

    fun addMessage(text: String, isUser: Boolean) {
        val message = VoiceMessage(text = text, isUser = isUser)
        _conversationHistory.value = _conversationHistory.value + message
    }

    fun clearHistory() {
        _conversationHistory.value = emptyList()
    }

    fun destroy() {
        try {
            inactivityJob?.cancel()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
    }
}
