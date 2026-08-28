package pyhon.pro.localhost_ai.server

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pyhon.pro.localhost_ai.engine.GenerationResult
import pyhon.pro.localhost_ai.engine.LocalAiEngine
import pyhon.pro.localhost_ai.engine.TokenCallback
import pyhon.pro.localhost_ai.util.PreferencesManager
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * OpenAI-compatible HTTP Server providing REST API endpoints on LAN.
 */
class OpenAiServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ChatMessage::class.java, ChatMessageDeserializer())
        .create()
    private val serverStartTime = System.currentTimeMillis()
    private val serverScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "OpenAiServer"
    }

    override fun serve(session: IHTTPSession): Response {
        val startTime = SystemClock.elapsedRealtime()
        val uri = session.uri
        val method = session.method
        val clientIp = session.remoteIpAddress ?: "unknown"

        Log.i(TAG, "Incoming HTTP request: $method $uri from $clientIp")

        // Handle CORS Pre-flight
        if (method == Method.OPTIONS) {
            return createCorsPreflightResponse()
        }

        // Optional API Key Verification
        val requiredApiKey = PreferencesManager.getApiKey(context)
        if (requiredApiKey.isNotBlank()) {
            val authHeader = session.headers["authorization"] ?: session.headers["Authorization"]
            val expectedAuth = "Bearer $requiredApiKey"
            if (authHeader != expectedAuth) {
                val errJson = """{"error":{"message":"Invalid API key. Please provide a valid Bearer token.","type":"invalid_request_error","code":"invalid_api_key"}}"""
                ServerLogManager.addLog(
                    ServerLogEntry(
                        method = method.name,
                        path = uri,
                        clientIp = clientIp,
                        statusCode = 401,
                        durationMs = SystemClock.elapsedRealtime() - startTime,
                        error = "Unauthorized API key"
                    )
                )
                return createJsonResponse(Response.Status.UNAUTHORIZED, errJson)
            }
        }

        return try {
            when {
                uri == "/" || uri == "/health" -> handleHealth(startTime, clientIp)
                uri == "/v1/models" && method == Method.GET -> handleModels(startTime, clientIp)
                (uri == "/v1/chat/completions" || uri == "/chat/completions") && method == Method.POST -> {
                    handleChatCompletions(session, startTime, clientIp)
                }
                (uri == "/v1/completions" || uri == "/completions") && method == Method.POST -> {
                    handleCompletions(session, startTime, clientIp)
                }
                else -> {
                    val notFound = """{"error":{"message":"Endpoint not found: $uri","type":"invalid_request_error","code":"resource_not_found"}}"""
                    ServerLogManager.addLog(
                        ServerLogEntry(
                            method = method.name,
                            path = uri,
                            clientIp = clientIp,
                            statusCode = 404,
                            durationMs = SystemClock.elapsedRealtime() - startTime,
                            error = "Not found"
                        )
                    )
                    createJsonResponse(Response.Status.NOT_FOUND, notFound)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server internal error processing $uri", e)
            val errJson = """{"error":{"message":"Internal server error: ${e.message}","type":"internal_server_error","code":"server_error"}}"""
            ServerLogManager.addLog(
                ServerLogEntry(
                    method = method.name,
                    path = uri,
                    clientIp = clientIp,
                    statusCode = 500,
                    durationMs = SystemClock.elapsedRealtime() - startTime,
                    error = e.message
                )
            )
            createJsonResponse(Response.Status.INTERNAL_ERROR, errJson)
        }
    }

    private fun handleHealth(startTime: Long, clientIp: String): Response {
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val uptimeSec = (System.currentTimeMillis() - serverStartTime) / 1000

        val health = HealthResponse(
            status = if (LocalAiEngine.isLoaded.value) "ready" else "idle",
            activeModel = LocalAiEngine.activeModelName.value ?: "No model loaded",
            backend = LocalAiEngine.activeBackend.value,
            memoryUsedMb = usedMemMb,
            uptimeSec = uptimeSec
        )

        ServerLogManager.addLog(
            ServerLogEntry(
                method = "GET",
                path = "/health",
                clientIp = clientIp,
                statusCode = 200,
                durationMs = SystemClock.elapsedRealtime() - startTime
            )
        )

        return createJsonResponse(Response.Status.OK, gson.toJson(health))
    }

    private fun handleModels(startTime: Long, clientIp: String): Response {
        val activeName = LocalAiEngine.activeModelName.value ?: "default"
        val modelObjects = listOf(
            ModelObject(
                id = activeName,
                created = System.currentTimeMillis() / 1000,
                ownedBy = "localhost-ai"
            )
        )

        val response = ModelListResponse(data = modelObjects)

        ServerLogManager.addLog(
            ServerLogEntry(
                method = "GET",
                path = "/v1/models",
                clientIp = clientIp,
                statusCode = 200,
                durationMs = SystemClock.elapsedRealtime() - startTime
            )
        )

        return createJsonResponse(Response.Status.OK, gson.toJson(response))
    }

    private fun handleChatCompletions(
        session: IHTTPSession,
        startTime: Long,
        clientIp: String
    ): Response {
        if (!LocalAiEngine.isLoaded.value) {
            val err = """{"error":{"message":"No model is currently loaded in LocalHost AI. Please load a model in the app.","type":"server_error","code":"model_not_loaded"}}"""
            ServerLogManager.addLog(
                ServerLogEntry(
                    method = "POST",
                    path = "/v1/chat/completions",
                    clientIp = clientIp,
                    statusCode = 503,
                    durationMs = SystemClock.elapsedRealtime() - startTime,
                    error = "Model not loaded"
                )
            )
            return createJsonResponse(Response.Status.SERVICE_UNAVAILABLE, err)
        }

        val body = parseRequestBody(session)
        val request = try {
            gson.fromJson(body, ChatCompletionRequest::class.java)
        } catch (e: JsonSyntaxException) {
            val err = """{"error":{"message":"Invalid JSON payload: ${e.message}","type":"invalid_request_error"}}"""
            return createJsonResponse(Response.Status.BAD_REQUEST, err)
        }

        val messagesList = request.messages.map { it.role to it.content }
        val prompt = LocalAiEngine.formatChat(messagesList, addGenerationPrompt = true)
        val modelId = LocalAiEngine.activeModelName.value ?: "local-model"
        val requestId = "chatcmpl-${UUID.randomUUID()}"

        if (request.stream) {
            return streamChatCompletions(
                requestId = requestId,
                modelId = modelId,
                prompt = prompt,
                request = request,
                startTime = startTime,
                clientIp = clientIp
            )
        } else {
            return nonStreamChatCompletions(
                requestId = requestId,
                modelId = modelId,
                prompt = prompt,
                request = request,
                startTime = startTime,
                clientIp = clientIp
            )
        }
    }

    private fun streamChatCompletions(
        requestId: String,
        modelId: String,
        prompt: String,
        request: ChatCompletionRequest,
        startTime: Long,
        clientIp: String
    ): Response {
        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut, 64 * 1024)

        serverScope.launch {
            var tokenCount = 0
            val genStartTime = SystemClock.elapsedRealtime()

            try {
                // Send initial role chunk
                val firstChunk = ChatCompletionChunk(
                    id = requestId,
                    model = modelId,
                    choices = listOf(
                        ChatCompletionChunkChoice(
                            index = 0,
                            delta = ChatDelta(role = "assistant", content = ""),
                            finishReason = null
                        )
                    )
                )
                writeSseChunk(pipedOut, gson.toJson(firstChunk))

                // Stream generation tokens
                val callback = object : TokenCallback {
                    override fun onToken(token: String, isFinished: Boolean): Boolean {
                        if (token.isNotEmpty()) {
                            tokenCount++
                            val chunk = ChatCompletionChunk(
                                id = requestId,
                                model = modelId,
                                choices = listOf(
                                    ChatCompletionChunkChoice(
                                        index = 0,
                                        delta = ChatDelta(content = token),
                                        finishReason = null
                                    )
                                )
                            )
                            return writeSseChunk(pipedOut, gson.toJson(chunk))
                        }
                        return true
                    }
                }

                val resultJson = LocalAiEngine.generateComplete(
                    prompt = prompt,
                    maxTokens = request.effectiveMaxTokens,
                    temperature = request.temperature,
                    topP = request.topP,
                    stopWords = request.getStopSequences()
                ).second

                // Send final completion chunk
                val finalChunk = ChatCompletionChunk(
                    id = requestId,
                    model = modelId,
                    choices = listOf(
                        ChatCompletionChunkChoice(
                            index = 0,
                            delta = ChatDelta(),
                            finishReason = "stop"
                        )
                    ),
                    usage = Usage(
                        promptTokens = resultJson.promptTokens,
                        completionTokens = resultJson.completionTokens,
                        totalTokens = resultJson.totalTokens
                    )
                )
                writeSseChunk(pipedOut, gson.toJson(finalChunk))
                writeSseDone(pipedOut)

                val duration = SystemClock.elapsedRealtime() - startTime
                val tps = if (duration > 0) (tokenCount.toDouble() / (duration.toDouble() / 1000.0)) else 0.0

                ServerLogManager.addLog(
                    ServerLogEntry(
                        method = "POST",
                        path = "/v1/chat/completions",
                        clientIp = clientIp,
                        statusCode = 200,
                        durationMs = duration,
                        tokensCount = tokenCount,
                        speedTps = tps,
                        isStream = true
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in SSE streaming", e)
            } finally {
                try {
                    pipedOut.flush()
                    pipedOut.close()
                } catch (ignored: Exception) {}
            }
        }

        val response = newChunkedResponse(Response.Status.OK, "text/event-stream; charset=UTF-8", pipedIn)
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        response.addHeader("X-Accel-Buffering", "no")
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun nonStreamChatCompletions(
        requestId: String,
        modelId: String,
        prompt: String,
        request: ChatCompletionRequest,
        startTime: Long,
        clientIp: String
    ): Response {
        val (text, stats) = runBlockingWithStats(
            prompt = prompt,
            maxTokens = request.effectiveMaxTokens,
            temperature = request.temperature,
            topP = request.topP,
            stopWords = request.getStopSequences()
        )

        val completionResponse = ChatCompletionResponse(
            id = requestId,
            model = modelId,
            choices = listOf(
                ChatCompletionChoice(
                    index = 0,
                    message = ChatMessage(role = "assistant", content = text),
                    finishReason = "stop"
                )
            ),
            usage = Usage(
                promptTokens = stats.promptTokens,
                completionTokens = stats.completionTokens,
                totalTokens = stats.totalTokens
            )
        )

        val duration = SystemClock.elapsedRealtime() - startTime
        val tps = stats.genSpeedTps

        ServerLogManager.addLog(
            ServerLogEntry(
                method = "POST",
                path = "/v1/chat/completions",
                clientIp = clientIp,
                statusCode = 200,
                durationMs = duration,
                tokensCount = stats.completionTokens,
                speedTps = tps,
                isStream = false
            )
        )

        return createJsonResponse(Response.Status.OK, gson.toJson(completionResponse))
    }

    private fun handleCompletions(
        session: IHTTPSession,
        startTime: Long,
        clientIp: String
    ): Response {
        if (!LocalAiEngine.isLoaded.value) {
            val err = """{"error":{"message":"No model loaded","type":"server_error"}}"""
            return createJsonResponse(Response.Status.SERVICE_UNAVAILABLE, err)
        }

        val body = parseRequestBody(session)
        val request = gson.fromJson(body, CompletionRequest::class.java)
        val modelId = LocalAiEngine.activeModelName.value ?: "local-model"
        val requestId = "cmpl-${UUID.randomUUID()}"

        val (text, stats) = runBlockingWithStats(
            prompt = request.getPromptText(),
            maxTokens = request.maxTokens,
            temperature = request.temperature,
            topP = request.topP,
            stopWords = request.getStopSequences()
        )

        val response = CompletionResponse(
            id = requestId,
            model = modelId,
            choices = listOf(
                CompletionChoice(
                    text = text,
                    index = 0,
                    finishReason = "stop"
                )
            ),
            usage = Usage(
                promptTokens = stats.promptTokens,
                completionTokens = stats.completionTokens,
                totalTokens = stats.totalTokens
            )
        )

        ServerLogManager.addLog(
            ServerLogEntry(
                method = "POST",
                path = "/v1/completions",
                clientIp = clientIp,
                statusCode = 200,
                durationMs = SystemClock.elapsedRealtime() - startTime,
                tokensCount = stats.completionTokens,
                speedTps = stats.genSpeedTps,
                isStream = false
            )
        )

        return createJsonResponse(Response.Status.OK, gson.toJson(response))
    }

    private fun runBlockingWithStats(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        stopWords: List<String>
    ): Pair<String, GenerationResult> {
        var resultPair: Pair<String, GenerationResult>? = null
        val latch = java.util.concurrent.CountDownLatch(1)

        serverScope.launch {
            try {
                resultPair = LocalAiEngine.generateComplete(
                    prompt = prompt,
                    maxTokens = maxTokens,
                    temperature = temperature,
                    topP = topP,
                    stopWords = stopWords
                )
            } finally {
                latch.countDown()
            }
        }

        latch.await()
        return resultPair ?: Pair("", GenerationResult(error = "Generation timeout"))
    }

    private fun writeSseChunk(outputStream: PipedOutputStream, json: String): Boolean {
        return try {
            val bytes = "data: $json\n\n".toByteArray(StandardCharsets.UTF_8)
            outputStream.write(bytes)
            outputStream.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeSseDone(outputStream: PipedOutputStream) {
        try {
            val bytes = "data: [DONE]\n\n".toByteArray(StandardCharsets.UTF_8)
            outputStream.write(bytes)
            outputStream.flush()
        } catch (ignored: Exception) {}
    }

    private fun parseRequestBody(session: IHTTPSession): String {
        val map = HashMap<String, String>()
        try {
            session.parseBody(map)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse body in session", e)
        }
        val postData = map["postData"]
        if (!postData.isNullOrBlank()) {
            return postData
        }
        val contentFilePath = map["content"]
        if (!contentFilePath.isNullOrBlank()) {
            val file = File(contentFilePath)
            if (file.exists()) {
                return file.readText(StandardCharsets.UTF_8)
            }
        }
        return ""
    }

    private fun createJsonResponse(status: Response.IStatus, json: String): Response {
        val res = newFixedLengthResponse(status, "application/json; charset=UTF-8", json)
        res.addHeader("Access-Control-Allow-Origin", "*")
        return res
    }

    private fun createCorsPreflightResponse(): Response {
        val res = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
        res.addHeader("Access-Control-Allow-Origin", "*")
        res.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE, HEAD")
        res.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Origin, User-Agent, X-Requested-With")
        res.addHeader("Access-Control-Max-Age", "86400")
        return res
    }
}
