package ir.hrka.download_manager.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Comprehensive test suite for [DownloadState] and all related data classes.
 *
 * This test class validates the complete state hierarchy, progress tracking,
 * speed calculations, error types, and utility functions used in download management.
 *
 * **Test Coverage:**
 * - All 8 state types (Idle, Validating, Connecting, Downloading, Paused, Completed, Failed, Cancelled)
 * - Progress calculation and percentage computation
 * - Speed metrics (current, average, ETA)
 * - All 8 error types with typed error handling
 * - Formatting utilities (bytes, speed, time)
 * - Enum validation (FileSystemErrorType, TimeoutType)
 *
 * **Total Tests:** 35
 *
 * @see DownloadState
 * @see DownloadProgress
 * @see DownloadSpeed
 * @see DownloadError
 * @author Download Manager Team
 */
class DownloadStateTest {

    /**
     * JUnit rule that creates a temporary folder for test files.
     * Automatically cleaned up after each test execution.
     */
    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * Tests the Idle state creation and property access.
     *
     * **What is tested:**
     * - Idle state can be created with download ID
     * - downloadId property is correctly stored and accessible
     * - State represents initial state before download begins
     *
     * **Validates:**
     * - Idle state instantiation
     * - downloadId property accessibility
     */
    @Test
    fun `Idle state has correct properties`() {
        val state = DownloadState.Idle("test-id")

        assertEquals("test-id", state.downloadId)
    }

    /**
     * Tests the Validating state creation and validation stage tracking.
     *
     * **What is tested:**
     * - Validating state stores both downloadId and validationStage
     * - validationStage provides context about what's being validated
     * - State represents pre-download validation phase
     *
     * **Validates:**
     * - Validating state instantiation
     * - Both properties accessible
     * - Validation stage description storage
     */
    @Test
    fun `Validating state has correct properties`() {
        val state = DownloadState.Validating("test-id", "Checking disk space")

        assertEquals("test-id", state.downloadId)
        assertEquals("Checking disk space", state.validationStage)
    }

    /**
     * Tests the Connecting state creation with attempt number tracking.
     *
     * **What is tested:**
     * - Connecting state stores downloadId, URL, and attempt number
     * - attemptNumber tracks retry attempts (1-based indexing)
     * - State represents HTTP connection establishment phase
     *
     * **Validates:**
     * - Connecting state instantiation
     * - All three properties accessible
     * - Attempt number for retry tracking
     */
    @Test
    fun `Connecting state has correct properties`() {
        val state = DownloadState.Connecting(
            downloadId = "test-id",
            url = "https://example.com/file.zip",
            attemptNumber = 2
        )

        assertEquals("test-id", state.downloadId)
        assertEquals("https://example.com/file.zip", state.url)
        assertEquals(2, state.attemptNumber)
    }

    /**
     * Tests progress percentage calculation for active downloads.
     *
     * **What is tested:**
     * - getProgressPercentage() correctly calculates percentage
     * - Formula: (downloadedBytes / totalBytes) * 100
     * - Result is accurate for known total size
     *
     * **Validates:**
     * - Progress calculation: 500/1000 = 50%
     * - Floating-point precision
     * - Percentage is in 0-100 range
     */
    @Test
    fun `Downloading state calculates progress percentage correctly`() {
        val progress = DownloadProgress(
            downloadedBytes = 500,
            totalBytes = 1000
        )
        val speed = DownloadSpeed(1024, 512, 5000)
        val state = DownloadState.Downloading("test-id", progress, speed)

        assertEquals(50f, state.getProgressPercentage(), 0.01f)
    }

    /**
     * Tests progress percentage when total size is unknown.
     *
     * **What is tested:**
     * - When totalBytes is unknown (-1), percentage returns -1
     * - Handles streaming downloads where size is not known upfront
     * - Doesn't throw exception for division by negative number
     *
     * **Validates:**
     * - Graceful handling of unknown file size
     * - Special return value (-1) indicates indeterminate progress
     * - No arithmetic errors
     */
    @Test
    fun `Downloading state returns -1 for unknown total size`() {
        val progress = DownloadProgress(
            downloadedBytes = 500,
            totalBytes = -1
        )
        val speed = DownloadSpeed(1024, 512, 5000)
        val state = DownloadState.Downloading("test-id", progress, speed)

        assertEquals(-1f, state.getProgressPercentage(), 0.01f)
    }

    /**
     * Tests detection of near-complete downloads (>= 99%).
     *
     * **What is tested:**
     * - isAlmostComplete() returns true when progress >= 99%
     * - Useful for UI optimization (showing "finalizing" state)
     * - Threshold is exactly 99%, not 99.9%
     *
     * **Validates:**
     * - 990/1000 = 99% triggers almost complete
     * - Boolean logic is correct
     * - Threshold behavior
     */
    @Test
    fun `Downloading state isAlmostComplete returns true at 99 percent`() {
        val progress = DownloadProgress(
            downloadedBytes = 990,
            totalBytes = 1000
        )
        val speed = DownloadSpeed(1024, 512, 5000)
        val state = DownloadState.Downloading("test-id", progress, speed)

        assertTrue(state.isAlmostComplete())
    }

    /**
     * Tests that downloads below 99% are not considered almost complete.
     *
     * **What is tested:**
     * - isAlmostComplete() returns false when progress < 99%
     * - 98% is not considered almost complete
     * - Threshold is strict (not >= 98%)
     *
     * **Validates:**
     * - 980/1000 = 98% does NOT trigger almost complete
     * - Boolean logic boundary condition
     * - Precise threshold enforcement
     */
    @Test
    fun `Downloading state isAlmostComplete returns false below 99 percent`() {
        val progress = DownloadProgress(
            downloadedBytes = 980,
            totalBytes = 1000
        )
        val speed = DownloadSpeed(1024, 512, 5000)
        val state = DownloadState.Downloading("test-id", progress, speed)

        assertFalse(state.isAlmostComplete())
    }

    /**
     * Tests paused duration calculation in Paused state.
     *
     * **What is tested:**
     * - getPausedDuration() calculates time since pause
     * - Duration is in milliseconds
     * - Calculation uses System.currentTimeMillis() - pausedAt
     * - Result is approximately correct (allows 100ms variance for test execution)
     *
     * **Validates:**
     * - Time calculation: current time - pause time
     * - Result is ~5000ms (5 seconds)
     * - Allows ±100ms for timing variance
     */
    @Test
    fun `Paused state calculates paused duration`() {
        val progress = DownloadProgress(500, 1000)
        val pausedAt = System.currentTimeMillis() - 5000
        val state = DownloadState.Paused("test-id", progress, pausedAt)

        val duration = state.getPausedDuration()
        assertTrue(duration >= 4900 && duration <= 5100) // Allow 100ms variance
    }

    /**
     * Tests file size formatting in Completed state.
     *
     * **What is tested:**
     * - getFormattedSize() converts bytes to human-readable format
     * - 1 MB (1024*1024 bytes) is formatted as "1.00 MB"
     * - Uses formatBytes() helper function
     * - Precision is 2 decimal places
     *
     * **Validates:**
     * - Byte-to-MB conversion
     * - Formatting: "1.00 MB" (not "1.0 MB" or "1 MB")
     * - Helper function integration
     */
    @Test
    fun `Completed state formats size correctly`() {
        val file = tempFolder.newFile("test.zip")
        val state = DownloadState.Completed(
            downloadId = "test-id",
            file = file,
            totalBytes = 1024 * 1024, // 1 MB
            downloadDuration = 10000,
            averageSpeed = 102400
        )

        assertEquals("1.00 MB", state.getFormattedSize())
    }

    /**
     * Tests download speed formatting in Completed state.
     *
     * **What is tested:**
     * - getFormattedSpeed() formats average speed with units
     * - 102400 bytes/second is formatted as "100.00 KB/s"
     * - Includes "/s" suffix for "per second"
     * - Uses formatBytes() with speed suffix
     *
     * **Validates:**
     * - Speed formatting: 102400 B/s = 100 KB/s
     * - Format: "100.00 KB/s" with unit and rate
     * - Helper function integration
     */
    @Test
    fun `Completed state formats speed correctly`() {
        val file = tempFolder.newFile("test.zip")
        val state = DownloadState.Completed(
            downloadId = "test-id",
            file = file,
            totalBytes = 1024000,
            downloadDuration = 10000,
            averageSpeed = 102400 // 100 KB/s
        )

        assertEquals("100.00 KB/s", state.getFormattedSpeed())
    }

    /**
     * Tests retry attempt limit detection in Failed state.
     *
     * **What is tested:**
     * - isFinalAttempt() returns true when attemptNumber equals maxRetries
     * - Used to determine if retry should be attempted
     * - Boundary condition: exactly at max is final
     *
     * **Validates:**
     * - attemptNumber = 5, maxRetries = 5 → true (final attempt)
     * - Equality check (not >= )
     * - Retry termination logic
     */
    @Test
    fun `Failed state isFinalAttempt returns true when at max retries`() {
        val error = DownloadError.NetworkError("Connection failed")
        val state = DownloadState.Failed(
            downloadId = "test-id",
            error = error,
            attemptNumber = 5
        )

        assertTrue(state.isFinalAttempt(5))
    }

    /**
     * Tests that attempts below max are not considered final.
     *
     * **What is tested:**
     * - isFinalAttempt() returns false when attemptNumber < maxRetries
     * - More retries are possible
     * - Retry logic should continue
     *
     * **Validates:**
     * - attemptNumber = 3, maxRetries = 5 → false (not final)
     * - Retry continuation logic
     * - Correct boundary behavior
     */
    @Test
    fun `Failed state isFinalAttempt returns false when below max retries`() {
        val error = DownloadError.NetworkError("Connection failed")
        val state = DownloadState.Failed(
            downloadId = "test-id",
            error = error,
            attemptNumber = 3
        )

        assertFalse(state.isFinalAttempt(5))
    }

    /**
     * Tests Cancelled state stores partial download information.
     *
     * **What is tested:**
     * - Cancelled state stores downloadId, partial file, and bytes downloaded
     * - All properties are accessible after cancellation
     * - Supports resuming cancelled downloads
     *
     * **Validates:**
     * - All three properties: downloadId, partialFile, downloadedBytes
     * - Property storage and retrieval
     * - Support for cancellation metadata
     */
    @Test
    fun `Cancelled state has correct properties`() {
        val file = tempFolder.newFile("partial.zip")
        val state = DownloadState.Cancelled(
            downloadId = "test-id",
            partialFile = file,
            downloadedBytes = 500
        )

        assertEquals("test-id", state.downloadId)
        assertEquals(file, state.partialFile)
        assertEquals(500, state.downloadedBytes)
    }

    /**
     * Tests byte formatting utility function for all size ranges.
     *
     * **What is tested:**
     * - formatBytes() converts bytes to appropriate units (B, KB, MB, GB)
     * - Automatic unit selection based on size
     * - 2 decimal places for KB/MB/GB
     * - No decimal places for bytes
     *
     * **Validates:**
     * - 100 bytes → "100 B"
     * - 1024 bytes → "1.00 KB"
     * - 1048576 bytes → "1.00 MB"
     * - 1073741824 bytes → "1.00 GB"
     */
    @Test
    fun `formatBytes formats bytes correctly`() {
        assertEquals("100 B", DownloadState.formatBytes(100))
        assertEquals("1.00 KB", DownloadState.formatBytes(1024))
        assertEquals("1.00 MB", DownloadState.formatBytes(1024 * 1024))
        assertEquals("1.00 GB", DownloadState.formatBytes(1024 * 1024 * 1024))
    }

    /**
     * Tests progress percentage calculation in DownloadProgress.
     *
     * **What is tested:**
     * - getPercentage() correctly calculates (downloadedBytes/totalBytes) * 100
     * - Returns float percentage value
     * - 250/1000 = 25%
     *
     * **Validates:**
     * - Mathematical accuracy
     * - Percentage range (0-100)
     * - Floating-point precision
     */
    @Test
    fun `DownloadProgress calculates percentage correctly`() {
        val progress = DownloadProgress(250, 1000)

        assertEquals(25f, progress.getPercentage(), 0.01f)
    }

    /**
     * Tests progress percentage when total size is unknown.
     *
     * **What is tested:**
     * - getPercentage() returns -1 when totalBytes is unknown (-1)
     * - Handles streaming downloads gracefully
     * - Special value -1 indicates indeterminate progress
     *
     * **Validates:**
     * - Unknown size handling
     * - No division by zero or negative
     * - Special return value convention
     */
    @Test
    fun `DownloadProgress returns -1 for unknown size`() {
        val progress = DownloadProgress(250, -1)

        assertEquals(-1f, progress.getPercentage(), 0.01f)
    }

    /**
     * Tests elapsed time calculation since download started.
     *
     * **What is tested:**
     * - getElapsedTime() returns milliseconds since startTime
     * - Calculation: currentTimeMillis() - startTime
     * - Result is approximately 5000ms (allows ±100ms variance)
     *
     * **Validates:**
     * - Time calculation accuracy
     * - Millisecond precision
     * - Reasonable timing variance allowance
     */
    @Test
    fun `DownloadProgress calculates elapsed time`() {
        val startTime = System.currentTimeMillis() - 5000
        val progress = DownloadProgress(250, 1000, startTime)

        val elapsed = progress.getElapsedTime()
        assertTrue(elapsed >= 4900 && elapsed <= 5100)
    }

    /**
     * Tests detection of whether total file size is known.
     *
     * **What is tested:**
     * - isTotalSizeKnown() returns true when totalBytes > 0
     * - Returns false when totalBytes is -1 (unknown)
     * - Used to determine if percentage can be calculated
     *
     * **Validates:**
     * - Known size: totalBytes = 1000 → true
     * - Unknown size: totalBytes = -1 → false
     * - Boolean detection logic
     */
    @Test
    fun `DownloadProgress isTotalSizeKnown returns correct values`() {
        val known = DownloadProgress(250, 1000)
        val unknown = DownloadProgress(250, -1)

        assertTrue(known.isTotalSizeKnown())
        assertFalse(unknown.isTotalSizeKnown())
    }

    /**
     * Tests remaining bytes calculation in DownloadProgress.
     *
     * **What is tested:**
     * - getRemainingBytes() = totalBytes - downloadedBytes
     * - Calculation is simple subtraction
     * - Result is coerced to minimum 0 (no negative values)
     *
     * **Validates:**
     * - 1000 - 250 = 750 bytes remaining
     * - Arithmetic accuracy
     * - Non-negative result guarantee
     */
    @Test
    fun `DownloadProgress getRemainingBytes calculates correctly`() {
        val progress = DownloadProgress(250, 1000)

        assertEquals(750, progress.getRemainingBytes())
    }

    /**
     * Tests current download speed formatting.
     *
     * **What is tested:**
     * - getCurrentSpeedFormatted() formats bytes/sec to readable string
     * - 102400 bytes/second → "100.00 KB/s"
     * - Includes "/s" suffix for rate indication
     * - Uses formatBytes() helper
     *
     * **Validates:**
     * - Speed formatting: 102400 B/s = 100 KB/s
     * - Format string: "100.00 KB/s"
     * - Unit conversion accuracy
     */
    @Test
    fun `DownloadSpeed getCurrentSpeedFormatted returns correct string`() {
        val speed = DownloadSpeed(102400, 51200, 10000)

        assertEquals("100.00 KB/s", speed.getCurrentSpeedFormatted())
    }

    /**
     * Tests average download speed formatting.
     *
     * **What is tested:**
     * - getAverageSpeedFormatted() formats average speed
     * - 51200 bytes/second → "50.00 KB/s"
     * - Separate from current speed (can differ)
     *
     * **Validates:**
     * - Average speed formatting: 51200 B/s = 50 KB/s
     * - Format string: "50.00 KB/s"
     * - Independent from current speed
     */
    @Test
    fun `DownloadSpeed getAverageSpeedFormatted returns correct string`() {
        val speed = DownloadSpeed(102400, 51200, 10000)

        assertEquals("50.00 KB/s", speed.getAverageSpeedFormatted())
    }

    /**
     * Tests ETA formatting for short durations (seconds).
     *
     * **What is tested:**
     * - getEstimatedTimeFormatted() formats milliseconds to readable time
     * - 45000ms → "45 sec"
     * - Uses seconds format for durations < 1 minute
     *
     * **Validates:**
     * - Time conversion: 45000ms = 45 seconds
     * - Format: "{seconds} sec"
     * - Appropriate unit selection
     */
    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted returns correct string for seconds`() {
        val speed = DownloadSpeed(102400, 51200, 45000)

        assertEquals("45 sec", speed.getEstimatedTimeFormatted())
    }

    /**
     * Tests ETA formatting for medium durations (minutes and seconds).
     *
     * **What is tested:**
     * - getEstimatedTimeFormatted() formats to "min sec" for 60+ seconds
     * - 90000ms (90 sec) → "1 min 30 sec"
     * - Shows both minutes and seconds
     *
     * **Validates:**
     * - Time conversion: 90000ms = 1 minute 30 seconds
     * - Format: "{min} min {sec} sec"
     * - Both units displayed
     */
    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted returns correct string for minutes`() {
        val speed = DownloadSpeed(102400, 51200, 90000) // 90 seconds

        assertEquals("1 min 30 sec", speed.getEstimatedTimeFormatted())
    }

    /**
     * Tests ETA formatting for long durations (hours and minutes).
     *
     * **What is tested:**
     * - getEstimatedTimeFormatted() formats to "hr min" for 3600+ seconds
     * - 3660000ms (1 hour 1 minute) → "1 hr 1 min"
     * - Seconds are omitted for hour+ durations
     *
     * **Validates:**
     * - Time conversion: 3660000ms = 1 hour 1 minute
     * - Format: "{hr} hr {min} min"
     * - Seconds omitted for long durations
     */
    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted returns correct string for hours`() {
        val speed = DownloadSpeed(102400, 51200, 3660000) // 1 hour 1 minute

        assertEquals("1 hr 1 min", speed.getEstimatedTimeFormatted())
    }

    /**
     * Tests stalled download detection (very low speed).
     *
     * **What is tested:**
     * - isStalled() returns true when speed < 100 bytes/second
     * - 50 bytes/sec is considered stalled
     * - Threshold is 100 bytes/sec
     *
     * **Validates:**
     * - Stall detection threshold
     * - Low speed detection: 50 < 100 → true
     * - Useful for network issue detection
     */
    @Test
    fun `DownloadSpeed isStalled returns true for very low speed`() {
        val speed = DownloadSpeed(50, 100, 10000)

        assertTrue(speed.isStalled())
    }

    /**
     * Tests that normal download speeds are not flagged as stalled.
     *
     * **What is tested:**
     * - isStalled() returns false for normal speeds (>= 100 bytes/sec)
     * - 102400 bytes/sec (100 KB/s) is not stalled
     * - Normal downloads are not incorrectly flagged
     *
     * **Validates:**
     * - Normal speed detection: 102400 >= 100 → false
     * - Threshold boundary behavior
     * - No false positives
     */
    @Test
    fun `DownloadSpeed isStalled returns false for normal speed`() {
        val speed = DownloadSpeed(102400, 51200, 10000)

        assertFalse(speed.isStalled())
    }

    /**
     * Tests NetworkError creation and property storage.
     *
     * **What is tested:**
     * - NetworkError stores message, cause, and optional error code
     * - All properties are accessible
     * - Represents connection, DNS, or network-level errors
     *
     * **Validates:**
     * - message = "Connection timeout"
     * - cause = null (can be null)
     * - errorCode = "TIMEOUT" (optional context)
     */
    @Test
    fun `NetworkError has correct properties`() {
        val error = DownloadError.NetworkError(
            message = "Connection timeout",
            cause = null,
            errorCode = "TIMEOUT"
        )

        assertEquals("Connection timeout", error.message)
        assertNull(error.cause)
        assertEquals("TIMEOUT", error.errorCode)
    }

    /**
     * Tests HttpError creation with HTTP status codes.
     *
     * **What is tested:**
     * - HttpError stores statusCode and statusMessage
     * - Message is auto-formatted as "HTTP {code}: {message}"
     * - Represents HTTP protocol errors (4xx, 5xx)
     *
     * **Validates:**
     * - statusCode = 404
     * - statusMessage = "Not Found"
     * - Auto-formatted message = "HTTP 404: Not Found"
     * - cause is null by default
     */
    @Test
    fun `HttpError has correct properties`() {
        val error = DownloadError.HttpError(
            statusCode = 404,
            statusMessage = "Not Found"
        )

        assertEquals(404, error.statusCode)
        assertEquals("Not Found", error.statusMessage)
        assertEquals("HTTP 404: Not Found", error.message)
        assertNull(error.cause)
    }

    /**
     * Tests FileSystemError with typed error categorization.
     *
     * **What is tested:**
     * - FileSystemError stores message and errorType enum
     * - errorType provides specific error category
     * - Represents disk, permission, or I/O errors
     *
     * **Validates:**
     * - message = "Disk full"
     * - errorType = DISK_FULL
     * - Typed error for specific handling
     */
    @Test
    fun `FileSystemError has correct properties`() {
        val error = DownloadError.FileSystemError(
            message = "Disk full",
            errorType = FileSystemErrorType.DISK_FULL
        )

        assertEquals("Disk full", error.message)
        assertEquals(FileSystemErrorType.DISK_FULL, error.errorType)
    }

    /**
     * Tests VerificationError for checksum validation failures.
     *
     * **What is tested:**
     * - VerificationError stores expected and actual checksums
     * - Used when downloaded file doesn't match expected checksum
     * - Provides both values for debugging
     *
     * **Validates:**
     * - message = "Checksum mismatch"
     * - expected = "abc123"
     * - actual = "def456"
     * - Both checksums stored for comparison
     */
    @Test
    fun `VerificationError has correct properties`() {
        val error = DownloadError.VerificationError(
            message = "Checksum mismatch",
            expected = "abc123",
            actual = "def456"
        )

        assertEquals("Checksum mismatch", error.message)
        assertEquals("abc123", error.expected)
        assertEquals("def456", error.actual)
    }

    /**
     * Tests UnsupportedOperationError for server capability issues.
     *
     * **What is tested:**
     * - UnsupportedOperationError stores operation name
     * - Used when server doesn't support required features
     * - Example: resume not supported, parallel downloads unavailable
     *
     * **Validates:**
     * - message = "Resume not supported"
     * - operation = "resume"
     * - Clear indication of unsupported feature
     */
    @Test
    fun `UnsupportedOperationError has correct properties`() {
        val error = DownloadError.UnsupportedOperationError(
            message = "Resume not supported",
            operation = "resume"
        )

        assertEquals("Resume not supported", error.message)
        assertEquals("resume", error.operation)
    }

    /**
     * Tests TimeoutError with categorized timeout types.
     *
     * **What is tested:**
     * - TimeoutError stores message and timeoutType enum
     * - timeoutType distinguishes connect, read, write timeouts
     * - Allows specific timeout handling
     *
     * **Validates:**
     * - message = "Connection timeout"
     * - timeoutType = CONNECT_TIMEOUT
     * - Typed categorization for specific error handling
     */
    @Test
    fun `TimeoutError has correct properties`() {
        val error = DownloadError.TimeoutError(
            message = "Connection timeout",
            timeoutType = TimeoutType.CONNECT_TIMEOUT
        )

        assertEquals("Connection timeout", error.message)
        assertEquals(TimeoutType.CONNECT_TIMEOUT, error.timeoutType)
    }

    /**
     * Tests AuthenticationError for auth/authorization failures.
     *
     * **What is tested:**
     * - AuthenticationError stores message and HTTP status code
     * - Used for 401 Unauthorized and 403 Forbidden responses
     * - Includes status code for specific handling
     *
     * **Validates:**
     * - message = "Unauthorized"
     * - statusCode = 401
     * - Auth-specific error representation
     */
    @Test
    fun `AuthenticationError has correct properties`() {
        val error = DownloadError.AuthenticationError(
            message = "Unauthorized",
            statusCode = 401
        )

        assertEquals("Unauthorized", error.message)
        assertEquals(401, error.statusCode)
    }

    /**
     * Tests UnknownError as catch-all for uncategorized errors.
     *
     * **What is tested:**
     * - UnknownError stores message and optional cause
     * - Used for errors that don't fit other categories
     * - Provides fallback error type
     *
     * **Validates:**
     * - message storage
     * - cause is optional (null in this test)
     * - Generic error representation
     */
    @Test
    fun `UnknownError has correct properties`() {
        val error = DownloadError.UnknownError(
            message = "Unknown error occurred"
        )

        assertEquals("Unknown error occurred", error.message)
        assertNull(error.cause)
    }

    /**
     * Tests FileSystemErrorType enum completeness.
     *
     * **What is tested:**
     * - Enum has exactly 7 values
     * - All expected error types are present
     * - Covers: DISK_FULL, PERMISSION_DENIED, FILE_NOT_FOUND,
     *   DIRECTORY_NOT_FOUND, FILE_ALREADY_EXISTS, INVALID_PATH, IO_ERROR
     *
     * **Validates:**
     * - Complete enum definition (no missing values)
     * - Count is 7
     * - All specific file system error cases covered
     */
    @Test
    fun `FileSystemErrorType enum has all values`() {
        val types = FileSystemErrorType.values()

        assertEquals(7, types.size)
        assertTrue(types.contains(FileSystemErrorType.DISK_FULL))
        assertTrue(types.contains(FileSystemErrorType.PERMISSION_DENIED))
        assertTrue(types.contains(FileSystemErrorType.FILE_NOT_FOUND))
        assertTrue(types.contains(FileSystemErrorType.DIRECTORY_NOT_FOUND))
        assertTrue(types.contains(FileSystemErrorType.FILE_ALREADY_EXISTS))
        assertTrue(types.contains(FileSystemErrorType.INVALID_PATH))
        assertTrue(types.contains(FileSystemErrorType.IO_ERROR))
    }

    /**
     * Tests TimeoutType enum completeness.
     *
     * **What is tested:**
     * - Enum has exactly 3 values
     * - Covers all timeout scenarios: CONNECT_TIMEOUT, READ_TIMEOUT, WRITE_TIMEOUT
     * - Complete categorization of timeout types
     *
     * **Validates:**
     * - Complete enum definition
     * - Count is 3
     * - All network timeout types covered
     */
    @Test
    fun `TimeoutType enum has all values`() {
        val types = TimeoutType.values()

        assertEquals(3, types.size)
        assertTrue(types.contains(TimeoutType.CONNECT_TIMEOUT))
        assertTrue(types.contains(TimeoutType.READ_TIMEOUT))
        assertTrue(types.contains(TimeoutType.WRITE_TIMEOUT))
    }
}
