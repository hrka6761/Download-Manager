package ir.hrka.download_manager.core

import java.io.File

/**
 * Sealed class representing the complete state hierarchy of a download.
 *
 * This design provides type-safe state representation with exhaustive when expressions,
 * making it impossible to miss handling a state.
 *
 * **State Transitions:**
 * ```
 * Idle → Validating → Connecting → Downloading → Completed
 *                                              ↓
 *                                            Failed
 *                                              ↓
 *                                          Cancelled
 *                 Downloading ←→ Paused
 * ```
 */
sealed class DownloadState {
    abstract val downloadId: String

    /**
     * Initial state before download starts.
     */
    data class Idle(
        override val downloadId: String
    ) : DownloadState()

    /**
     * Download request is being validated.
     *
     * Checks include URL validity, disk space, network availability, etc.
     */
    data class Validating(
        override val downloadId: String,
        val validationStage: String
    ) : DownloadState()

    /**
     * Establishing connection to the server.
     */
    data class Connecting(
        override val downloadId: String,
        val url: String,
        val attemptNumber: Int = 1
    ) : DownloadState()

    /**
     * Actively downloading the file.
     *
     * @property progress Download progress information
     * @property speed Current download speed metrics
     */
    data class Downloading(
        override val downloadId: String,
        val progress: DownloadProgress,
        val speed: DownloadSpeed
    ) : DownloadState() {

        /**
         * Calculates the percentage of completion.
         *
         * @return Progress percentage (0-100), or -1 if total size is unknown
         */
        fun getProgressPercentage(): Float {
            return if (progress.totalBytes > 0) {
                (progress.downloadedBytes.toFloat() / progress.totalBytes * 100)
            } else {
                -1f
            }
        }

        /**
         * Checks if download is almost complete (>= 99%).
         */
        fun isAlmostComplete(): Boolean {
            return getProgressPercentage() >= 99f
        }
    }

    /**
     * Download has been paused and can be resumed.
     *
     * @property progress Progress at the time of pausing
     * @property pausedAt Timestamp when download was paused
     */
    data class Paused(
        override val downloadId: String,
        val progress: DownloadProgress,
        val pausedAt: Long = System.currentTimeMillis()
    ) : DownloadState() {

        /**
         * Calculates how long the download has been paused.
         *
         * @return Duration in milliseconds
         */
        fun getPausedDuration(): Long {
            return System.currentTimeMillis() - pausedAt
        }
    }

    /**
     * Download completed successfully.
     *
     * @property file The downloaded file
     * @property totalBytes Total file size in bytes
     * @property downloadDuration Time taken to download in milliseconds
     * @property averageSpeed Average download speed in bytes per second
     * @property verified Whether file integrity was verified
     */
    data class Completed(
        override val downloadId: String,
        val file: File,
        val totalBytes: Long,
        val downloadDuration: Long,
        val averageSpeed: Long,
        val verified: Boolean = false
    ) : DownloadState() {

        /**
         * Gets human-readable file size.
         */
        fun getFormattedSize(): String {
            return formatBytes(totalBytes)
        }

        /**
         * Gets human-readable average speed.
         */
        fun getFormattedSpeed(): String {
            return "${formatBytes(averageSpeed)}/s"
        }
    }

    /**
     * Download failed due to an error.
     *
     * @property error The error that caused the failure
     * @property canRetry Whether the download can be retried
     * @property attemptNumber Number of attempts made
     */
    data class Failed(
        override val downloadId: String,
        val error: DownloadError,
        val canRetry: Boolean = true,
        val attemptNumber: Int = 1
    ) : DownloadState() {

        /**
         * Checks if this was the final retry attempt.
         */
        fun isFinalAttempt(maxRetries: Int): Boolean {
            return attemptNumber >= maxRetries
        }
    }

    /**
     * Download was cancelled by user or system.
     *
     * @property partialFile Partially downloaded file, if any
     * @property downloadedBytes Bytes downloaded before cancellation
     */
    data class Cancelled(
        override val downloadId: String,
        val partialFile: File? = null,
        val downloadedBytes: Long = 0
    ) : DownloadState()

    /**
     * Helper function to format bytes to human-readable format.
     */
    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
                else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
            }
        }
    }
}

/**
 * Detailed progress information for an active download.
 */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val startTime: Long = System.currentTimeMillis(),
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    /**
     * Calculates the percentage of completion.
     */
    fun getPercentage(): Float {
        return if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes * 100)
        } else {
            -1f
        }
    }

    /**
     * Calculates elapsed time since download started.
     */
    fun getElapsedTime(): Long {
        return System.currentTimeMillis() - startTime
    }

    /**
     * Checks if total size is known.
     */
    fun isTotalSizeKnown(): Boolean = totalBytes > 0

    /**
     * Gets remaining bytes to download.
     */
    fun getRemainingBytes(): Long = (totalBytes - downloadedBytes).coerceAtLeast(0)
}

/**
 * Speed and time estimation metrics for an active download.
 */
data class DownloadSpeed(
    val currentBytesPerSecond: Long,
    val averageBytesPerSecond: Long,
    val estimatedTimeRemainingMs: Long
) {
    /**
     * Gets human-readable current speed.
     */
    fun getCurrentSpeedFormatted(): String {
        return "${DownloadState.formatBytes(currentBytesPerSecond)}/s"
    }

    /**
     * Gets human-readable average speed.
     */
    fun getAverageSpeedFormatted(): String {
        return "${DownloadState.formatBytes(averageBytesPerSecond)}/s"
    }

    /**
     * Gets human-readable estimated time remaining.
     */
    fun getEstimatedTimeFormatted(): String {
        val seconds = estimatedTimeRemainingMs / 1000
        return when {
            seconds < 60 -> "$seconds sec"
            seconds < 3600 -> "${seconds / 60} min ${seconds % 60} sec"
            else -> "${seconds / 3600} hr ${(seconds % 3600) / 60} min"
        }
    }

    /**
     * Checks if download is stalled (speed near zero).
     */
    fun isStalled(): Boolean = currentBytesPerSecond < 100 // Less than 100 bytes/sec
}

/**
 * Sealed class hierarchy for typed download errors.
 *
 * Provides specific error types for better error handling and user messaging.
 */
sealed class DownloadError {
    abstract val message: String
    abstract val cause: Throwable?

    /**
     * Network-related errors (connection, timeout, DNS, etc.).
     */
    data class NetworkError(
        override val message: String,
        override val cause: Throwable? = null,
        val errorCode: String? = null
    ) : DownloadError()

    /**
     * HTTP protocol errors (4xx, 5xx status codes).
     */
    data class HttpError(
        val statusCode: Int,
        val statusMessage: String,
        override val message: String = "HTTP $statusCode: $statusMessage",
        override val cause: Throwable? = null
    ) : DownloadError()

    /**
     * File system errors (disk full, permission denied, etc.).
     */
    data class FileSystemError(
        override val message: String,
        override val cause: Throwable? = null,
        val errorType: FileSystemErrorType
    ) : DownloadError()

    /**
     * File verification failed (checksum mismatch, size mismatch, etc.).
     */
    data class VerificationError(
        override val message: String,
        override val cause: Throwable? = null,
        val expected: String,
        val actual: String
    ) : DownloadError()

    /**
     * Server doesn't support required features (e.g., range requests for resume).
     */
    data class UnsupportedOperationError(
        override val message: String,
        override val cause: Throwable? = null,
        val operation: String
    ) : DownloadError()

    /**
     * Timeout errors (connect timeout, read timeout).
     */
    data class TimeoutError(
        override val message: String,
        override val cause: Throwable? = null,
        val timeoutType: TimeoutType
    ) : DownloadError()

    /**
     * Authentication or authorization errors.
     */
    data class AuthenticationError(
        override val message: String,
        override val cause: Throwable? = null,
        val statusCode: Int
    ) : DownloadError()

    /**
     * Unknown or uncategorized errors.
     */
    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null
    ) : DownloadError()
}

/**
 * Types of file system errors.
 */
enum class FileSystemErrorType {
    DISK_FULL,
    PERMISSION_DENIED,
    FILE_NOT_FOUND,
    DIRECTORY_NOT_FOUND,
    FILE_ALREADY_EXISTS,
    INVALID_PATH,
    IO_ERROR
}

/**
 * Types of timeout errors.
 */
enum class TimeoutType {
    CONNECT_TIMEOUT,
    READ_TIMEOUT,
    WRITE_TIMEOUT
}

