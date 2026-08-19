package com.chromemobile.browser.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class McpProperty(
    val type: String,
    val description: String,
    val enumValues: List<String>? = null
)

@Serializable
data class McpTool(
    val name: String,
    val description: String,
    val properties: Map<String, McpProperty> = emptyMap(),
    val required: List<String> = emptyList()
) {
    /**
     * Standard JSON Schema representation for MCP inputSchema
     */
    fun getInputSchema(): JsonObject {
        return buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                for ((key, prop) in properties) {
                    put(key, buildJsonObject {
                        put("type", prop.type)
                        put("description", prop.description)
                        if (prop.enumValues != null) {
                            put("enum", buildJsonArray {
                                for (v in prop.enumValues) add(kotlinx.serialization.json.JsonPrimitive(v))
                            })
                        }
                    })
                }
            })
            if (required.isNotEmpty()) {
                put("required", buildJsonArray {
                    for (r in required) add(kotlinx.serialization.json.JsonPrimitive(r))
                })
            }
        }
    }

    /**
     * Convert to OpenAI Function Tool Schema
     */
    fun toOpenAiToolSchema(): JsonObject {
        return buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", name)
                put("description", description)
                put("parameters", getInputSchema())
            })
        }
    }

    /**
     * Convert to Anthropic Tool Schema
     */
    fun toAnthropicToolSchema(): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("description", description)
            put("input_schema", getInputSchema())
        }
    }

    /**
     * Convert to Google Gemini Function Declaration Schema
     */
    fun toGeminiFunctionDeclaration(): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("description", description)
            put("parameters", buildJsonObject {
                put("type", "OBJECT")
                put("properties", buildJsonObject {
                    for ((key, prop) in properties) {
                        put(key, buildJsonObject {
                            val geminiType = when (prop.type.lowercase()) {
                                "integer", "int" -> "INTEGER"
                                "number", "float", "double" -> "NUMBER"
                                "boolean", "bool" -> "BOOLEAN"
                                "array" -> "ARRAY"
                                else -> "STRING"
                            }
                            put("type", geminiType)
                            put("description", prop.description)
                            if (prop.enumValues != null) {
                                put("enum", buildJsonArray {
                                    for (v in prop.enumValues) add(kotlinx.serialization.json.JsonPrimitive(v))
                                })
                            }
                        })
                    }
                })
                if (required.isNotEmpty()) {
                    put("required", buildJsonArray {
                        for (r in required) add(kotlinx.serialization.json.JsonPrimitive(r))
                    })
                }
            })
        }
    }
}

@Serializable
data class McpContent(
    val type: String, // "text" or "image"
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

@Serializable
data class McpCallToolRequest(
    val name: String,
    val arguments: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)

@Serializable
data class McpCallToolResponse(
    val content: List<McpContent> = emptyList(),
    val isError: Boolean = false,
    val highlightedElementId: Int? = null
) {
    fun getCombinedText(): String {
        return content.joinToString("\n") { it.text ?: "" }
    }
}
