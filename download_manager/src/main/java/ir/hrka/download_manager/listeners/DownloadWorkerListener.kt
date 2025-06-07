package ir.hrka.download_manager.listeners

/**
 * Listener interface for receiving download status updates from a background worker.
 *
 * Implementations handle download lifecycle events such as enqueueing, progress updates,
 * success, failure, cancellation, and blocking.
 */
interface DownloadWorkerListener {

    /**
     * Called when the download has been enqueued and is waiting to start.
     */
    fun onDownloadEnqueued()

    /**
     * Called periodically to report the progress of the ongoing download.
     *
     * @param receivedBytes The number of bytes downloaded so far.
     * @param downloadRate The current download speed in bytes per second.
     * @param remainingTime Estimated remaining time to complete the download in milliseconds.
     */
    fun onDownloadRunning(receivedBytes: Long, downloadRate: Long, remainingTime: Long)

    /**
     * Called when the download completes successfully.
     *
     * @param filePath The local file path of the downloaded file, or null if unavailable.
     */
    fun onDownloadSuccess(filePath: String?)

    /**
     * Called when the download fails.
     *
     * @param errorMsg An optional error message describing the failure.
     */
    fun onDownloadFailed(errorMsg: String?)

    /**
     * Called when the download is cancelled by the user or system.
     */
    fun onDownloadCancelled()

    /**
     * Called when the download is blocked, for example due to network restrictions or permissions.
     */
    fun onDownloadBlocked()
}
