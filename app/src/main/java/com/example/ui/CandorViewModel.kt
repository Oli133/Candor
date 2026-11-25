package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.CandorAiRepository
import com.example.ai.LiteRtLmRepository
import com.example.ai.LocalEngineStatus
import com.example.ai.LocalModelManager
import com.example.ai.ModelDownloadState
import com.example.data.CandorModel
import com.example.data.TransparencyPreferencesRepository
import com.example.data.TransparencySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.math.ceil

private const val TAG = "CandorViewModel"
private const val CLOUD_TIMEOUT_MS = 60_000L
private const val LOCAL_TIMEOUT_MS = 120_000L

enum class MessageRole {
    USER,
    ASSISTANT,
}

enum class AuditAction(
    val label: String,
    val promptInstruction: String,
) {
    CHALLENGE(
        label = "Challenge",
        promptInstruction = "Steelman the strongest objection, counterexample, or failure mode against your previous response without defending your earlier framing.",
    ),
    ASSUMPTIONS(
        label = "Assumptions",
        promptInstruction = "List every implicit assumption, missing variable, and unstated value judgment that your previous response relied on.",
    ),
    CERTAINTY(
        label = "Certainty Check",
        promptInstruction = "Audit your previous response by separating: (1) established facts, (2) reasonable inferences, and (3) uncertain or speculative claims, plus what evidence would change your conclusion.",
    ),
}

data class StarterPrompt(
    val category: String,
    val title: String,
    val prompt: String,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val answerText: String = "",
    val thoughtSummary: String = "",
    val modelUsedId: String = CandorModel.QWEN3_0_6B.modelId,
    val fallbackNote: String? = null,
    val thoughtsTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
    val thinkingDurationMs: Long? = null,
    val isThinking: Boolean = false,
    val isStreaming: Boolean = false,
    val errorMessage: String? = null,
    val isRateLimitError: Boolean = false,
    val retryCountdownSeconds: Int = 0,
    val retryPrompt: String? = null,
)

data class CandorUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val settings: TransparencySettings = TransparencySettings(),
    val isSettingsSheetOpen: Boolean = false,
    val isSelectedLocalModelDownloaded: Boolean = false,
    val downloadState: ModelDownloadState = ModelDownloadState.Idle,
    val localEngineStatus: LocalEngineStatus = LocalEngineStatus(),
) {
    val canSendPrompt: Boolean
        get() = !isGenerating &&
            (!settings.selectedModel.isLocalOpenSource ||
                (isSelectedLocalModelDownloaded && localEngineStatus.isReady))
}

object RateLimitParser {
    private val retryRegex = Regex("""retry\s+in\s+(\d+(?:\.\d+)?)s""", RegexOption.IGNORE_CASE)

    fun isRateLimit(rawMessage: String?): Boolean {
        if (rawMessage.isNullOrBlank()) return false
        val lower = rawMessage.lowercase()
        return "quota" in lower ||
            "rate limit" in lower ||
            "rate-limit" in lower ||
            "free_tier" in lower ||
            "resource_exhausted" in lower ||
            "429" in lower
    }

    fun extractRetrySeconds(rawMessage: String?): Int {
        if (rawMessage.isNullOrBlank()) return 30
        val match = retryRegex.find(rawMessage) ?: return 30
        val value = match.groupValues[1].toDoubleOrNull() ?: return 30
        return ceil(value).toInt().coerceIn(5, 120)
    }

    fun formatFriendlyMessage(modelId: String, retrySeconds: Int): String {
        return "Free-tier speed limit reached on $modelId (no charges or billing apply). " +
            "You can retry in ${retrySeconds}s, or switch to an on-device open-source model for unlimited offline use."
    }
}

class CandorViewModel(application: Application) : AndroidViewModel(application) {

    private val cloudRepository = CandorAiRepository()
    private val localModelManager = LocalModelManager(application.applicationContext)
    private val liteRtLmRepository = LiteRtLmRepository(application.applicationContext)
    private val preferencesRepository = TransparencyPreferencesRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(CandorUiState())
    val uiState: StateFlow<CandorUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var activeDownloadJob: Job? = null
    private var countdownJob: Job? = null

    val starterPrompts: List<StarterPrompt> = listOf(
        StarterPrompt(
            category = "DECISION",
            title = "Rent vs. buy trade-offs",
            prompt = "Should I rent or buy a home right now? Walk through the key financial and lifestyle trade-offs without nudging me toward either choice.",
        ),
        StarterPrompt(
            category = "EVIDENCE",
            title = "Longevity claims on coffee",
            prompt = "Is daily coffee actually beneficial or harmful for long-term health? Separate strong epidemiological evidence from weak correlation and hype.",
        ),
        StarterPrompt(
            category = "PERSPECTIVES",
            title = "Nuclear energy debate",
            prompt = "Present the strongest steelmanned arguments both for and against expanding nuclear fission power over the next 20 years.",
        ),
        StarterPrompt(
            category = "REASONING",
            title = "Career offer evaluation",
            prompt = "Help me build an unbiased framework to compare a higher-paying stressful job offer against a lower-paying flexible role.",
        ),
    )

    init {
        viewModelScope.launch {
            localModelManager.downloadState.collect { dlState ->
                _uiState.update { state ->
                    state.copy(
                        downloadState = dlState,
                        isSelectedLocalModelDownloaded = localModelManager.isModelAvailable(state.settings.selectedModel),
                    )
                }
            }
        }

        viewModelScope.launch {
            liteRtLmRepository.engineStatus.collect { engineStatus ->
                _uiState.update { it.copy(localEngineStatus = engineStatus) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.settingsFlow.collect { loadedSettings ->
                val isAvailable = localModelManager.isModelAvailable(loadedSettings.selectedModel)
                _uiState.update {
                    it.copy(
                        settings = loadedSettings,
                        isSelectedLocalModelDownloaded = isAvailable,
                    )
                }
                if (loadedSettings.selectedModel.isLocalOpenSource && isAvailable) {
                    val modelFile = localModelManager.modelFile(loadedSettings.selectedModel)
                    if (modelFile != null) {
                        liteRtLmRepository.ensureModelLoaded(modelFile.absolutePath, loadedSettings)
                    }
                }
            }
        }
    }

    fun onInputChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun openSettingsSheet() {
        _uiState.update { it.copy(isSettingsSheetOpen = true) }
    }

    fun closeSettingsSheet() {
        _uiState.update { it.copy(isSettingsSheetOpen = false) }
    }

    fun setSelectedModel(model: CandorModel) {
        viewModelScope.launch { preferencesRepository.updateSelectedModel(model) }
    }

    fun setPreferGpuBackend(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updatePreferGpuBackend(enabled) }
    }

    fun setAutoExpandThinking(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateAutoExpandThinking(enabled) }
    }

    fun setNeutralFraming(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateNeutralFraming(enabled) }
    }

    fun setExplicitAssumptions(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateExplicitAssumptions(enabled) }
    }

    fun setIncludeCounterarguments(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateIncludeCounterarguments(enabled) }
    }

    fun setCalibratedUncertainty(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateCalibratedUncertainty(enabled) }
    }

    fun downloadSelectedModel() {
        val model = _uiState.value.settings.selectedModel
        if (!model.isLocalOpenSource || model.downloadUrl() == null) return
        if (activeDownloadJob?.isActive == true) return

        localModelManager.clearDownloadError()
        activeDownloadJob = viewModelScope.launch {
            val result = localModelManager.downloadModel(model)
            result.onSuccess { file ->
                _uiState.update { it.copy(isSelectedLocalModelDownloaded = true) }
                liteRtLmRepository.ensureModelLoaded(file.absolutePath, _uiState.value.settings)
            }
        }
    }

    fun cancelModelDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        localModelManager.cancelDownload()
    }

    fun importCustomModel(uri: Uri) {
        if (activeDownloadJob?.isActive == true) return
        localModelManager.clearDownloadError()
        activeDownloadJob = viewModelScope.launch {
            val result = localModelManager.importCustomModel(uri)
            result.onSuccess { file ->
                preferencesRepository.updateSelectedModel(CandorModel.CUSTOM_LOCAL)
                _uiState.update { it.copy(isSelectedLocalModelDownloaded = true) }
                liteRtLmRepository.ensureModelLoaded(file.absolutePath, _uiState.value.settings)
            }
        }
    }

    fun deleteLocalModel(model: CandorModel) {
        viewModelScope.launch {
            if (_uiState.value.settings.selectedModel == model) {
                liteRtLmRepository.resetConversation(_uiState.value.settings)
            }
            localModelManager.deleteModel(model)
            _uiState.update { state ->
                state.copy(
                    isSelectedLocalModelDownloaded = localModelManager.isModelAvailable(state.settings.selectedModel)
                )
            }
        }
    }

    fun isLocalModelDownloaded(model: CandorModel): Boolean =
        localModelManager.isModelAvailable(model)

    fun reloadLocalEngine() {
        val settings = _uiState.value.settings
        if (!settings.selectedModel.isLocalOpenSource) return
        val file = localModelManager.modelFile(settings.selectedModel) ?: return
        if (!file.exists()) return
        viewModelScope.launch {
            liteRtLmRepository.ensureModelLoaded(file.absolutePath, settings)
        }
    }

    fun sendCurrentInput() {
        val prompt = _uiState.value.inputText.trim()
        if (prompt.isEmpty() || !_uiState.value.canSendPrompt) return
        _uiState.update { it.copy(inputText = "") }
        submitPrompt(prompt)
    }

    fun sendStarterPrompt(prompt: String) {
        if (!_uiState.value.canSendPrompt) return
        submitPrompt(prompt)
    }

    fun sendAuditAction(action: AuditAction) {
        if (!_uiState.value.canSendPrompt) return
        submitPrompt(action.promptInstruction)
    }

    fun retryMessage(messageId: String) {
        if (_uiState.value.isGenerating) return
        val failedMessage = _uiState.value.messages.find { it.id == messageId } ?: return
        val prompt = failedMessage.retryPrompt ?: return

        countdownJob?.cancel()
        _uiState.update { state ->
            state.copy(messages = state.messages.filterNot { it.id == messageId })
        }
        submitPrompt(prompt, addUserBubble = false)
    }

    fun switchToFlashLiteAndRetry(messageId: String) {
        if (_uiState.value.isGenerating) return
        val failedMessage = _uiState.value.messages.find { it.id == messageId } ?: return
        val prompt = failedMessage.retryPrompt ?: return

        countdownJob?.cancel()
        setSelectedModel(CandorModel.FLASH_LITE_3_5)
        _uiState.update { state ->
            state.copy(messages = state.messages.filterNot { it.id == messageId })
        }
        submitPrompt(prompt, addUserBubble = false, modelOverride = CandorModel.FLASH_LITE_3_5)
    }

    fun dismissError(messageId: String) {
        countdownJob?.cancel()
        _uiState.update { state ->
            state.copy(messages = state.messages.filterNot { it.id == messageId })
        }
    }

    fun stopGeneration() {
        if (_uiState.value.settings.selectedModel.isLocalOpenSource) {
            liteRtLmRepository.cancelActiveGeneration()
        }
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _uiState.update { state ->
            state.copy(
                isGenerating = false,
                messages = state.messages.map { msg ->
                    if (msg.isStreaming || msg.isThinking) {
                        msg.copy(
                            isThinking = false,
                            isStreaming = false,
                            answerText = msg.answerText.ifBlank { "Generation stopped by user." },
                        )
                    } else {
                        msg
                    }
                },
            )
        }
    }

    fun clearConversation() {
        if (_uiState.value.settings.selectedModel.isLocalOpenSource) {
            liteRtLmRepository.cancelActiveGeneration()
            liteRtLmRepository.resetConversation(_uiState.value.settings)
        }
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        countdownJob?.cancel()
        cloudRepository.resetSession()
        _uiState.update { state ->
            state.copy(
                messages = emptyList(),
                isGenerating = false,
            )
        }
    }

    private fun submitPrompt(
        prompt: String,
        addUserBubble: Boolean = true,
        modelOverride: CandorModel? = null,
    ) {
        countdownJob?.cancel()
        val currentSettings = _uiState.value.settings
        val initialModel = modelOverride ?: currentSettings.selectedModel
        val assistantMsgId = UUID.randomUUID().toString()

        val newMessages = buildList {
            if (addUserBubble) {
                add(ChatMessage(role = MessageRole.USER, answerText = prompt))
            }
            add(
                ChatMessage(
                    id = assistantMsgId,
                    role = MessageRole.ASSISTANT,
                    modelUsedId = initialModel.displayName,
                    isThinking = true,
                    isStreaming = true,
                    retryPrompt = prompt,
                )
            )
        }

        _uiState.update { state ->
            state.copy(
                messages = state.messages + newMessages,
                isGenerating = true,
            )
        }

        activeGenerationJob = viewModelScope.launch {
            try {
                if (initialModel.isLocalOpenSource) {
                    runLocalAttempt(
                        assistantMsgId = assistantMsgId,
                        prompt = prompt,
                        settings = currentSettings,
                        model = initialModel,
                    )
                } else {
                    val succeeded = runCloudStreamAttempt(
                        assistantMsgId = assistantMsgId,
                        prompt = prompt,
                        settings = currentSettings,
                        model = initialModel,
                        fallbackNote = null,
                    )

                    if (!succeeded && initialModel == CandorModel.FLASH_3_8) {
                        val currentMsg = _uiState.value.messages.find { it.id == assistantMsgId }
                        if (currentMsg?.isRateLimitError == true) {
                            Log.w(TAG, "3.8 Flash free quota hit; switching to 3.5 Flash-Lite.")
                            updateAssistantMessage(assistantMsgId) { msg ->
                                msg.copy(
                                    modelUsedId = CandorModel.FLASH_LITE_3_5.modelId,
                                    fallbackNote = "Switched to free 3.5 Flash-Lite (3.8 Flash 5/min limit reached)",
                                    isThinking = true,
                                    isStreaming = true,
                                    errorMessage = null,
                                    isRateLimitError = false,
                                    retryCountdownSeconds = 0,
                                    thoughtSummary = "",
                                    answerText = "",
                                )
                            }
                            runCloudStreamAttempt(
                                assistantMsgId = assistantMsgId,
                                prompt = prompt,
                                settings = currentSettings,
                                model = CandorModel.FLASH_LITE_3_5,
                                fallbackNote = "Switched to free 3.5 Flash-Lite (3.8 Flash 5/min limit reached)",
                            )
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private suspend fun runLocalAttempt(
        assistantMsgId: String,
        prompt: String,
        settings: TransparencySettings,
        model: CandorModel,
    ) {
        val modelFile = localModelManager.modelFile(model)
        if (modelFile == null || !localModelManager.isModelAvailable(model)) {
            updateAssistantMessage(assistantMsgId) { msg ->
                msg.copy(
                    isThinking = false,
                    isStreaming = false,
                    errorMessage = "${model.displayName} is not downloaded yet. Tap Download above to run locally.",
                )
            }
            return
        }

        val startTimeMs = System.currentTimeMillis()
        val thoughtBuilder = StringBuilder()
        val answerBuilder = StringBuilder()
        var thinkingEndMs: Long? = null

        try {
            withTimeout(LOCAL_TIMEOUT_MS) {
                liteRtLmRepository.sendMessageStream(
                    modelPath = modelFile.absolutePath,
                    prompt = prompt,
                    settings = settings,
                ).collect { delta ->
                    if (delta.thoughtDelta.isNotEmpty()) {
                        thoughtBuilder.append(delta.thoughtDelta)
                    }
                    if (delta.answerDelta.isNotEmpty()) {
                        if (thinkingEndMs == null) {
                            thinkingEndMs = System.currentTimeMillis()
                        }
                        answerBuilder.append(delta.answerDelta)
                    }

                    val hasAnswerStarted = answerBuilder.isNotEmpty()
                    val durationMs = (thinkingEndMs ?: System.currentTimeMillis()) - startTimeMs
                    val backendLabel = _uiState.value.localEngineStatus.activeBackendName ?: "CPU"

                    updateAssistantMessage(assistantMsgId) { msg ->
                        msg.copy(
                            modelUsedId = "${model.displayName} · $backendLabel",
                            thoughtSummary = thoughtBuilder.toString(),
                            answerText = answerBuilder.toString(),
                            thinkingDurationMs = if (hasAnswerStarted || thoughtBuilder.isNotEmpty()) durationMs else null,
                            isThinking = !hasAnswerStarted,
                            isStreaming = true,
                        )
                    }
                }
            }

            val finalAnswer = answerBuilder.toString().trim()
            val finalThought = thoughtBuilder.toString().trim()
            val durationMs = (thinkingEndMs ?: System.currentTimeMillis()) - startTimeMs

            if (finalAnswer.isBlank() && finalThought.isBlank()) {
                updateAssistantMessage(assistantMsgId) { msg ->
                    msg.copy(
                        isThinking = false,
                        isStreaming = false,
                        errorMessage = "Local model returned an empty response. Please try again.",
                    )
                }
            } else {
                updateAssistantMessage(assistantMsgId) { msg ->
                    msg.copy(
                        thoughtSummary = finalThought,
                        answerText = finalAnswer.ifBlank { "(See reasoning trace above.)" },
                        thinkingDurationMs = durationMs,
                        isThinking = false,
                        isStreaming = false,
                        errorMessage = null,
                    )
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Local open-source generation timed out", e)
            updateAssistantMessage(assistantMsgId) { msg ->
                msg.copy(
                    isThinking = false,
                    isStreaming = false,
                    errorMessage = "Local generation timed out after 120 seconds. Tap Retry to try again.",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Local LiteRT-LM generation failed", e)
            updateAssistantMessage(assistantMsgId) { msg ->
                msg.copy(
                    isThinking = false,
                    isStreaming = false,
                    errorMessage = e.localizedMessage ?: "On-device inference failed.",
                )
            }
        }
    }

    private suspend fun runCloudStreamAttempt(
        assistantMsgId: String,
        prompt: String,
        settings: TransparencySettings,
        model: CandorModel,
        fallbackNote: String?,
    ): Boolean {
        val startTimeMs = System.currentTimeMillis()
        val thoughtBuilder = StringBuilder()
        val answerBuilder = StringBuilder()
        var thinkingEndMs: Long? = null
        var lastThoughtsTokens: Int? = null
        var lastTotalTokens: Int? = null

        try {
            withTimeout(CLOUD_TIMEOUT_MS) {
                cloudRepository.sendMessageStream(prompt, settings, model).collect { chunk ->
                    val thoughtDelta = chunk.thoughtSummary.orEmpty()
                    val answerDelta = chunk.text.orEmpty()

                    if (thoughtDelta.isNotEmpty()) {
                        thoughtBuilder.append(thoughtDelta)
                    }
                    if (answerDelta.isNotEmpty()) {
                        if (thinkingEndMs == null) {
                            thinkingEndMs = System.currentTimeMillis()
                        }
                        answerBuilder.append(answerDelta)
                    }

                    chunk.usageMetadata?.let { usage ->
                        if (usage.thoughtsTokenCount > 0) {
                            lastThoughtsTokens = usage.thoughtsTokenCount
                        }
                        if (usage.totalTokenCount > 0) {
                            lastTotalTokens = usage.totalTokenCount
                        }
                    }

                    val hasAnswerStarted = answerBuilder.isNotEmpty()
                    val durationMs = (thinkingEndMs ?: System.currentTimeMillis()) - startTimeMs

                    updateAssistantMessage(assistantMsgId) { msg ->
                        msg.copy(
                            modelUsedId = model.modelId,
                            fallbackNote = fallbackNote,
                            thoughtSummary = thoughtBuilder.toString(),
                            answerText = answerBuilder.toString(),
                            thoughtsTokenCount = lastThoughtsTokens,
                            totalTokenCount = lastTotalTokens,
                            thinkingDurationMs = if (hasAnswerStarted || thoughtBuilder.isNotEmpty()) durationMs else null,
                            isThinking = !hasAnswerStarted,
                            isStreaming = true,
                        )
                    }
                }
            }

            val finalAnswer = answerBuilder.toString()
            val finalThought = thoughtBuilder.toString()
            val durationMs = (thinkingEndMs ?: System.currentTimeMillis()) - startTimeMs

            if (finalAnswer.isBlank() && finalThought.isBlank()) {
                updateAssistantMessage(assistantMsgId) { msg ->
                    msg.copy(
                        isThinking = false,
                        isStreaming = false,
                        errorMessage = "Model returned an empty response. Please try again.",
                    )
                }
                return false
            }

            updateAssistantMessage(assistantMsgId) { msg ->
                msg.copy(
                    modelUsedId = model.modelId,
                    fallbackNote = fallbackNote,
                    thoughtSummary = finalThought,
                    answerText = finalAnswer.ifBlank { "(No final text emitted; see reasoning trace above.)" },
                    thoughtsTokenCount = lastThoughtsTokens,
                    totalTokenCount = lastTotalTokens,
                    thinkingDurationMs = durationMs,
                    isThinking = false,
                    isStreaming = false,
                    errorMessage = null,
                    isRateLimitError = false,
                )
            }
            return true
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Cloud generation timed out on ${model.modelId}", e)
            updateAssistantMessage(assistantMsgId) { msg ->
                msg.copy(
                    isThinking = false,
                    isStreaming = false,
                    errorMessage = "Reasoning timed out after 60 seconds. Tap Retry to run again.",
                )
            }
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed on ${model.modelId}", e)
            val raw = e.localizedMessage ?: e.message
            if (RateLimitParser.isRateLimit(raw)) {
                val retrySec = RateLimitParser.extractRetrySeconds(raw)
                updateAssistantMessage(assistantMsgId) { msg ->
                    msg.copy(
                        modelUsedId = model.modelId,
                        isThinking = false,
                        isStreaming = false,
                        isRateLimitError = true,
                        retryCountdownSeconds = retrySec,
                        errorMessage = RateLimitParser.formatFriendlyMessage(model.modelId, retrySec),
                    )
                }
                startRetryCountdown(assistantMsgId, model.modelId, retrySec)
            } else {
                updateAssistantMessage(assistantMsgId) { msg ->
                    msg.copy(
                        isThinking = false,
                        isStreaming = false,
                        isRateLimitError = false,
                        errorMessage = raw ?: "Failed to generate response.",
                    )
                }
            }
            return false
        }
    }

    private fun startRetryCountdown(
        messageId: String,
        modelId: String,
        initialSeconds: Int,
    ) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = initialSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                updateAssistantMessage(messageId) { msg ->
                    if (msg.isRateLimitError) {
                        msg.copy(
                            retryCountdownSeconds = remaining,
                            errorMessage = if (remaining > 0) {
                                RateLimitParser.formatFriendlyMessage(modelId, remaining)
                            } else {
                                "Free-tier cooldown complete (no charges apply). Tap Retry to send again."
                            },
                        )
                    } else {
                        msg
                    }
                }
            }
        }
    }

    private fun updateAssistantMessage(
        messageId: String,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId) transform(msg) else msg
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeDownloadJob?.cancel()
        liteRtLmRepository.shutdown()
    }
}
