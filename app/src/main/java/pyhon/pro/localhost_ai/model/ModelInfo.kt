package pyhon.pro.localhost_ai.model

import java.io.File

/**
 * Model representation for catalog and local storage.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val filename: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val ramRequiredFormatted: String,
    val downloadUrl: String? = null,
    var localPath: String? = null,
    var isDownloaded: Boolean = false,
    val isPreset: Boolean = true,
    val quantType: String = "Q4_K_M",
    val parameterCount: String = "1.5B",
    val description: String = "",
    val contextWindow: Int = 4096,
    val tags: List<String> = emptyList()
) {
    fun existsLocally(): Boolean {
        return localPath != null && File(localPath!!).exists() && File(localPath!!).length() > 0
    }
}
