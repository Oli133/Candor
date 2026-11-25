package com.example

import com.example.ai.ThinkTagStreamParser
import com.example.data.CandorModel
import com.example.data.TransparencySettings
import com.example.ui.AuditAction
import com.example.ui.RateLimitParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandorLogicTest {

    @Test
    fun `transparency settings counts active epistemic rules accurately`() {
        val allEnabled = TransparencySettings()
        assertEquals(4, allEnabled.activeRuleCount())
        assertEquals(CandorModel.QWEN3_0_6B, allEnabled.selectedModel)

        val partial = TransparencySettings(
            selectedModel = CandorModel.GEMMA_4_E2B,
            autoExpandThinking = false,
            neutralFraming = true,
            explicitAssumptions = false,
            includeCounterarguments = true,
            calibratedUncertainty = false,
        )
        assertEquals(2, partial.activeRuleCount())
    }

    @Test
    fun `open source models expose valid huggingface download urls`() {
        assertEquals(
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            CandorModel.QWEN3_0_6B.downloadUrl(),
        )
        assertEquals(
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            CandorModel.GEMMA_4_E2B.downloadUrl(),
        )
        assertTrue(CandorModel.QWEN3_0_6B.isLocalOpenSource)
        assertFalse(CandorModel.FLASH_3_8.isLocalOpenSource)
    }

    @Test
    fun `think tag parser splits streamed chunks across tag boundaries`() {
        val parser = ThinkTagStreamParser()
        val thoughts = StringBuilder()
        val answers = StringBuilder()

        val chunks = listOf("<thi", "nk>Step 1: weigh", " options.</th", "ink>\nFinal ", "answer.")
        for (chunk in chunks) {
            val delta = parser.feed(chunk)
            thoughts.append(delta.thoughtDelta)
            answers.append(delta.answerDelta)
        }
        val tail = parser.flush()
        thoughts.append(tail.thoughtDelta)
        answers.append(tail.answerDelta)

        assertEquals("Step 1: weigh options.", thoughts.toString())
        assertEquals("\nFinal answer.", answers.toString())
    }

    @Test
    fun `audit actions provide non-empty critical verification instructions`() {
        AuditAction.entries.forEach { action ->
            assertTrue(action.label.isNotBlank())
            assertTrue(action.promptInstruction.isNotBlank())
        }
    }

    @Test
    fun `rate limit parser detects free tier quota error and extracts retry seconds`() {
        val raw = "You exceeded your current quota, please check your plan and billing details. " +
            "* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, " +
            "limit: 5, model: gemini-3.8-flash. Please retry in 30.340463436s."

        assertTrue(RateLimitParser.isRateLimit(raw))
        assertEquals(31, RateLimitParser.extractRetrySeconds(raw))
        assertFalse(RateLimitParser.isRateLimit("Network connection lost"))
    }
}
