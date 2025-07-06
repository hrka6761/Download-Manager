package ir.hrka.download_manager.listeners

import java.io.File

/**
 * Listener interface for receiving download status updates.
 *
 * Implementations of this interface handle callbacks for download lifecycle events,
 * including start, progress updates, completion, and failure.
 */
internal interface DownloadListener {

    /**
     * Called when the download is started.
     *
     * @param file The [File] where the download will be saved.
     */
    suspend fun onStartDownload(file: File)

    /**
     * Called periodically to report progress during the download.
     *
     * @param file The [File] being downloaded.
     * @param downloadedBytes The number of bytes downloaded so far.
     * @param downloadRate The current download speed in bytes per second.
     * @param remainingTime Estimated remaining time to complete the download in milliseconds.
     */
    suspend fun onProgressUpdate(
        file: File,
        downloadedBytes: Long,
        downloadRate: Float,
        remainingTime: Float
    )

    /**
     * Called when the download completes successfully.
     *
     * @param file The fully downloaded [File].
     */
    suspend fun onDownloadCompleted(file: File)

    /**
     * Called when the download fails due to an exception.
     *
     * @param e The [Exception] that caused the failure.
     */
    suspend fun onDownloadFailed(file: File?, e: Exception)
}
