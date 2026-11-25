package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.transparencyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "candor_transparency_prefs"
)

enum class CandorModel(
    val modelId: String,
    val displayName: String,
    val badgeText: String,
    val subtitle: String,
    val isLocalOpenSource: Boolean,
    val repoId: String? = null,
    val fileName: String? = null,
    val approxSizeLabel: String? = null,
    val minExpectedBytes: Long = 0L,
) {
    QWEN3_0_6B(
        modelId = "qwen3-0.6b-local",
        displayName = "Qwen3 0.6B (Open-Source)",
        badgeText = "Qwen3-0.6B · Open-Source · On-Device",
        subtitle = "Apache-2.0 open-source model (~600 MB). Runs locally with <think> reasoning & zero limits.",
        isLocalOpenSource = true,
        repoId = "litert-community/Qwen3-0.6B",
        fileName = "Qwen3-0.6B.litertlm",
        approxSizeLabel = "~600 MB",
        minExpectedBytes = 100_000_000L,
    ),
    GEMMA_4_E2B(
        modelId = "gemma-4-e2b-local",
        displayName = "Gemma 4 E2B (Open-Weights)",
        badgeText = "Gemma 4 E2B · Open-Weights · On-Device",
        subtitle = "Open-weights 2B-effective model (~2.6 GB). Deeper local reasoning, zero charges.",
        isLocalOpenSource = true,
        repoId = "litert-community/gemma-4-E2B-it-litert-lm",
        fileName = "gemma-4-E2B-it.litertlm",
        approxSizeLabel = "~2.6 GB",
        minExpectedBytes = 500_000_000L,
    ),
    CUSTOM_LOCAL(
        modelId = "custom-litertlm-local",
        displayName = "Custom .litertlm (Local File)",
        badgeText = "Custom Local · Open-Source · On-Device",
        subtitle = "Use any open-source .litertlm model imported from your device storage.",
        isLocalOpenSource = true,
        repoId = null,
        fileName = "custom_imported.litertlm",
        approxSizeLabel = "Local file",
        minExpectedBytes = 10_000_000L,
    ),
    FLASH_3_8(
        modelId = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash (Free Cloud)",
        badgeText = "gemini-3.8-flash · High Thinking · Free Cloud",
        subtitle = "Cloud free tier (5 req/min). No local download required.",
        isLocalOpenSource = false,
    ),
    FLASH_LITE_3_5(
        modelId = "gemini-3.5-flash-lite",
        displayName = "Gemini 3.5 Flash-Lite (Free Cloud)",
        badgeText = "gemini-3.5-flash-lite · High Thinking · Free Cloud",
        subtitle = "Cloud free tier with higher rate limit. No local download required.",
        isLocalOpenSource = false,
    );

    fun downloadUrl(): String? {
        val repo = repoId ?: return null
        val file = fileName ?: return null
        return "https://huggingface.co/$repo/resolve/main/$file"
    }

    companion object {
        fun fromId(id: String?): CandorModel =
            entries.firstOrNull { it.modelId == id } ?: QWEN3_0_6B
    }
}

data class TransparencySettings(
    val selectedModel: CandorModel = CandorModel.QWEN3_0_6B,
    val preferGpuBackend: Boolean = false,
    val autoExpandThinking: Boolean = true,
    val neutralFraming: Boolean = true,
    val explicitAssumptions: Boolean = true,
    val includeCounterarguments: Boolean = true,
    val calibratedUncertainty: Boolean = true,
) {
    fun activeRuleCount(): Int = listOf(
        neutralFraming,
        explicitAssumptions,
        includeCounterarguments,
        calibratedUncertainty,
    ).count { it }
}

class TransparencyPreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val PREFER_GPU_BACKEND = booleanPreferencesKey("prefer_gpu_backend")
        val AUTO_EXPAND_THINKING = booleanPreferencesKey("auto_expand_thinking")
        val NEUTRAL_FRAMING = booleanPreferencesKey("neutral_framing")
        val EXPLICIT_ASSUMPTIONS = booleanPreferencesKey("explicit_assumptions")
        val INCLUDE_COUNTERARGUMENTS = booleanPreferencesKey("include_counterarguments")
        val CALIBRATED_UNCERTAINTY = booleanPreferencesKey("calibrated_uncertainty")
    }

    val settingsFlow: Flow<TransparencySettings> = context.transparencyDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            TransparencySettings(
                selectedModel = CandorModel.fromId(prefs[Keys.SELECTED_MODEL]),
                preferGpuBackend = prefs[Keys.PREFER_GPU_BACKEND] ?: false,
                autoExpandThinking = prefs[Keys.AUTO_EXPAND_THINKING] ?: true,
                neutralFraming = prefs[Keys.NEUTRAL_FRAMING] ?: true,
                explicitAssumptions = prefs[Keys.EXPLICIT_ASSUMPTIONS] ?: true,
                includeCounterarguments = prefs[Keys.INCLUDE_COUNTERARGUMENTS] ?: true,
                calibratedUncertainty = prefs[Keys.CALIBRATED_UNCERTAINTY] ?: true,
            )
        }

    suspend fun updateSelectedModel(model: CandorModel) {
        context.transparencyDataStore.edit { it[Keys.SELECTED_MODEL] = model.modelId }
    }

    suspend fun updatePreferGpuBackend(enabled: Boolean) {
        context.transparencyDataStore.edit { it[Keys.PREFER_GPU_BACKEND] = enabled }
    }

    suspend fun updateAutoExpandThinking(enabled: Boolean) {
        context.transparencyDataStore.edit { it[Keys.AUTO_EXPAND_THINKING] = enabled }
    }

    suspend fun updateNeutralFraming(enabled: Boolean) {
        context.transparencyDataStore.edit { it[Keys.NEUTRAL_FRAMING] = enabled }
    }

    suspend fun updateExplicitAssumptions(enabled: Boolean) {
        context.transparencyDataStore.edit { it[Keys.EXPLICIT_ASSUMPTIONS] = enabled }
    }

    suspend fun updateIncludeCounterarguments(enabled: Boolean) {
        context.transparencyDataStore.edit { it[Keys.INCLUDE_COUNTERARGUMENTS] = enabled }
    }

    suspend fun updateCalibratedUncertainty(enabled: Boolean) {
        context.transparencyDataStore.edit { it[Keys.CALIBRATED_UNCERTAINTY] = enabled }
    }
}
