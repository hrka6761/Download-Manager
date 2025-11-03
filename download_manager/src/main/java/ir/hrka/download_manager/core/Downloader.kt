package ir.hrka.download_manager.core

import kotlinx.coroutines.flow.Flow

/**
 * Core interface for downloading files with reactive state management.
 *
 * This interface is extensible API for file downloads.
 * It uses Kotlin Flow for reactive state updates, supports pause/resume, cancellation,
 * and provides comprehensive download information.
 *
 * @see DownloadRequest for request configuration
 * @see DownloadState for state representation
 * @see DownloadInfo for detailed download information
 */
interface Downloader {

    /**
     * Starts a download and returns a Flow of download states.
     *
     * This method initiates a download operation and emits state updates throughout
     * the download lifecycle. The Flow is cold and will start the download when collected.
     *
     * **State Flow:**
     * Idle → Validating → Connecting → Downloading → Completed/Failed/Cancelled
     *
     * The Flow completes when the download finishes (success, failure, or cancellation).
     *
     * @param request The download request containing URL, destination, and configuration
     * @return A Flow emitting [DownloadState] updates throughout the download lifecycle
     *
     * @throws IllegalArgumentException if request validation fails
     *
     * Example:
     * ```kotlin
     * downloader.download(request).collect { state ->
     *     when (state) {
     *         is DownloadState.Downloading -> updateProgress(state.progress)
     *         is DownloadState.Completed -> showSuccess(state.file)
     *         is DownloadState.Failed -> showError(state.error)
     *     }
     * }
     * ```
     */
    fun download(request: DownloadRequest): Flow<DownloadState>

    /**
     * Retrieves the current state of an active or completed download.
     *
     * This method allows querying the state of a download by its unique ID.
     * Returns null if no download with the given ID exists.
     *
     * @param downloadId The unique identifier of the download
     * @return The current [DownloadState] or null if download doesn't exist
     */
    suspend fun getState(downloadId: String): DownloadState?

    /**
     * Retrieves detailed information about a download.
     *
     * Provides comprehensive information including download metadata, progress metrics,
     * timing information, and file details.
     *
     * @param downloadId The unique identifier of the download
     * @return [DownloadInfo] containing detailed download information, or null if not found
     */
    suspend fun getInfo(downloadId: String): DownloadInfo?

    /**
     * Pauses an active download.
     *
     * The download can be resumed later from the point where it was paused.
     * Not all downloads can be paused (depends on server support for range requests).
     *
     * @param downloadId The unique identifier of the download to pause
     * @return Result indicating success or failure with reason
     *
     * @see resume
     * @see canPause
     */
    suspend fun pause(downloadId: String): Result<Unit>

    /**
     * Resumes a paused download.
     *
     * Continues downloading from where it was paused. Returns a Flow of state updates
     * similar to [download].
     *
     * @param downloadId The unique identifier of the download to resume
     * @return A Flow emitting [DownloadState] updates, or Result.failure if cannot resume
     *
     * @see pause
     * @see canResume
     */
    fun resume(downloadId: String): Flow<DownloadState>

    /**
     * Cancels an active or paused download.
     *
     * Immediately stops the download and cleans up resources. The partially downloaded
     * file may be retained or deleted based on the implementation.
     *
     * @param downloadId The unique identifier of the download to cancel
     * @return Result indicating success or failure
     */
    suspend fun cancel(downloadId: String): Result<Unit>

    /**
     * Cancels all active downloads.
     *
     * Useful for cleanup operations or when stopping the application.
     *
     * @return Result containing the list of cancelled download IDs
     */
    suspend fun cancelAll(): Result<List<String>>

    /**
     * Checks if a download can be paused.
     *
     * Returns true if the download is currently active and the server supports
     * range requests (required for pause/resume functionality).
     *
     * @param downloadId The unique identifier of the download
     * @return true if the download can be paused, false otherwise
     */
    suspend fun canPause(downloadId: String): Boolean

    /**
     * Checks if a download can be resumed.
     *
     * Returns true if there's a paused or partially downloaded file that can be resumed.
     *
     * @param downloadId The unique identifier of the download
     * @return true if the download can be resumed, false otherwise
     */
    suspend fun canResume(downloadId: String): Boolean

    /**
     * Validates a download request without starting the download.
     *
     * Performs checks like URL validity, server availability, file size verification,
     * and disk space availability.
     *
     * @param request The download request to validate
     * @return Result with [DownloadValidation] containing validation results
     */
    suspend fun validate(request: DownloadRequest): Result<DownloadValidation>

    /**
     * Verifies the integrity of a downloaded file.
     *
     * Checks if the downloaded file is complete and matches expected checksums if provided.
     *
     * @param downloadId The unique identifier of the download
     * @return Result indicating if the file is valid
     */
    suspend fun verify(downloadId: String): Result<Boolean>

    /**
     * Retries a failed download.
     *
     * Attempts to restart a failed download with the same configuration.
     *
     * @param downloadId The unique identifier of the failed download
     * @return A Flow emitting [DownloadState] updates for the retry attempt
     */
    fun retry(downloadId: String): Flow<DownloadState>

    /**
     * Gets a list of all active downloads.
     *
     * Returns download IDs for all downloads that are currently in progress.
     *
     * @return List of active download IDs
     */
    suspend fun getActiveDownloads(): List<String>

    /**
     * Clears the history and state of a completed or failed download.
     *
     * @param downloadId The unique identifier of the download to clear
     * @return Result indicating success or failure
     */
    suspend fun clearDownload(downloadId: String): Result<Unit>
}
