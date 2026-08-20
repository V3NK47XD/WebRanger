package com.chromemobile.browser.agent

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class SearchEngine(val displayName: String, val searchUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q=")
}

class LlmPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun loadConfig(): LlmConfig {
        val providerStr = prefs.getString(KEY_PROVIDER, LlmProvider.GEMINI.name) ?: LlmProvider.GEMINI.name
        val provider = try {
            LlmProvider.valueOf(providerStr)
        } catch (e: Exception) {
            LlmProvider.GEMINI
        }

        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val defaultModel = getDefaultModelForProvider(provider)
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
        recordModelUsed(config.model)
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider.name)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_MODEL, config.model)
            .putString(KEY_BASE_URL, config.baseUrl)
            .apply()
    }

    fun getSearchEngine(): SearchEngine {
        val name = prefs.getString(KEY_SEARCH_ENGINE, SearchEngine.GOOGLE.name) ?: SearchEngine.GOOGLE.name
        return try {
            SearchEngine.valueOf(name)
        } catch (e: Exception) {
            SearchEngine.GOOGLE
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, engine.name).apply()
    }

    fun getDefaultModelForProvider(provider: LlmProvider): String {
        return when (provider) {
            LlmProvider.GEMINI -> "gemini-2.0-flash"
            LlmProvider.OPENAI -> "gpt-4o"
            LlmProvider.ANTHROPIC -> "claude-3-7-sonnet-20250219"
            LlmProvider.OLLAMA -> "llama3.2"
            LlmProvider.MOCK -> "mock-model"
        }
    }

    fun getModelsForProvider(provider: LlmProvider): List<String> {
        val builtIn = when (provider) {
            LlmProvider.GEMINI -> listOf("gemini-2.0-flash", "gemini-2.0-pro-exp", "gemini-1.5-pro", "gemini-1.5-flash")
            LlmProvider.OPENAI -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "o1-mini", "o3-mini")
            LlmProvider.ANTHROPIC -> listOf("claude-3-7-sonnet-20250219", "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022")
            LlmProvider.OLLAMA -> listOf("llama3.2", "mistral", "qwen2.5", "deepseek-r1:7b", "gemma-4-31b-it")
            LlmProvider.MOCK -> listOf("mock-model")
        }

        val customRaw = prefs.getString("${KEY_CUSTOM_MODELS_PREFIX}_${provider.name}", null)
        val customList = if (!customRaw.isNullOrBlank()) {
            try {
                json.decodeFromString<List<String>>(customRaw)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        return (builtIn + customList).distinct()
    }

    fun addCustomModel(provider: LlmProvider, modelName: String) {
        val clean = modelName.trim()
        if (clean.isBlank()) return
        val current = getModelsForProvider(provider)
        if (!current.contains(clean)) {
            val customRaw = prefs.getString("${KEY_CUSTOM_MODELS_PREFIX}_${provider.name}", null)
            val customList = if (!customRaw.isNullOrBlank()) {
                try {
                    json.decodeFromString<List<String>>(customRaw).toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
            customList.add(clean)
            prefs.edit().putString("${KEY_CUSTOM_MODELS_PREFIX}_${provider.name}", json.encodeToString(customList)).apply()
        }
    }

    fun removeCustomModel(provider: LlmProvider, modelName: String) {
        val customRaw = prefs.getString("${KEY_CUSTOM_MODELS_PREFIX}_${provider.name}", null) ?: return
        try {
            val customList = json.decodeFromString<List<String>>(customRaw).toMutableList()
            customList.remove(modelName)
            prefs.edit().putString("${KEY_CUSTOM_MODELS_PREFIX}_${provider.name}", json.encodeToString(customList)).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun getRecentlyUsedModels(): List<String> {
        val raw = prefs.getString(KEY_RECENT_MODELS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun recordModelUsed(modelName: String) {
        val clean = modelName.trim()
        if (clean.isBlank()) return
        val recent = getRecentlyUsedModels().toMutableList()
        recent.remove(clean)
        recent.add(0, clean)
        val trimmed = recent.take(6)
        prefs.edit().putString(KEY_RECENT_MODELS, json.encodeToString(trimmed)).apply()
    }

    companion object {
        private const val PREFS_NAME = "webranger_ai_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_PROVIDER = "llm_provider"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_MODEL = "llm_model"
        private const val KEY_BASE_URL = "llm_base_url"
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_CUSTOM_MODELS_PREFIX = "custom_models"
        private const val KEY_RECENT_MODELS = "recent_models"
    }
}
