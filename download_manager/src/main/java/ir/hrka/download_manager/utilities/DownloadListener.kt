package ir.hrka.download_manager.utilities

/**
 * Listener interface for monitoring the progress and result of a file download operation.
 *
 * Implement this interface to receive callbacks for key events during a file download,
 * such as when the download starts, progresses, completes, or fails.
 *
 * This is useful for updating UI elements or handling download logic in a structured way.
 *
 * @see Downloader
 */
interface DownloadListener {

    /**
     * Called when the download process starts.
     *
     * Typically used to trigger UI updates such as showing a progress bar.
     */
    fun onStartDownload()

    /**
     * Called periodically to report download progress.
     *
     * @param fileName Name of the file being downloaded.
     * @param downloadedBytes Number of bytes downloaded so far.
     * @param downloadRate Current download speed in bytes per second.
     * @param remainingTime Estimated time remaining in milliseconds.
     */
    fun onProgressUpdate(
        fileName: String,
        downloadedBytes: Long,
        downloadRate: Float,
        remainingTime: Float
    )

    /**
     * Called when the download successfully completes.
     *
     * This is typically where post-processing or UI updates should happen.
     */
    fun onDownloadCompleted()

    /**
     * Called if the download fails due to an exception.
     *
     * @param e The exception that caused the failure.
     */
    fun onDownloadFailed(e: Exception)
}
