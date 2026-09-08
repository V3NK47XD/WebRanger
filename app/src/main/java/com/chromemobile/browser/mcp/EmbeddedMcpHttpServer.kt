package com.chromemobile.browser.mcp

import android.graphics.Bitmap
import com.chromemobile.browser.tab.TabManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded Localhost HTTP + SSE Server for WebRanger MCP.
 * Enables external AI agents (omp, Claude Code, Cursor, terminal tools) in Termux
 * to connect directly to the running Chromium mobile browser.
 */
class EmbeddedMcpHttpServer(
    private val mcpServer: MobileChromeMcpServer,
    private val tabManager: TabManager? = null,
    val port: Int = DEFAULT_PORT,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // Active SSE sessions: sessionId -> SSE client OutputStream
    private val sseSessions = ConcurrentHashMap<String, OutputStream>()

    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Start the embedded server on 127.0.0.1:port
     */
    fun start(): Boolean {
        if (isRunning) return true

        return try {
            val address = InetAddress.getByName("127.0.0.1")
            val socket = ServerSocket(port, 50, address)
            serverSocket = socket
            isRunning = true

            serverJob = scope.launch {
                while (isActive && !socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        launch {
                            handleClientSocket(clientSocket)
                        }
                    } catch (e: SocketException) {
                        // Server socket closed
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning = false
            false
        }
    }

    /**
     * Stop the server and close all active SSE connections
     */
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverSocket = null

        sseSessions.forEach { (_, stream) ->
            try {
                stream.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        sseSessions.clear()
        serverJob?.cancel()
        serverJob = null
    }

    private suspend fun handleClientSocket(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 30000
            val input = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = rawPath.substringBefore("?")
            val queryString = if (rawPath.contains("?")) rawPath.substringAfter("?") else ""

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val colonIdx = line!!.indexOf(":")
                if (colonIdx > 0) {
                    val key = line!!.substring(0, colonIdx).trim().lowercase()
                    val value = line!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            // Handle CORS Preflight
            if (method == "OPTIONS") {
                sendCorsPreflightResponse(output)
                socket.close()
                return@withContext
            }

            when {
                // MCP SSE endpoint: GET /sse
                method == "GET" && (path == "/sse" || path == "/events") -> {
                    handleSseConnection(socket, output)
                    // Keep socket open for SSE stream; do not close
                    return@withContext
                }

                // MCP SSE message delivery: POST /message?sessionId=...
                method == "POST" && (path == "/message" || path == "/msg") -> {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = readBody(reader, contentLength)
                    val sessionId = parseQueryParam(queryString, "sessionId")
                    handleSseMessagePost(output, sessionId, body)
                    socket.close()
                }

                // Standard MCP HTTP JSON-RPC endpoint: POST /mcp or POST /
                method == "POST" && (path == "/mcp" || path == "/" || path == "/rpc") -> {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = readBody(reader, contentLength)
                    val responseJson = processMcpRequest(body)
                    sendJsonResponse(output, 200, responseJson)
                    socket.close()
                }

                // Server health & status: GET /status or GET /
                method == "GET" && (path == "/status" || path == "/" || path == "/health") -> {
                    val statusJson = buildStatusJson()
                    sendJsonResponse(output, 200, statusJson)
                    socket.close()
                }

                // Direct screenshot endpoint: GET /screenshot
                method == "GET" && path == "/screenshot" -> {
                    handleScreenshotRequest(output)
                    socket.close()
                }

                // List open browser tabs: GET /tabs
                method == "GET" && path == "/tabs" -> {
                    val tabsJson = buildTabsJson()
                    sendJsonResponse(output, 200, tabsJson)
                    socket.close()
                }

                else -> {
                    sendJsonResponse(output, 404, """{"error":"Not Found","path":"$path"}""")
                    socket.close()
                }
            }
        } catch (e: Exception) {
            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Establish long-lived SSE connection for Model Context Protocol
     */
    private fun handleSseConnection(socket: Socket, output: OutputStream) {
        val sessionId = UUID.randomUUID().toString()
        socket.soTimeout = 0 // Disable read timeout for persistent SSE stream

        val sseHeader = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream\r\n")
            append("Cache-Control: no-cache, no-transform\r\n")
            append("Connection: keep-alive\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("X-Accel-Buffering: no\r\n")
            append("\r\n")
            // Send initial MCP endpoint announcement event
            append("event: endpoint\r\n")
            append("data: /message?sessionId=$sessionId\r\n\r\n")
        }

        output.write(sseHeader.toByteArray(StandardCharsets.UTF_8))
        output.flush()

        sseSessions[sessionId] = output
    }

    /**
     * Handle incoming JSON-RPC POST request paired with an active SSE session
     */
    private suspend fun handleSseMessagePost(output: OutputStream, sessionId: String?, body: String) {
        val sseOut = sessionId?.let { sseSessions[it] }

        val responseJson = processMcpRequest(body)

        if (sseOut != null) {
            try {
                val ssePayload = "event: message\r\ndata: $responseJson\r\n\r\n"
                synchronized(sseOut) {
                    sseOut.write(ssePayload.toByteArray(StandardCharsets.UTF_8))
                    sseOut.flush()
                }
                sendTextResponse(output, 202, "Accepted")
            } catch (e: Exception) {
                sseSessions.remove(sessionId)
                sendJsonResponse(output, 200, responseJson)
            }
        } else {
            // Direct return if no SSE session registered
            sendJsonResponse(output, 200, responseJson)
        }
    }

    /**
     * Process MCP JSON-RPC 2.0 or direct tool call request
     */
    suspend fun processMcpRequest(body: String): String {
        return try {
            if (body.isBlank()) {
                return buildErrorJson(null, -32700, "Parse error: empty request")
            }

            val parsed = json.parseToJsonElement(body).jsonObject
            val id = parsed["id"]

            // Check if this is a JSON-RPC 2.0 request
            val method = parsed["method"]?.jsonPrimitive?.content

            if (method != null) {
                when (method) {
                    "initialize" -> {
                        val initResult = buildJsonObject {
                            put("protocolVersion", "2024-11-05")
                            put("capabilities", buildJsonObject {
                                put("tools", buildJsonObject {
                                    put("listChanged", false)
                                })
                            })
                            put("serverInfo", buildJsonObject {
                                put("name", "WebRanger")
                                put("version", "1.0.0")
                            })
                        }
                        buildSuccessJson(id, initResult)
                    }

                    "notifications/initialized", "initialized" -> {
                        buildJsonObject {
                            put("jsonrpc", "2.0")
                            if (id != null) put("id", id)
                            put("result", buildJsonObject {})
                        }.toString()
                    }

                    "tools/list" -> {
                        val toolsList = mcpServer.listTools()
                        val toolsArray = buildJsonArray {
                            for (tool in toolsList) {
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("inputSchema", tool.getInputSchema())
                                })
                            }
                        }
                        val resultObj = buildJsonObject {
                            put("tools", toolsArray)
                        }
                        buildSuccessJson(id, resultObj)
                    }

                    "tools/call" -> {
                        val params = parsed["params"]?.jsonObject
                        val toolName = params?.get("name")?.jsonPrimitive?.content
                            ?: return buildErrorJson(id, -32602, "Missing 'name' in tool call params")

                        val argsObj = params["arguments"]?.jsonObject ?: buildJsonObject {}
                        val argsMap = mutableMapOf<String, JsonElement>()
                        for ((k, v) in argsObj) {
                            argsMap[k] = v
                        }

                        val toolResponse = mcpServer.callTool(McpCallToolRequest(toolName, argsMap))

                        val contentArray = buildJsonArray {
                            for (item in toolResponse.content) {
                                add(buildJsonObject {
                                    put("type", item.type)
                                    if (item.text != null) put("text", item.text)
                                    if (item.data != null) put("data", item.data)
                                    if (item.mimeType != null) put("mimeType", item.mimeType)
                                })
                            }
                        }

                        val resultObj = buildJsonObject {
                            put("content", contentArray)
                            put("isError", toolResponse.isError)
                        }
                        buildSuccessJson(id, resultObj)
                    }

                    "ping" -> {
                        buildSuccessJson(id, buildJsonObject {})
                    }

                    else -> {
                        buildErrorJson(id, -32601, "Method not found: '$method'")
                    }
                }
            } else {
                // Direct tool invocation fallback (e.g. { "name": "chrome_navigate", "arguments": { "url": "..." } })
                val toolName = parsed["name"]?.jsonPrimitive?.content
                    ?: parsed["tool"]?.jsonPrimitive?.content
                    ?: return buildErrorJson(id, -32600, "Invalid Request: missing 'method' or 'name'")

                val argsObj = (parsed["arguments"] ?: parsed["args"] ?: parsed["parameters"])?.jsonObject ?: buildJsonObject {}
                val argsMap = mutableMapOf<String, JsonElement>()
                for ((k, v) in argsObj) {
                    argsMap[k] = v
                }

                val toolResponse = mcpServer.callTool(McpCallToolRequest(toolName, argsMap))
                json.encodeToString(toolResponse)
            }
        } catch (e: Exception) {
            buildErrorJson(null, -32603, "Internal error: ${e.message}")
        }
    }

    private suspend fun handleScreenshotRequest(output: OutputStream) {
        val bitmap = mcpServer.currentEngine.captureScreenshotAsync()
        if (bitmap != null) {
            val bytesStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, bytesStream)
            val bytes = bytesStream.toByteArray()

            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: image/png\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("\r\n")
            }
            output.write(header.toByteArray(StandardCharsets.UTF_8))
            output.write(bytes)
            output.flush()
        } else {
            sendJsonResponse(output, 500, """{"error":"Failed to capture screenshot"}""")
        }
    }

    private fun buildStatusJson(): String {
        val engine = mcpServer.currentEngine
        val state = engine.state.value
        val tm = tabManager

        val activeTabObj = buildJsonObject {
            put("id", tm?.activeTabId?.value ?: "main")
            put("title", state.title)
            put("url", state.currentUrl)
            put("isLoading", state.isLoading)
        }

        return buildJsonObject {
            put("server", "WebRanger MCP Server")
            put("status", "running")
            put("version", "1.0.0")
            put("port", port)
            put("activeTab", activeTabObj)
            put("tabsCount", tm?.tabs?.value?.size ?: 1)
            put("toolsCount", mcpServer.listTools().size)
        }.toString()
    }

    private fun buildTabsJson(): String {
        val tm = tabManager
        val tabsArray = buildJsonArray {
            if (tm != null) {
                for (tab in tm.tabs.value) {
                    add(buildJsonObject {
                        put("id", tab.id)
                        put("title", tab.engine.state.value.title.ifBlank { "New Tab" })
                        put("url", tab.engine.state.value.currentUrl)
                        put("isActive", tab.id == tm.activeTabId.value)
                        put("hasAiContext", tab.hasAiContext)
                    })
                }
            } else {
                val state = mcpServer.currentEngine.state.value
                add(buildJsonObject {
                    put("id", "main")
                    put("title", state.title.ifBlank { "Active Tab" })
                    put("url", state.currentUrl)
                    put("isActive", true)
                    put("hasAiContext", false)
                })
            }
        }
        return buildJsonObject {
            put("tabs", tabsArray)
        }.toString()
    }

    private fun readBody(reader: BufferedReader, length: Int): String {
        if (length <= 0) return ""
        val chars = CharArray(length)
        var readTotal = 0
        while (readTotal < length) {
            val count = reader.read(chars, readTotal, length - readTotal)
            if (count < 0) break
            readTotal += count
        }
        return String(chars, 0, readTotal)
    }

    private fun parseQueryParam(query: String, paramName: String): String? {
        if (query.isBlank()) return null
        return query.split("&")
            .map { it.split("=") }
            .firstOrNull { it.size == 2 && it[0] == paramName }
            ?.get(1)
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, bodyJson: String) {
        val bytes = bodyJson.toByteArray(StandardCharsets.UTF_8)
        val statusMsg = if (statusCode == 200) "OK" else if (statusCode == 404) "Not Found" else "Error"
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusMsg\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, Authorization, x-requested-with\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendTextResponse(output: OutputStream, statusCode: Int, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $statusCode OK\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendCorsPreflightResponse(output: OutputStream) {
        val header = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, Authorization, x-requested-with\r\n")
            append("Access-Control-Max-Age: 86400\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun buildSuccessJson(id: JsonElement?, result: JsonElement): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id) else put("id", JsonPrimitive(1))
            put("result", result)
        }.toString()
    }

    private fun buildErrorJson(id: JsonElement?, code: Int, message: String): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id) else put("id", JsonPrimitive(1))
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }.toString()
    }

    companion object {
        const val DEFAULT_PORT = 8765
    }
}
