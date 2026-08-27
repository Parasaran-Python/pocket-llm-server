package pyhon.pro.localhost_ai.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val modelId: String,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedMbPerSec: Double
    ) : DownloadState()
    data class Completed(val modelId: String, val file: File) : DownloadState()
    data class Failed(val modelId: String, val error: String) : DownloadState()
}

/**
 * High-performance file downloader with progress tracking, speed calculation, and resume capabilities.
 */
object ModelDownloader {
    private const val TAG = "ModelDownloader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var isCancelled = false

    fun cancelDownload() {
        isCancelled = true
        _downloadState.value = DownloadState.Idle
    }

    suspend fun downloadModel(
        context: Context,
        modelInfo: ModelInfo
    ): Result<File> = withContext(Dispatchers.IO) {
        val url = modelInfo.downloadUrl
            ?: return@withContext Result.failure(IllegalArgumentException("Model has no download URL"))

        val targetDir = ModelCatalog.getModelsDirectory(context)
        val targetFile = File(targetDir, modelInfo.filename)
        val tempFile = File(targetDir, "${modelInfo.filename}.download")

        isCancelled = false
        Log.i(TAG, "Starting download for ${modelInfo.name} from $url to ${targetFile.absolutePath}")

        var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L

        try {
            val requestBuilder = Request.Builder().url(url)
            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
                Log.i(TAG, "Resuming download from $downloadedBytes bytes")
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful && response.code != 206) {
                // If range request fails, restart from 0
                if (downloadedBytes > 0) {
                    tempFile.delete()
                    downloadedBytes = 0L
                    return@withContext downloadModel(context, modelInfo)
                }
                val err = "Download failed with HTTP ${response.code}: ${response.message}"
                _downloadState.value = DownloadState.Failed(modelInfo.id, err)
                return@withContext Result.failure(RuntimeException(err))
            }

            val body = response.body
                ?: return@withContext Result.failure(RuntimeException("Response body is null"))

            val contentLength = body.contentLength()
            val totalBytes = if (response.code == 206) {
                downloadedBytes + contentLength
            } else {
                contentLength
            }

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(tempFile, downloadedBytes > 0)

            val buffer = ByteArray(64 * 1024) // 64KB buffer
            var bytesRead: Int
            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isCancelled) {
                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    _downloadState.value = DownloadState.Idle
                    return@withContext Result.failure(RuntimeException("Download cancelled by user"))
                }

                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                bytesSinceLastUpdate += bytesRead

                val now = System.currentTimeMillis()
                val timeDiff = now - lastUpdateTime
                if (timeDiff >= 500) { // update UI every 500ms
                    val speedMbPerSec = (bytesSinceLastUpdate.toDouble() / (1024.0 * 1024.0)) / (timeDiff.toDouble() / 1000.0)
                    val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0

                    _downloadState.value = DownloadState.Downloading(
                        modelId = modelInfo.id,
                        progressPercent = percent,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        speedMbPerSec = speedMbPerSec
                    )

                    lastUpdateTime = now
                    bytesSinceLastUpdate = 0L
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            if (tempFile.renameTo(targetFile)) {
                Log.i(TAG, "Download completed and file renamed to: ${targetFile.absolutePath}")
                _downloadState.value = DownloadState.Completed(modelInfo.id, targetFile)
                Result.success(targetFile)
            } else {
                val err = "Failed to rename downloaded temp file"
                _downloadState.value = DownloadState.Failed(modelInfo.id, err)
                Result.failure(RuntimeException(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            _downloadState.value = DownloadState.Failed(modelInfo.id, e.message ?: "Unknown download error")
            Result.failure(e)
        }
    }
}
