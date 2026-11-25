package com.example.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.CandorModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "LocalModelManager"

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(
        val model: CandorModel,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : ModelDownloadState {
        val progressFraction: Float?
            get() = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }

    data class Error(val message: String) : ModelDownloadState
}

class LocalModelManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var activeCall: okhttp3.Call? = null

    private fun modelsDir(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun modelFile(model: CandorModel): File? {
        val fileName = model.fileName ?: return null
        return File(modelsDir(), fileName)
    }

    fun isModelAvailable(model: CandorModel): Boolean {
        if (!model.isLocalOpenSource) return true
        val file = modelFile(model) ?: return false
        return file.exists() && file.length() >= model.minExpectedBytes
    }

    fun downloadedFileSizeBytes(model: CandorModel): Long {
        val file = modelFile(model) ?: return 0L
        return if (file.exists()) file.length() else 0L
    }

    suspend fun downloadModel(model: CandorModel): Result<File> = withContext(Dispatchers.IO) {
        val url = model.downloadUrl()
            ?: return@withContext Result.failure(IllegalArgumentException("${model.displayName} does not have a download URL."))
        val targetFile = modelFile(model)
            ?: return@withContext Result.failure(IllegalArgumentException("Missing file path for ${model.displayName}."))
        val tempFile = File(modelsDir(), "${targetFile.name}.part")

        try {
            if (isModelAvailable(model)) {
                _downloadState.value = ModelDownloadState.Idle
                return@withContext Result.success(targetFile)
            }

            if (tempFile.exists()) {
                tempFile.delete()
            }

            _downloadState.value = ModelDownloadState.Downloading(
                model = model,
                bytesDownloaded = 0L,
                totalBytes = -1L,
            )

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CandorAndroid/1.0")
                .build()

            val call = httpClient.newCall(request)
            activeCall = call

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed with HTTP ${response.code} ${response.message}")
                }
                val body = response.body ?: throw IOException("Empty response body while downloading model.")
                val contentLength = body.contentLength()
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                var lastEmitTime = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 150L) {
                                lastEmitTime = now
                                _downloadState.value = ModelDownloadState.Downloading(
                                    model = model,
                                    bytesDownloaded = downloaded,
                                    totalBytes = contentLength,
                                )
                            }
                        }
                        output.flush()
                    }
                }

                if (contentLength > 0L && downloaded != contentLength) {
                    throw IOException("Incomplete download ($downloaded of $contentLength bytes received).")
                }
                if (downloaded < model.minExpectedBytes) {
                    throw IOException("Downloaded file size ($downloaded bytes) is smaller than expected for ${model.displayName}.")
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                throw IOException("Failed to finalize model file in app storage.")
            }

            _downloadState.value = ModelDownloadState.Idle
            Result.success(targetFile)
        } catch (e: CancellationException) {
            tempFile.delete()
            _downloadState.value = ModelDownloadState.Idle
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Model download error for ${model.displayName}", e)
            tempFile.delete()
            _downloadState.value = ModelDownloadState.Error(
                e.localizedMessage ?: "Failed to download ${model.displayName}."
            )
            Result.failure(e)
        } finally {
            activeCall = null
        }
    }

    fun cancelDownload() {
        activeCall?.cancel()
        activeCall = null
        _downloadState.value = ModelDownloadState.Idle
    }

    suspend fun importCustomModel(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = modelFile(CandorModel.CUSTOM_LOCAL)
            ?: return@withContext Result.failure(IllegalStateException("Target path unavailable."))
        val tempFile = File(modelsDir(), "${targetFile.name}.part")

        try {
            _downloadState.value = ModelDownloadState.Downloading(
                model = CandorModel.CUSTOM_LOCAL,
                bytesDownloaded = 0L,
                totalBytes = -1L,
            )

            var copied = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                    }
                    output.flush()
                }
            } ?: throw IOException("Unable to open selected file.")

            if (copied < CandorModel.CUSTOM_LOCAL.minExpectedBytes) {
                throw IOException("Selected file ($copied bytes) does not look like a valid .litertlm model.")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                throw IOException("Failed to save imported model file.")
            }

            _downloadState.value = ModelDownloadState.Idle
            Result.success(targetFile)
        } catch (e: CancellationException) {
            tempFile.delete()
            _downloadState.value = ModelDownloadState.Idle
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Custom model import failed", e)
            tempFile.delete()
            _downloadState.value = ModelDownloadState.Error(
                e.localizedMessage ?: "Failed to import .litertlm model file."
            )
            Result.failure(e)
        }
    }

    fun deleteModel(model: CandorModel): Boolean {
        val file = modelFile(model) ?: return false
        return if (file.exists()) file.delete() else false
    }

    fun clearDownloadError() {
        if (_downloadState.value is ModelDownloadState.Error) {
            _downloadState.value = ModelDownloadState.Idle
        }
    }
}
