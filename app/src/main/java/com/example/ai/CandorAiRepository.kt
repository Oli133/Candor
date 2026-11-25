package com.example.ai

import com.example.data.CandorModel
import com.example.data.TransparencySettings
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.flow.Flow

class CandorAiRepository {

    private var cachedSettings: TransparencySettings? = null
    private var cachedModelId: String? = null
    private var cachedModel: GenerativeModel? = null
    private var activeChat: Chat? = null

    private fun getOrCreateModel(
        settings: TransparencySettings,
        modelId: String,
    ): GenerativeModel {
        val currentModel = cachedModel
        if (currentModel != null && cachedSettings == settings && cachedModelId == modelId) {
            return currentModel
        }

        // Preserve conversation history if transparency settings or fallback model change mid-session.
        val existingHistory: List<Content> = activeChat?.history?.toList().orEmpty()

        val config = generationConfig {
            thinkingConfig = thinkingConfig {
                thinkingLevel = ThinkingLevel.HIGH
                includeThoughts = true
            }
        }

        val model = Firebase.ai.generativeModel(
            modelName = modelId,
            generationConfig = config,
            systemInstruction = content { text(CandorSystemPromptBuilder.build(settings)) },
        )

        cachedSettings = settings
        cachedModelId = modelId
        cachedModel = model
        activeChat = model.startChat(history = existingHistory)
        return model
    }

    private fun getOrCreateChat(
        settings: TransparencySettings,
        modelOverride: CandorModel? = null,
    ): Chat {
        val targetModelId = (modelOverride ?: settings.selectedModel).modelId
        getOrCreateModel(settings, targetModelId)
        return activeChat ?: throw IllegalStateException("Chat session failed to initialize.")
    }

    fun sendMessageStream(
        prompt: String,
        settings: TransparencySettings,
        modelOverride: CandorModel? = null,
    ): Flow<GenerateContentResponse> {
        val chat = getOrCreateChat(settings, modelOverride)
        return chat.sendMessageStream(prompt)
    }

    fun resetSession() {
        val model = cachedModel
        activeChat = model?.startChat()
    }
}
