package ir.hrka.download_manager.listeners

import java.io.File

internal interface DownloadListener {

    suspend fun onStartDownload(file: File)
    suspend fun onProgressUpdate(
        file: File,
        downloadedBytes: Long,
        downloadRate: Float,
        remainingTime: Float
    )
    suspend fun onDownloadCompleted(file: File)
    suspend fun onDownloadFailed(e: Exception)
}