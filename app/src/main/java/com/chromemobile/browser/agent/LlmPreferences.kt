package com.chromemobile.browser.agent

import android.content.Context
import android.content.SharedPreferences

class LlmPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): LlmConfig {
        val providerStr = prefs.getString(KEY_PROVIDER, LlmProvider.OPENAI.name) ?: LlmProvider.OPENAI.name
        val provider = try {
            LlmProvider.valueOf(providerStr)
        } catch (e: Exception) {
            LlmProvider.OPENAI
        }

        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val defaultModel = when (provider) {
            LlmProvider.OPENAI -> "gpt-4o"
            LlmProvider.ANTHROPIC -> "claude-3-7-sonnet-20250219"
            LlmProvider.GEMINI -> "gemini-2.0-flash"
            LlmProvider.OLLAMA -> "llama3.2"
            LlmProvider.MOCK -> "mock-model"
        }
        val model = prefs.getString(KEY_MODEL, defaultModel) ?: defaultModel
        val baseUrl = prefs.getString(KEY_BASE_URL, if (provider == LlmProvider.OLLAMA) "http://10.0.2.2:11434/v1" else "") ?: ""

        return LlmConfig(
            provider = provider,
            apiKey = apiKey,
            model = model,
            baseUrl = baseUrl
        )
    }

    fun saveConfig(config: LlmConfig) {
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider.name)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_MODEL, config.model)
            .putString(KEY_BASE_URL, config.baseUrl)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "chrome_mobile_ai_prefs"
        private const val KEY_PROVIDER = "llm_provider"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_MODEL = "llm_model"
        private const val KEY_BASE_URL = "llm_base_url"
    }
}
