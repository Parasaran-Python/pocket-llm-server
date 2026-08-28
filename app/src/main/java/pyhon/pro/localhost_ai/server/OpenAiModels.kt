package pyhon.pro.localhost_ai.server
 
import com.google.gson.*
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

// Chat Message
data class ChatMessage(
    @SerializedName("role")
    val role: String = "user",

    @SerializedName("content")
    val content: String = "",

    @SerializedName("reasoning_content")
    val reasoningContent: String? = null,

    @SerializedName("name")
    val name: String? = null
)

class ChatMessageDeserializer : JsonDeserializer<ChatMessage> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ChatMessage {
        val obj = json.asJsonObject
        val role = obj.get("role")?.asString ?: "user"
        val reasoningContent = obj.get("reasoning_content")?.asString
        val name = obj.get("name")?.asString

        val contentElem = obj.get("content")
        val contentStr = when {
            contentElem == null || contentElem.isJsonNull -> ""
            contentElem.isJsonPrimitive -> contentElem.asString
            contentElem.isJsonArray -> {
                contentElem.asJsonArray.mapNotNull { item ->
                    when {
                        item.isJsonPrimitive -> item.asString
                        item.isJsonObject -> {
                            val itemObj = item.asJsonObject
                            itemObj.get("text")?.asString
                                ?: itemObj.get("content")?.asString
                        }
                        else -> null
                    }
                }.joinToString("\n")
            }
            else -> contentElem.toString()
        }

        return ChatMessage(
            role = role,
            content = contentStr,
            reasoningContent = reasoningContent,
            name = name
        )
    }
}

// Chat Completion Request
data class ChatCompletionRequest(
    @SerializedName("model")
    val model: String = "default",

    @SerializedName("messages")
    val messages: List<ChatMessage> = emptyList(),

    @SerializedName("temperature")
    val temperature: Float = 0.7f,

    @SerializedName("top_p")
    val topP: Float = 0.9f,

    @SerializedName("max_tokens")
    val maxTokens: Int? = null,

    @SerializedName("max_completion_tokens")
    val maxCompletionTokens: Int? = null,

    @SerializedName("stream")
    val stream: Boolean = false,

    @SerializedName("stop")
    val stop: Any? = null // String or List<String>
) {
    val effectiveMaxTokens: Int
        get() = maxTokens ?: maxCompletionTokens ?: 2048

    fun getStopSequences(): List<String> {
        return when (stop) {
            is String -> listOf(stop)
            is List<*> -> stop.filterIsInstance<String>()
            else -> emptyList()
        }
    }
}

// Non-Streaming Chat Completion Response
data class ChatCompletionResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("object")
    val obj: String = "chat.completion",

    @SerializedName("created")
    val created: Long = System.currentTimeMillis() / 1000,

    @SerializedName("model")
    val model: String,

    @SerializedName("choices")
    val choices: List<ChatCompletionChoice>,

    @SerializedName("usage")
    val usage: Usage
)

data class ChatCompletionChoice(
    @SerializedName("index")
    val index: Int = 0,

    @SerializedName("message")
    val message: ChatMessage,

    @SerializedName("finish_reason")
    val finishReason: String = "stop"
)

// Streaming Chunk Response
data class ChatCompletionChunk(
    @SerializedName("id")
    val id: String,

    @SerializedName("object")
    val obj: String = "chat.completion.chunk",

    @SerializedName("created")
    val created: Long = System.currentTimeMillis() / 1000,

    @SerializedName("model")
    val model: String,

    @SerializedName("choices")
    val choices: List<ChatCompletionChunkChoice>,

    @SerializedName("usage")
    val usage: Usage? = null
)

data class ChatCompletionChunkChoice(
    @SerializedName("index")
    val index: Int = 0,

    @SerializedName("delta")
    val delta: ChatDelta,

    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class ChatDelta(
    @SerializedName("role")
    val role: String? = null,

    @SerializedName("content")
    val content: String? = null
)

// Text Completion Request
data class CompletionRequest(
    @SerializedName("model")
    val model: String = "default",

    @SerializedName("prompt")
    val prompt: Any = "", // String or List<String>

    @SerializedName("max_tokens")
    val maxTokens: Int = 2048,

    @SerializedName("temperature")
    val temperature: Float = 0.7f,

    @SerializedName("top_p")
    val topP: Float = 0.9f,

    @SerializedName("stream")
    val stream: Boolean = false,

    @SerializedName("stop")
    val stop: Any? = null
) {
    fun getPromptText(): String {
        return when (prompt) {
            is String -> prompt
            is List<*> -> prompt.joinToString("\n")
            else -> prompt.toString()
        }
    }

    fun getStopSequences(): List<String> {
        return when (stop) {
            is String -> listOf(stop)
            is List<*> -> stop.filterIsInstance<String>()
            else -> emptyList()
        }
    }
}

// Text Completion Response
data class CompletionResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("object")
    val obj: String = "text_completion",

    @SerializedName("created")
    val created: Long = System.currentTimeMillis() / 1000,

    @SerializedName("model")
    val model: String,

    @SerializedName("choices")
    val choices: List<CompletionChoice>,

    @SerializedName("usage")
    val usage: Usage
)

data class CompletionChoice(
    @SerializedName("text")
    val text: String,

    @SerializedName("index")
    val index: Int = 0,

    @SerializedName("finish_reason")
    val finishReason: String = "stop"
)

// Usage Stats
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,

    @SerializedName("completion_tokens")
    val completionTokens: Int = 0,

    @SerializedName("total_tokens")
    val totalTokens: Int = 0
)

// Models List Response
data class ModelListResponse(
    @SerializedName("object")
    val obj: String = "list",

    @SerializedName("data")
    val data: List<ModelObject>
)

data class ModelObject(
    @SerializedName("id")
    val id: String,

    @SerializedName("object")
    val obj: String = "model",

    @SerializedName("created")
    val created: Long = 1700000000,

    @SerializedName("owned_by")
    val ownedBy: String = "localhost-ai"
)

// Health Response
data class HealthResponse(
    @SerializedName("status")
    val status: String = "online",

    @SerializedName("service")
    val service: String = "LocalHost AI Android Server",

    @SerializedName("active_model")
    val activeModel: String? = null,

    @SerializedName("backend")
    val backend: String = "Unknown",

    @SerializedName("hardware")
    val hardware: String = "Snapdragon 8 Gen 2 / Adreno 740",

    @SerializedName("memory_used_mb")
    val memoryUsedMb: Long = 0,

    @SerializedName("uptime_sec")
    val uptimeSec: Long = 0
)
