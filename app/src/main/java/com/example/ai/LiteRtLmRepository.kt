package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.TransparencySettings
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val TAG = "LiteRtLmRepository"

data class LocalEngineStatus(
    val loadedModelPath: String? = null,
    val activeBackendName: String? = null,
    val isInitializing: Boolean = false,
    val errorMessage: String? = null,
) {
    val isReady: Boolean get() = loadedModelPath != null && !isInitializing
}

data class LocalStreamDelta(
    val thoughtDelta: String,
    val answerDelta: String,
)

class LiteRtLmRepository(private val context: Context) {

    // Single-thread executor owns Engine and Conversation lifecycle per LiteRT-LM mandate.
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeSettings: TransparencySettings? = null
    private var activeModelPath: String? = null
    private var isGenerating = false

    private val _engineStatus = MutableStateFlow(LocalEngineStatus())
    val engineStatus: StateFlow<LocalEngineStatus> = _engineStatus.asStateFlow()

    suspend fun ensureModelLoaded(
        modelPath: String,
        settings: TransparencySettings,
    ): Result<String> = withContext(dispatcher) {
        mutex.withLock {
            val currentEngine = engine
            if (
                currentEngine != null &&
                activeModelPath == modelPath &&
                activeSettings == settings &&
                conversation != null
            ) {
                return@withContext Result.success(_engineStatus.value.activeBackendName ?: "CPU")
            }

            // If same model is loaded and only system instructions changed, recreate conversation cleanly.
            if (currentEngine != null && activeModelPath == modelPath) {
                try {
                    while (isGenerating) {
                        conversation?.cancelProcess()
                        delay(100)
                    }
                    conversation?.close()
                    conversation = null
                    conversation = currentEngine.createConversation(buildConversationConfig(settings))
                    activeSettings = settings
                    return@withContext Result.success(_engineStatus.value.activeBackendName ?: "CPU")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed recreating conversation; reloading engine", e)
                }
            }

            releaseLocked()
            _engineStatus.value = LocalEngineStatus(isInitializing = true)

            val preferredBackend = if (settings.preferGpuBackend) Backend.GPU() else Backend.CPU()
            val (loadedEngine, backendLabel) = try {
                val cfg = EngineConfig(
                    modelPath = modelPath,
                    backend = preferredBackend,
                    cacheDir = context.cacheDir.path,
                )
                Engine(cfg).also { it.initialize() } to (if (settings.preferGpuBackend) "GPU" else "CPU")
            } catch (e: Exception) {
                if (settings.preferGpuBackend) {
                    Log.w(TAG, "GPU backend failed; falling back to CPU backend", e)
                    val cpuCfg = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.path,
                    )
                    try {
                        Engine(cpuCfg).also { it.initialize() } to "CPU (fallback)"
                    } catch (cpuErr: Exception) {
                        Log.e(TAG, "CPU fallback initialization failed", cpuErr)
                        _engineStatus.value = LocalEngineStatus(
                            errorMessage = cpuErr.localizedMessage ?: "Failed to initialize local model."
                        )
                        return@withContext Result.failure(cpuErr)
                    }
                } else {
                    Log.e(TAG, "Engine initialization failed", e)
                    _engineStatus.value = LocalEngineStatus(
                        errorMessage = e.localizedMessage ?: "Failed to initialize local model."
                    )
                    return@withContext Result.failure(e)
                }
            }

            return@withContext try {
                engine = loadedEngine
                conversation = loadedEngine.createConversation(buildConversationConfig(settings))
                activeModelPath = modelPath
                activeSettings = settings
                _engineStatus.value = LocalEngineStatus(
                    loadedModelPath = modelPath,
                    activeBackendName = backendLabel,
                    isInitializing = false,
                )
                Result.success(backendLabel)
            } catch (e: Exception) {
                Log.e(TAG, "Conversation creation failed", e)
                releaseLocked()
                _engineStatus.value = LocalEngineStatus(
                    errorMessage = e.localizedMessage ?: "Failed to create conversation."
                )
                Result.failure(e)
            }
        }
    }

    fun sendMessageStream(
        modelPath: String,
        prompt: String,
        settings: TransparencySettings,
    ): Flow<LocalStreamDelta> = channelFlow {
        withContext(dispatcher) {
            val initResult = ensureModelLoaded(modelPath, settings)
            if (initResult.isFailure) {
                throw initResult.exceptionOrNull() ?: IllegalStateException("Local model failed to load.")
            }

            val conv = conversation ?: throw IllegalStateException("Local conversation not initialized.")
            val tagParser = ThinkTagStreamParser()
            isGenerating = true
            try {
                conv.sendMessageAsync(prompt).collect { msg ->
                    val thoughtChannel = msg.channels["thought"]
                        ?: msg.channels["thinking"]
                        ?: ""
                    val rawText = msg.toString()

                    if (thoughtChannel.isNotEmpty()) {
                        send(
                            LocalStreamDelta(
                                thoughtDelta = thoughtChannel,
                                answerDelta = rawText,
                            )
                        )
                    } else {
                        val parsed = tagParser.feed(rawText)
                        if (parsed.thoughtDelta.isNotEmpty() || parsed.answerDelta.isNotEmpty()) {
                            send(
                                LocalStreamDelta(
                                    thoughtDelta = parsed.thoughtDelta,
                                    answerDelta = parsed.answerDelta,
                                )
                            )
                        }
                    }
                }
                val tail = tagParser.flush()
                if (tail.thoughtDelta.isNotEmpty() || tail.answerDelta.isNotEmpty()) {
                    send(LocalStreamDelta(tail.thoughtDelta, tail.answerDelta))
                }
            } catch (e: CancellationException) {
                conv.cancelProcess()
                throw e
            } finally {
                isGenerating = false
            }
        }
    }

    fun resetConversation(settings: TransparencySettings) {
        scope.launch {
            mutex.withLock {
                val loadedEngine = engine ?: return@withLock
                while (isGenerating) {
                    conversation?.cancelProcess()
                    delay(100)
                }
                conversation?.close()
                conversation = null
                conversation = loadedEngine.createConversation(buildConversationConfig(settings))
                activeSettings = settings
            }
        }
    }

    fun cancelActiveGeneration() {
        scope.launch {
            conversation?.cancelProcess()
        }
    }

    private suspend fun releaseLocked() {
        while (isGenerating) {
            conversation?.cancelProcess()
            delay(100)
        }
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        activeModelPath = null
        activeSettings = null
        _engineStatus.value = LocalEngineStatus()
    }

    fun shutdown() {
        scope.launch {
            mutex.withLock {
                releaseLocked()
            }
            executor.shutdown()
        }.invokeOnCompletion {
            scope.cancel()
        }
    }

    private fun buildConversationConfig(settings: TransparencySettings): ConversationConfig {
        val systemPrompt = CandorSystemPromptBuilder.build(settings)
        return ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            samplerConfig = SamplerConfig(
                topK = 20,
                topP = 0.95,
                temperature = 0.6,
            ),
            thinkingConfig = ThinkingConfig(enableThinking = true),
        )
    }
}
