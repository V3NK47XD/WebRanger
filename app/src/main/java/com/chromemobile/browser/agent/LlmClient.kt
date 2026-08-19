package com.chromemobile.browser.agent

import com.chromemobile.browser.mcp.McpTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

enum class LlmProvider {
    ANTHROPIC,
    OPENAI,
    GEMINI,
    OLLAMA,
    MOCK
}

data class LlmConfig(
    val provider: LlmProvider = LlmProvider.OPENAI,
    val apiKey: String = "",
    val model: String = "gpt-4o",
    val baseUrl: String = ""
)

@Serializable
data class LlmMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<AgentToolCall> = emptyList(),
    val toolCallId: String? = null
)

data class LlmResponse(
    val content: String? = null,
    val reasoning: String? = null,
    val toolCalls: List<AgentToolCall> = emptyList()
)

class LlmClient(
    var config: LlmConfig = LlmConfig(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    suspend fun chatCompletion(
        messages: List<LlmMessage>,
        tools: List<McpTool>,
        onReasoningChunk: ((String) -> Unit)? = null
    ): LlmResponse = withContext(Dispatchers.IO) {
        when (config.provider) {
            LlmProvider.OPENAI, LlmProvider.OLLAMA -> callOpenAi(messages, tools, onReasoningChunk)
            LlmProvider.ANTHROPIC -> callAnthropic(messages, tools, onReasoningChunk)
            LlmProvider.GEMINI -> callGemini(messages, tools)
            LlmProvider.MOCK -> mockResponse(messages)
        }
    }

    /**
     * OpenAI API, Gemma / Ollama compatibility endpoint with Hybrid Tool Extraction
     */
    private fun callOpenAi(
        messages: List<LlmMessage>,
        tools: List<McpTool>,
        onReasoningChunk: ((String) -> Unit)?
    ): LlmResponse {
        val endpoint = if (config.baseUrl.isNotEmpty()) {
            "${config.baseUrl.removeSuffix("/")}/chat/completions"
        } else {
            "https://api.openai.com/v1/chat/completions"
        }

        val requestJson = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.1)
            put("messages", buildJsonArray {
                for (msg in messages) {
                    add(buildJsonObject {
                        put("role", msg.role)
                        if (msg.content != null) put("content", msg.content)
                        if (msg.toolCallId != null) put("tool_call_id", msg.toolCallId)
                        if (msg.toolCalls.isNotEmpty()) {
                            put("tool_calls", buildJsonArray {
                                for (tc in msg.toolCalls) {
                                    add(buildJsonObject {
                                        put("id", tc.toolCallId)
                                        put("type", "function")
                                        put("function", buildJsonObject {
                                            put("name", tc.name)
                                            put("arguments", json.encodeToString(JsonObject.serializer(), JsonObject(tc.arguments)))
                                        })
                                    })
                                }
                            })
                        }
                    })
                }
            })

            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in tools) {
                        add(tool.toOpenAiToolSchema())
                    }
                })
                put("tool_choice", "auto")
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBody)

        if (config.apiKey.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response from API endpoint")

        if (!response.isSuccessful) {
            throw RuntimeException("API error (${response.code}): $responseBody")
        }

        val root = json.parseToJsonElement(responseBody).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return LlmResponse(content = "No response generated")

        val messageObj = choice["message"]?.jsonObject ?: return LlmResponse()
        val content = messageObj["content"]?.jsonPrimitive?.content
        val rawToolCalls = messageObj["tool_calls"]?.jsonArray

        val parsedToolCalls = mutableListOf<AgentToolCall>()

        // 1. Check native OpenAI structured tool_calls
        if (rawToolCalls != null && rawToolCalls.isNotEmpty()) {
            for (tc in rawToolCalls) {
                val tcObj = tc.jsonObject
                val tcId = tcObj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                val fnObj = tcObj["function"]?.jsonObject
                val name = fnObj?.get("name")?.jsonPrimitive?.content ?: continue
                val argsString = fnObj["arguments"]?.jsonPrimitive?.content ?: "{}"
                val argsMap = try {
                    json.parseToJsonElement(argsString).jsonObject.toMap()
                } catch (e: Exception) {
                    emptyMap()
                }

                parsedToolCalls.add(
                    AgentToolCall(
                        toolCallId = tcId,
                        name = name,
                        arguments = argsMap
                    )
                )
            }
        }

        // 2. Hybrid Fallback: If native tool_calls is empty, parse Gemma / local text tool format
        if (parsedToolCalls.isEmpty() && !content.isNullOrBlank()) {
            val textExtractedTools = extractToolCallsFromText(content)
            parsedToolCalls.addAll(textExtractedTools)
        }

        return LlmResponse(
            content = content,
            toolCalls = parsedToolCalls
        )
    }

    /**
     * Anthropic Claude Messages API
     */
    private fun callAnthropic(
        messages: List<LlmMessage>,
        tools: List<McpTool>,
        onReasoningChunk: ((String) -> Unit)?
    ): LlmResponse {
        val endpoint = if (config.baseUrl.isNotEmpty()) {
            "${config.baseUrl.removeSuffix("/")}/v1/messages"
        } else {
            "https://api.anthropic.com/v1/messages"
        }

        val systemMessage = messages.find { it.role == "system" }?.content ?: ""
        val nonSystemMessages = messages.filter { it.role != "system" }

        val requestJson = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 2048)
            put("temperature", 0.1)
            if (systemMessage.isNotEmpty()) {
                put("system", systemMessage)
            }

            put("messages", buildJsonArray {
                for (msg in nonSystemMessages) {
                    add(buildJsonObject {
                        put("role", if (msg.role == "tool") "user" else msg.role)
                        if (msg.role == "tool") {
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", msg.toolCallId ?: "")
                                    put("content", msg.content ?: "")
                                })
                            })
                        } else if (msg.toolCalls.isNotEmpty()) {
                            put("content", buildJsonArray {
                                for (tc in msg.toolCalls) {
                                    add(buildJsonObject {
                                        put("type", "tool_use")
                                        put("id", tc.toolCallId)
                                        put("name", tc.name)
                                        put("input", JsonObject(tc.arguments))
                                    })
                                }
                            })
                        } else {
                            put("content", msg.content ?: "")
                        }
                    })
                }
            })

            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in tools) {
                        add(tool.toAnthropicToolSchema())
                    }
                })
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response from Anthropic")

        if (!response.isSuccessful) {
            throw RuntimeException("Anthropic API error (${response.code}): $responseBody")
        }

        val root = json.parseToJsonElement(responseBody).jsonObject
        val contentArray = root["content"]?.jsonArray ?: return LlmResponse()

        var textContent: String? = null
        var thinkingContent: String? = null
        val parsedToolCalls = mutableListOf<AgentToolCall>()

        for (item in contentArray) {
            val itemObj = item.jsonObject
            val type = itemObj["type"]?.jsonPrimitive?.content

            if (type == "text") {
                textContent = (textContent ?: "") + (itemObj["text"]?.jsonPrimitive?.content ?: "")
            } else if (type == "thinking") {
                thinkingContent = itemObj["thinking"]?.jsonPrimitive?.content
            } else if (type == "tool_use") {
                val tcId = itemObj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                val name = itemObj["name"]?.jsonPrimitive?.content ?: continue
                val inputObj = itemObj["input"]?.jsonObject ?: JsonObject(emptyMap())

                parsedToolCalls.add(
                    AgentToolCall(
                        toolCallId = tcId,
                        name = name,
                        arguments = inputObj.toMap()
                    )
                )
            }
        }

        if (parsedToolCalls.isEmpty() && !textContent.isNullOrBlank()) {
            parsedToolCalls.addAll(extractToolCallsFromText(textContent))
        }

        return LlmResponse(
            content = textContent,
            reasoning = thinkingContent,
            toolCalls = parsedToolCalls
        )
    }

    /**
     * Google Gemini API with Function Declarations & Text Fallback
     */
    private fun callGemini(
        messages: List<LlmMessage>,
        tools: List<McpTool>
    ): LlmResponse {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}"
        val systemMessage = messages.find { it.role == "system" }?.content ?: ""
        val nonSystemMessages = messages.filter { it.role != "system" }

        val requestJson = buildJsonObject {
            if (systemMessage.isNotEmpty()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemMessage) })
                    })
                })
            }

            put("contents", buildJsonArray {
                for (msg in nonSystemMessages) {
                    add(buildJsonObject {
                        put("role", if (msg.role == "assistant") "model" else "user")
                        put("parts", buildJsonArray {
                            if (!msg.content.isNullOrBlank()) {
                                add(buildJsonObject { put("text", msg.content) })
                            }
                        })
                    })
                }
            })

            put("generationConfig", buildJsonObject {
                put("temperature", 0.1)
                put("maxOutputTokens", 2048)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response from Gemini")

        if (!response.isSuccessful) {
            throw RuntimeException("Gemini API error (${response.code}): $responseBody")
        }

        val root = json.parseToJsonElement(responseBody).jsonObject
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray

        var textContent = ""
        val parsedToolCalls = mutableListOf<AgentToolCall>()

        if (parts != null) {
            for (part in parts) {
                val partObj = part.jsonObject
                val text = partObj["text"]?.jsonPrimitive?.content
                if (!text.isNullOrBlank()) {
                    textContent += text
                }
                val functionCall = partObj["functionCall"]?.jsonObject
                if (functionCall != null) {
                    val name = functionCall["name"]?.jsonPrimitive?.content ?: continue
                    val argsObj = functionCall["args"]?.jsonObject ?: JsonObject(emptyMap())
                    parsedToolCalls.add(
                        AgentToolCall(
                            toolCallId = UUID.randomUUID().toString(),
                            name = name,
                            arguments = argsObj.toMap()
                        )
                    )
                }
            }
        }

        if (parsedToolCalls.isEmpty() && textContent.isNotEmpty()) {
            parsedToolCalls.addAll(extractToolCallsFromText(textContent))
        }

        return LlmResponse(
            content = textContent.ifEmpty { null },
            toolCalls = parsedToolCalls
        )
    }

    /**
     * Robust Hybrid Tool Call Extractor for Gemma, Qwen, DeepSeek & Gemini Flash text outputs
     */
    private fun extractToolCallsFromText(text: String): List<AgentToolCall> {
        val extracted = mutableListOf<AgentToolCall>()

        // Pattern A: <tool_call>{"name": "...", "arguments": {...}}</tool_call>
        val toolTagMatcher = Pattern.compile("(?s)<tool_call>(.*?)</tool_call>").matcher(text)
        while (toolTagMatcher.find()) {
            val jsonStr = toolTagMatcher.group(1)?.trim() ?: continue
            tryParseJsonToolCall(jsonStr)?.let { extracted.add(it) }
        }

        // Pattern B: ```json\n{"name": "chrome_...", "arguments": {...}}\n```
        if (extracted.isEmpty()) {
            val codeBlockMatcher = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?\\})\\s*```").matcher(text)
            while (codeBlockMatcher.find()) {
                val jsonStr = codeBlockMatcher.group(1)?.trim() ?: continue
                tryParseJsonToolCall(jsonStr)?.let { extracted.add(it) }
            }
        }

        // Pattern C: Action / Function call syntax: chrome_click_element(element_id=2) or chrome_type_text(element_id=1, text="...")
        if (extracted.isEmpty()) {
            val fnSyntaxMatcher = Pattern.compile("(chrome_[a-zA-Z_]+)\\s*\\((.*?)\\)").matcher(text)
            while (fnSyntaxMatcher.find()) {
                val fnName = fnSyntaxMatcher.group(1) ?: continue
                val rawArgs = fnSyntaxMatcher.group(2) ?: ""
                val argsMap = parseFunctionArguments(rawArgs)
                extracted.add(
                    AgentToolCall(
                        toolCallId = UUID.randomUUID().toString(),
                        name = fnName,
                        arguments = argsMap
                    )
                )
            }
        }

        return extracted
    }

    private fun tryParseJsonToolCall(jsonStr: String): AgentToolCall? {
        return try {
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            val name = obj["name"]?.jsonPrimitive?.content
                ?: obj["tool"]?.jsonPrimitive?.content
                ?: obj["action"]?.jsonPrimitive?.content
                ?: return null

            val argsObj = obj["arguments"]?.jsonObject
                ?: obj["parameters"]?.jsonObject
                ?: obj["args"]?.jsonObject
                ?: obj

            val cleanedArgs = argsObj.toMap().filterKeys { it != "name" && it != "tool" && it != "action" }

            AgentToolCall(
                toolCallId = UUID.randomUUID().toString(),
                name = name,
                arguments = cleanedArgs
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseFunctionArguments(rawArgs: String): Map<String, JsonElement> {
        val map = mutableMapOf<String, JsonElement>()
        val argPairs = rawArgs.split(",")
        for (pair in argPairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val rawVal = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
                val intVal = rawVal.toIntOrNull()
                val boolVal = rawVal.toBooleanStrictOrNull()
                map[key] = when {
                    intVal != null -> JsonPrimitive(intVal)
                    boolVal != null -> JsonPrimitive(boolVal)
                    else -> JsonPrimitive(rawVal)
                }
            }
        }
        return map
    }

    /**
     * Deterministic Mock Provider for offline testing
     */
    private fun mockResponse(messages: List<LlmMessage>): LlmResponse {
        val lastUserMessage = messages.findLast { it.role == "user" }?.content ?: ""

        return when {
            lastUserMessage.contains("search for 'Chromium'", ignoreCase = true) -> {
                LlmResponse(
                    content = null,
                    reasoning = "I will type 'Chromium' into the search input and submit.",
                    toolCalls = listOf(
                        AgentToolCall(
                            toolCallId = "mock_call_1",
                            name = "chrome_type_text",
                            arguments = mapOf(
                                "element_id" to JsonPrimitive(1),
                                "text" to JsonPrimitive("Chromium"),
                                "press_enter" to JsonPrimitive(true)
                            )
                        )
                    )
                )
            }
            lastUserMessage.contains("first paragraph", ignoreCase = true) || lastUserMessage.contains("Chromium (web browser)", ignoreCase = true) -> {
                LlmResponse(
                    content = null,
                    reasoning = "I have extracted the article content. Finishing task.",
                    toolCalls = listOf(
                        AgentToolCall(
                            toolCallId = "mock_call_2",
                            name = "chrome_finish_task",
                            arguments = mapOf(
                                "answer" to JsonPrimitive("Chromium is a free and open-source web browser project, mainly developed and maintained by Google."),
                                "success" to JsonPrimitive(true)
                            )
                        )
                    )
                )
            }
            else -> {
                LlmResponse(
                    content = "Task goal acknowledged.",
                    toolCalls = listOf(
                        AgentToolCall(
                            toolCallId = "mock_call_0",
                            name = "chrome_get_dom_snapshot",
                            arguments = mapOf("viewport_only" to JsonPrimitive(true))
                        )
                    )
                )
            }
        }
    }
}
