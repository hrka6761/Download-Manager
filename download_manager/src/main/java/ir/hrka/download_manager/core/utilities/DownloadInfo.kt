package ir.hrka.download_manager.core.utilities

import java.io.File

/**
 * Comprehensive information about a download operation.
 *
 * This class provides detailed metadata, progress, timing, and file information
 * for a download. It serves as a complete snapshot of the download state.
 *
 * @property id Unique identifier for the download
 * @property request The original download request
 * @property state Current state of the download
 * @property file The target file (may not exist yet if download not complete)
 * @property metadata Metadata about the download
 * @property progress Current progress information
 * @property timing Timing and performance metrics
 * @property serverInfo Information about the remote server
 * @property history State transition history
 */
data class DownloadInfo(
    val id: String,
    val request: DownloadRequest,
    val state: DownloadState,
    val file: File,
    val metadata: DownloadMetadata,
    val progress: DownloadProgressInfo,
    val timing: DownloadTiming,
    val serverInfo: ServerInfo,
    val history: List<StateTransition> = emptyList()
) {
    /**
     * Checks if download is currently active (downloading or connecting).
     */
    fun isActive(): Boolean {
        return state is DownloadState.Downloading || 
               state is DownloadState.Connecting ||
               state is DownloadState.Validating
    }

    /**
     * Checks if download is in a terminal state (completed, failed, or cancelled).
     */
    fun isTerminal(): Boolean {
        return state is DownloadState.Completed ||
               state is DownloadState.Failed ||
               state is DownloadState.Cancelled
    }

    /**
     * Checks if download can be resumed.
     */
    fun canResume(): Boolean {
        return (state is DownloadState.Paused || state is DownloadState.Failed) &&
               serverInfo.supportsRangeRequests &&
               file.exists()
    }

    /**
     * Checks if download can be retried.
     */
    fun canRetry(): Boolean {
        return state is DownloadState.Failed && state.canRetry
    }
}

/**
 * Metadata about the download.
 */
data class DownloadMetadata(
    val mimeType: String? = null,
    val contentLength: Long = -1,
    val fileName: String? = null,
    val lastModified: Long? = null,
    val etag: String? = null,
    val contentEncoding: String? = null,
    val contentDisposition: String? = null,
    val customMetadata: Map<String, String> = emptyMap()
) {
    /**
     * Checks if content length is known.
     */
    fun hasContentLength(): Boolean = contentLength > 0

    /**
     * Gets human-readable content length.
     */
    fun getFormattedContentLength(): String {
        return if (hasContentLength()) {
            DownloadState.formatBytes(contentLength)
        } else {
            "Unknown"
        }
    }
}

/**
 * Progress information for the download.
 */
data class DownloadProgressInfo(
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val percentage: Float = 0f,
    val currentSpeed: Long = 0,
    val averageSpeed: Long = 0,
    val estimatedTimeRemaining: Long = -1,
    val chunksDownloaded: Int = 0,
    val totalChunks: Int = -1
) {
    /**
     * Checks if download size is known.
     */
    fun isSizeKnown(): Boolean = totalBytes > 0

    /**
     * Gets remaining bytes to download.
     */
    fun getRemainingBytes(): Long {
        return if (isSizeKnown()) {
            (totalBytes - bytesDownloaded).coerceAtLeast(0)
        } else {
            -1
        }
    }

    /**
     * Gets formatted current speed.
     */
    fun getFormattedCurrentSpeed(): String {
        return "${DownloadState.formatBytes(currentSpeed)}/s"
    }

    /**
     * Gets formatted average speed.
     */
    fun getFormattedAverageSpeed(): String {
        return "${DownloadState.formatBytes(averageSpeed)}/s"
    }

    /**
     * Gets formatted estimated time remaining.
     */
    fun getFormattedTimeRemaining(): String {
        if (estimatedTimeRemaining < 0) return "Unknown"
        val seconds = estimatedTimeRemaining / 1000
        return when {
            seconds < 60 -> "$seconds sec"
            seconds < 3600 -> "${seconds / 60} min"
            else -> "${seconds / 3600} hr ${(seconds % 3600) / 60} min"
        }
    }
}

/**
 * Timing and performance metrics.
 */
data class DownloadTiming(
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val pausedDuration: Long = 0,
    val retryCount: Int = 0,
    val lastRetryAt: Long? = null
) {
    /**
     * Calculates total elapsed time including paused time.
     */
    fun getTotalElapsedTime(): Long {
        val endTime = completedAt ?: System.currentTimeMillis()
        val start = startedAt ?: createdAt
        return endTime - start
    }

    /**
     * Calculates active download time (excluding paused duration).
     */
    fun getActiveDownloadTime(): Long {
        return getTotalElapsedTime() - pausedDuration
    }

    /**
     * Checks if download is in progress.
     */
    fun isInProgress(): Boolean {
        return startedAt != null && completedAt == null
    }

    /**
     * Gets formatted total elapsed time.
     */
    fun getFormattedElapsedTime(): String {
        val ms = getTotalElapsedTime()
        val seconds = ms / 1000
        return when {
            seconds < 60 -> "$seconds sec"
            seconds < 3600 -> "${seconds / 60} min ${seconds % 60} sec"
            else -> "${seconds / 3600} hr ${(seconds % 3600) / 60} min"
        }
    }
}

/**
 * Information about the remote server.
 */
data class ServerInfo(
    val serverName: String? = null,
    val serverVersion: String? = null,
    val supportsRangeRequests: Boolean = false,
    val supportsCompression: Boolean = false,
    val maxConnectionsAllowed: Int = 1,
    val requiresAuthentication: Boolean = false,
    val redirectUrl: String? = null,
    val responseHeaders: Map<String, String> = emptyMap()
) {
    /**
     * Checks if resume is supported.
     */
    fun canResume(): Boolean = supportsRangeRequests

    /**
     * Checks if parallel downloads are possible.
     */
    fun supportsParallelDownloads(): Boolean = maxConnectionsAllowed > 1
}

/**
 * Represents a state transition in the download lifecycle.
 */
data class StateTransition(
    val from: DownloadState,
    val to: DownloadState,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String? = null
) {
    /**
     * Gets the duration spent in the previous state.
     */
    fun getStateDuration(previousTransition: StateTransition?): Long {
        return if (previousTransition != null) {
            timestamp - previousTransition.timestamp
        } else {
            0L
        }
    }
}

/**
 * Result of download validation.
 */
data class DownloadValidation(
    val isValid: Boolean,
    val checks: List<ValidationCheck>
) {
    /**
     * Gets all failed validation checks.
     */
    fun getFailedChecks(): List<ValidationCheck> {
        return checks.filter { !it.passed }
    }

    /**
     * Gets all passed validation checks.
     */
    fun getPassedChecks(): List<ValidationCheck> {
        return checks.filter { it.passed }
    }

    /**
     * Checks if a specific validation failed.
     */
    fun hasFailedCheck(checkType: ValidationCheckType): Boolean {
        return checks.any { it.type == checkType && !it.passed }
    }
}

/**
 * Individual validation check result.
 */
data class ValidationCheck(
    val type: ValidationCheckType,
    val passed: Boolean,
    val message: String,
    val severity: ValidationSeverity = ValidationSeverity.ERROR
) {
    /**
     * Checks if this is a blocking error.
     */
    fun isBlocking(): Boolean {
        return !passed && severity == ValidationSeverity.ERROR
    }

    /**
     * Checks if this is just a warning.
     */
    fun isWarning(): Boolean {
        return severity == ValidationSeverity.WARNING
    }
}

/**
 * Types of validation checks.
 */
enum class ValidationCheckType {
    URL_VALIDITY,
    URL_REACHABILITY,
    DISK_SPACE,
    NETWORK_AVAILABILITY,
    WIFI_REQUIREMENT,
    METERED_CONNECTION,
    DESTINATION_WRITABLE,
    FILE_EXISTS,
    AUTHENTICATION,
    SERVER_SUPPORT,
    FILE_SIZE,
    INTERNET_PERMISSION,  // Android INTERNET permission check
    STORAGE_PERMISSION    // Android storage permission check (WRITE_EXTERNAL_STORAGE or scoped storage)
}

/**
 * Severity levels for validation checks.
 */
enum class ValidationSeverity {
    INFO,
    WARNING,
    ERROR
}

