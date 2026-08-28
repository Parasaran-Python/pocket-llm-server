package pyhon.pro.localhost_ai.engine

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton wrapper around native C++ llama.cpp engine with GPU Vulkan and CPU KleidiAI backends.
 */
object LocalAiEngine {
    private const val TAG = "LocalAiEngine"
    private val gson = Gson()

    private var isInitialized = false

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _activeModelPath = MutableStateFlow<String?>(null)
    val activeModelPath: StateFlow<String?> = _activeModelPath.asStateFlow()

    private val _activeModelName = MutableStateFlow<String?>(null)
    val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress: StateFlow<Int> = _loadingProgress.asStateFlow()

    private val _loadingModelName = MutableStateFlow<String?>(null)
    val loadingModelName: StateFlow<String?> = _loadingModelName.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _activeBackend = MutableStateFlow("CPU (Auto)")
    val activeBackend: StateFlow<String> = _activeBackend.asStateFlow()

    init {
        try {
            System.loadLibrary("localhost_ai_engine")
            Log.i(TAG, "Native localhost_ai_engine library loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library localhost_ai_engine", e)
        }
    }

    /**
     * Initializes the native backend and registers log callbacks.
     */
    fun init(context: Context) {
        if (isInitialized) return
        try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            nativeInit(nativeLibDir)
            isInitialized = true
            Log.i(TAG, "LocalAiEngine initialized with nativeLibDir: $nativeLibDir")

            val backends = getAvailableBackends()
            Log.i(TAG, "Detected available GGML backends: ${backends.joinToString(", ")}")
            val gpuDeviceName = getGpuDeviceName()
            if (backends.any { it.contains("Vulkan", ignoreCase = true) }) {
                _activeBackend.value = if (gpuDeviceName.isNotBlank()) "GPU (Vulkan - $gpuDeviceName)" else "GPU (Vulkan)"
            } else {
                _activeBackend.value = "CPU (ARM NEON / KleidiAI)"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing LocalAiEngine", e)
        }
    }

    /**
     * Returns dynamic GPU device name reported by Vulkan driver.
     */
    fun getGpuDeviceName(): String {
        return try {
            nativeGetGpuDeviceName().trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Returns llama.cpp system info including CPU SIMD flags, OpenMP, Vulkan status, etc.
     */
    fun getSystemInfo(): String {
        return try {
            nativeGetSystemInfo()
        } catch (e: Exception) {
            "System Info unavailable: ${e.message}"
        }
    }

    /**
     * Returns list of available hardware acceleration backends.
     */
    fun getAvailableBackends(): List<String> {
        return try {
            nativeGetAvailableBackends().toList()
        } catch (e: Exception) {
            listOf("CPU (Fallback)")
        }
    }

    /**
     * Loads a GGUF model into memory.
     * @param modelPath Absolute file path to the .gguf model file.
     * @param nGpuLayers Number of layers to offload to GPU (e.g. 99 for full GPU offload, 0 for pure CPU).
     * @param nCtx Context window size in tokens (e.g. 4096).
     * @param nThreads CPU threads (0 for auto-detect).
     * @return Result indicating success or error message.
     */
    suspend fun loadModel(
        modelPath: String,
        nGpuLayers: Int = 99,
        nCtx: Int = 4096,
        nThreads: Int = 0
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(modelPath)
        if (!file.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Model file does not exist: $modelPath"))
        }

        try {
            _isLoading.value = true
            _loadingProgress.value = 0
            _loadingModelName.value = file.nameWithoutExtension

            val callback = object : ModelLoadProgressCallback {
                override fun onProgress(progressPercent: Int) {
                    _loadingProgress.value = progressPercent.coerceIn(0, 100)
                }
            }

            Log.i(TAG, "Loading model: ${file.name} (GPU Layers: $nGpuLayers, Ctx: $nCtx, Threads: $nThreads)")
            val ret = nativeLoadModel(modelPath, nGpuLayers, nCtx, nThreads, callback)
            if (ret == 0) {
                _isLoaded.value = true
                _activeModelPath.value = modelPath
                _activeModelName.value = file.nameWithoutExtension
                _loadingProgress.value = 100

                val gpuDeviceName = getGpuDeviceName()
                if (nGpuLayers > 0 && gpuDeviceName.isNotBlank()) {
                    _activeBackend.value = "GPU (Vulkan - $gpuDeviceName)"
                } else if (nGpuLayers > 0) {
                    _activeBackend.value = "GPU (Vulkan)"
                } else {
                    _activeBackend.value = "CPU (ARM KleidiAI / NEON)"
                }
                Log.i(TAG, "Model loaded successfully: ${file.name}")
                Result.success(Unit)
            } else {
                _isLoaded.value = false
                _activeModelPath.value = null
                _activeModelName.value = null
                val err = "Failed to load model (code: $ret)"
                Log.e(TAG, err)
                Result.failure(RuntimeException(err))
            }
        } catch (e: Exception) {
            _isLoaded.value = false
            _activeModelPath.value = null
            _activeModelName.value = null
            Log.e(TAG, "Exception during model loading", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Unloads the currently active model from memory.
     */
    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        try {
            nativeUnloadModel()
            _isLoaded.value = false
            _activeModelPath.value = null
            _activeModelName.value = null
            Log.i(TAG, "Model unloaded successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
    }

    /**
     * Formats structured chat messages into a model-ready prompt string using embedded Jinja chat templates.
     */
    fun formatChat(
        messages: List<Pair<String, String>>,
        addGenerationPrompt: Boolean = true
    ): String {
        if (!_isLoaded.value) return ""
        val roles = messages.map { it.first }.toTypedArray()
        val contents = messages.map { it.second }.toTypedArray()
        return try {
            nativeFormatChat(roles, contents, addGenerationPrompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting chat template", e)
            // Fallback standard ChatML formatting if native template fails
            buildString {
                for ((role, content) in messages) {
                    append("<|im_start|>$role\n$content<|im_end|>\n")
                }
                if (addGenerationPrompt) {
                    append("<|im_start|>assistant\n")
                }
            }
        }
    }

    /**
     * Cancels the active token generation.
     */
    fun cancelGeneration() {
        try {
            nativeCancelGeneration()
            _isGenerating.value = false
            Log.i(TAG, "Cancelled active generation.")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling generation", e)
        }
    }

    /**
     * Streams text generation tokens using a Kotlin Flow.
     */
    fun generateFlow(
        prompt: String,
        maxTokens: Int = 2048,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        stopWords: List<String> = emptyList()
    ): Flow<String> = callbackFlow {
        _isGenerating.value = true
        var cancelled = false

        val callback = object : TokenCallback {
            override fun onToken(token: String, isFinished: Boolean): Boolean {
                if (token.isNotEmpty()) {
                    trySend(token)
                }
                if (isFinished) {
                    channel.close()
                    _isGenerating.value = false
                    return false
                }
                return !cancelled
            }
        }

        withContext(Dispatchers.IO) {
            try {
                val jsonResult = nativeGenerate(
                    prompt,
                    maxTokens,
                    temperature,
                    topP,
                    stopWords.toTypedArray(),
                    callback
                )
                Log.d(TAG, "Native generation result: $jsonResult")
            } catch (e: Exception) {
                Log.e(TAG, "Error in native generation", e)
                channel.close(e)
            } finally {
                _isGenerating.value = false
            }
        }

        awaitClose {
            cancelled = true
            cancelGeneration()
            _isGenerating.value = false
        }
    }

    /**
     * Synchronous blocking generation with performance stats result.
     */
    suspend fun generateComplete(
        prompt: String,
        maxTokens: Int = 2048,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        stopWords: List<String> = emptyList(),
        onTokenDelta: ((String) -> Unit)? = null
    ): Pair<String, GenerationResult> = withContext(Dispatchers.IO) {
        _isGenerating.value = true
        val fullText = StringBuilder()

        val callback = object : TokenCallback {
            override fun onToken(token: String, isFinished: Boolean): Boolean {
                if (token.isNotEmpty()) {
                    fullText.append(token)
                    onTokenDelta?.invoke(token)
                }
                return true
            }
        }

        val jsonResult = try {
            nativeGenerate(
                prompt,
                maxTokens,
                temperature,
                topP,
                stopWords.toTypedArray(),
                callback
            )
        } catch (e: Exception) {
            Log.e(TAG, "Generation error", e)
            """{"error":"${e.message}"}"""
        } finally {
            _isGenerating.value = false
        }

        val result = try {
            gson.fromJson(jsonResult, GenerationResult::class.java)
        } catch (e: Exception) {
            GenerationResult(error = "JSON parse error: $jsonResult")
        }

        Pair(fullText.toString(), result)
    }

    // Native JNI Declarations
    private external fun nativeInit(nativeLibDir: String?)
    private external fun nativeGetSystemInfo(): String
    private external fun nativeGetAvailableBackends(): Array<String>
    private external fun nativeGetGpuDeviceName(): String
    private external fun nativeLoadModel(
        modelPath: String,
        nGpuLayers: Int,
        nCtx: Int,
        nThreads: Int,
        callback: ModelLoadProgressCallback?
    ): Int
    private external fun nativeUnloadModel()
    private external fun nativeIsModelLoaded(): Boolean
    private external fun nativeGetModelMetadata(): String
    private external fun nativeFormatChat(roles: Array<String>, contents: Array<String>, addGenerationPrompt: Boolean): String
    private external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        stopWords: Array<String>?,
        callback: TokenCallback?
    ): String
    private external fun nativeCancelGeneration()
}

interface ModelLoadProgressCallback {
    fun onProgress(progressPercent: Int)
}
