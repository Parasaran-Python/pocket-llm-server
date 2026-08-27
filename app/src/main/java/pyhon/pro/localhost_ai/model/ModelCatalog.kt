package pyhon.pro.localhost_ai.model

import android.content.Context
import java.io.File

/**
 * Curated catalog of battle-tested GGUF models optimized for mobile Snapdragon 8 Gen 2 / Adreno 740.
 */
object ModelCatalog {

    val presets = listOf(
        ModelInfo(
            id = "qwen2.5-coder-1.5b-instruct-q4_k_m",
            name = "Qwen 2.5 Coder 1.5B Instruct",
            filename = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            sizeBytes = 986_000_000L,
            sizeFormatted = "986 MB",
            ramRequiredFormatted = "~1.4 GB RAM",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            quantType = "Q4_K_M",
            parameterCount = "1.5B",
            description = "High-speed coding specialist. Perfect for coding agents (Cline, Roo Code, Aider, Continue).",
            contextWindow = 8192,
            tags = listOf("Recommended", "Coding", "Fast", "Adreno GPU")
        ),
        ModelInfo(
            id = "qwen2.5-coder-3b-instruct-q4_k_m",
            name = "Qwen 2.5 Coder 3B Instruct",
            filename = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            sizeBytes = 2_100_000_000L,
            sizeFormatted = "2.1 GB",
            ramRequiredFormatted = "~2.8 GB RAM",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            quantType = "Q4_K_M",
            parameterCount = "3B",
            description = "High intelligence coding model with multi-language code reasoning & complex logic.",
            contextWindow = 8192,
            tags = listOf("Coding", "High Intelligence", "Adreno GPU")
        ),
        ModelInfo(
            id = "llama-3.2-1b-instruct-q4_k_m",
            name = "Llama 3.2 1B Instruct",
            filename = "llama-3.2-1b-instruct-q4_k_m.gguf",
            sizeBytes = 805_000_000L,
            sizeFormatted = "805 MB",
            ramRequiredFormatted = "~1.2 GB RAM",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            quantType = "Q4_K_M",
            parameterCount = "1.2B",
            description = "Meta's ultra-light compact instruction model. Lightning fast latency on mobile.",
            contextWindow = 8192,
            tags = listOf("Meta", "Ultra Fast", "General")
        ),
        ModelInfo(
            id = "llama-3.2-3b-instruct-q4_k_m",
            name = "Llama 3.2 3B Instruct",
            filename = "llama-3.2-3b-instruct-q4_k_m.gguf",
            sizeBytes = 2_020_000_000L,
            sizeFormatted = "2.0 GB",
            ramRequiredFormatted = "~2.7 GB RAM",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            quantType = "Q4_K_M",
            parameterCount = "3.2B",
            description = "Meta's flagship compact model for general conversation, analysis, and reasoning.",
            contextWindow = 8192,
            tags = listOf("Meta", "Balanced", "General")
        ),
        ModelInfo(
            id = "deepseek-r1-distill-qwen-1.5b-q4_k_m",
            name = "DeepSeek R1 Distill Qwen 1.5B",
            filename = "deepseek-r1-distill-qwen-1.5b-q4_k_m.gguf",
            sizeBytes = 1_120_000_000L,
            sizeFormatted = "1.1 GB",
            ramRequiredFormatted = "~1.6 GB RAM",
            downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            quantType = "Q4_K_M",
            parameterCount = "1.5B",
            description = "Chain-of-thought reasoning model with full step-by-step thinking traces.",
            contextWindow = 8192,
            tags = listOf("Reasoning", "Thinking", "DeepSeek")
        ),
        ModelInfo(
            id = "smollm2-1.7b-instruct-q4_k_m",
            name = "SmolLM2 1.7B Instruct",
            filename = "smollm2-1.7b-instruct-q4_k_m.gguf",
            sizeBytes = 1_060_000_000L,
            sizeFormatted = "1.06 GB",
            ramRequiredFormatted = "~1.5 GB RAM",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
            quantType = "Q4_K_M",
            parameterCount = "1.7B",
            description = "HuggingFace's specialized lightweight model for edge devices.",
            contextWindow = 4096,
            tags = listOf("HuggingFace", "Edge", "Compact")
        )
    )

    fun getModelsDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getInstalledModels(context: Context): List<ModelInfo> {
        val modelsDir = getModelsDirectory(context)
        val installedList = mutableListOf<ModelInfo>()

        // Check presets against local directory
        val presetFilenames = presets.associateBy { it.filename }

        val files = modelsDir.listFiles { _, name -> name.endsWith(".gguf", ignoreCase = true) } ?: emptyArray()

        for (file in files) {
            val preset = presetFilenames[file.name]
            if (preset != null) {
                installedList.add(
                    preset.copy(
                        localPath = file.absolutePath,
                        isDownloaded = true,
                        sizeBytes = file.length()
                    )
                )
            } else {
                // Custom imported GGUF model
                val sizeMb = file.length() / (1024 * 1024)
                installedList.add(
                    ModelInfo(
                        id = file.nameWithoutExtension.lowercase().replace(" ", "-"),
                        name = file.nameWithoutExtension,
                        filename = file.name,
                        sizeBytes = file.length(),
                        sizeFormatted = "$sizeMb MB",
                        ramRequiredFormatted = "~${sizeMb + 400} MB RAM",
                        localPath = file.absolutePath,
                        isDownloaded = true,
                        isPreset = false,
                        description = "Custom imported GGUF model",
                        tags = listOf("Custom", "Local")
                    )
                )
            }
        }

        return installedList
    }
}
