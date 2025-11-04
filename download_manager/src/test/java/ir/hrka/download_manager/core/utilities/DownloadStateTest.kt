package ir.hrka.download_manager.core.utilities

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
 * **Total Tests:** 67
 *
 * @see DownloadState
 * @see DownloadProgress
 * @see DownloadSpeed
 * @see DownloadError
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
        val types = FileSystemErrorType.entries

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
        val types = TimeoutType.entries

        assertEquals(3, types.size)
        assertTrue(types.contains(TimeoutType.CONNECT_TIMEOUT))
        assertTrue(types.contains(TimeoutType.READ_TIMEOUT))
        assertTrue(types.contains(TimeoutType.WRITE_TIMEOUT))
    }

    // ==================== Edge Cases & Boundary Tests ====================

    /**
     * Tests formatBytes() handles zero correctly.
     */
    @Test
    fun `formatBytes handles zero bytes`() {
        assertEquals("0 B", DownloadState.formatBytes(0))
    }

    /**
     * Tests formatBytes() handles 1 byte.
     */
    @Test
    fun `formatBytes handles single byte`() {
        assertEquals("1 B", DownloadState.formatBytes(1))
    }

    /**
     * Tests formatBytes() handles boundary at 1023 bytes (just before KB).
     */
    @Test
    fun `formatBytes handles 1023 bytes boundary`() {
        assertEquals("1023 B", DownloadState.formatBytes(1023))
    }

    /**
     * Tests formatBytes() handles boundary at 1024 bytes (exactly 1 KB).
     */
    @Test
    fun `formatBytes handles exactly 1 KB`() {
        assertEquals("1.00 KB", DownloadState.formatBytes(1024))
    }

    /**
     * Tests formatBytes() handles KB to MB boundary.
     */
    @Test
    fun `formatBytes handles KB to MB boundary`() {
        assertEquals("1023.00 KB", DownloadState.formatBytes(1023 * 1024))
        assertEquals("1.00 MB", DownloadState.formatBytes(1024 * 1024))
    }

    /**
     * Tests formatBytes() handles MB to GB boundary.
     */
    @Test
    fun `formatBytes handles MB to GB boundary`() {
        assertEquals("1023.00 MB", DownloadState.formatBytes(1023 * 1024 * 1024))
        assertEquals("1.00 GB", DownloadState.formatBytes(1024L * 1024 * 1024))
    }

    /**
     * Tests formatBytes() handles very large values.
     */
    @Test
    fun `formatBytes handles very large file sizes`() {
        val tenGB = 10L * 1024 * 1024 * 1024
        assertEquals("10.00 GB", DownloadState.formatBytes(tenGB))
        
        val hundredGB = 100L * 1024 * 1024 * 1024
        assertEquals("100.00 GB", DownloadState.formatBytes(hundredGB))
    }

    /**
     * Tests progress percentage with zero downloaded (0%).
     */
    @Test
    fun `DownloadProgress calculates 0 percent correctly`() {
        val progress = DownloadProgress(0, 1000)
        assertEquals(0f, progress.getPercentage(), 0.01f)
    }

    /**
     * Tests progress percentage with 100% complete.
     */
    @Test
    fun `DownloadProgress calculates 100 percent correctly`() {
        val progress = DownloadProgress(1000, 1000)
        assertEquals(100f, progress.getPercentage(), 0.01f)
    }

    /**
     * Tests progress percentage with downloaded exceeding total (edge case).
     */
    @Test
    fun `DownloadProgress handles downloaded exceeding total`() {
        val progress = DownloadProgress(1500, 1000)
        // Should return percentage > 100
        assertEquals(150f, progress.getPercentage(), 0.01f)
    }

    /**
     * Tests remaining bytes when downloaded equals total.
     */
    @Test
    fun `DownloadProgress getRemainingBytes returns 0 when complete`() {
        val progress = DownloadProgress(1000, 1000)
        assertEquals(0, progress.getRemainingBytes())
    }

    /**
     * Tests remaining bytes when downloaded exceeds total.
     */
    @Test
    fun `DownloadProgress getRemainingBytes coerces to 0 when downloaded exceeds total`() {
        val progress = DownloadProgress(1500, 1000)
        assertEquals(0, progress.getRemainingBytes())
    }

    /**
     * Tests elapsed time with very short duration.
     */
    @Test
    fun `DownloadProgress calculates elapsed time for short duration`() {
        val startTime = System.currentTimeMillis() - 100
        val progress = DownloadProgress(100, 1000, startTime)
        
        val elapsed = progress.getElapsedTime()
        assertTrue(elapsed >= 90 && elapsed <= 110)
    }

    /**
     * Tests elapsed time immediately after start.
     */
    @Test
    fun `DownloadProgress elapsed time is near zero immediately after start`() {
        val progress = DownloadProgress(0, 1000) // Uses current time as default
        val elapsed = progress.getElapsedTime()
        assertTrue(elapsed < 50) // Should be very small
    }

    /**
     * Tests DownloadSpeed with zero speed values.
     */
    @Test
    fun `DownloadSpeed formats zero speed correctly`() {
        val speed = DownloadSpeed(0, 0, -1)
        
        assertEquals("0 B/s", speed.getCurrentSpeedFormatted())
        assertEquals("0 B/s", speed.getAverageSpeedFormatted())
    }

    /**
     * Tests DownloadSpeed with very high speed.
     */
    @Test
    fun `DownloadSpeed formats very high speed correctly`() {
        val hundredMBps = 100L * 1024 * 1024 // 100 MB/s
        val speed = DownloadSpeed(hundredMBps, hundredMBps, 1000)
        
        assertEquals("100.00 MB/s", speed.getCurrentSpeedFormatted())
    }

    /**
     * Tests DownloadSpeed ETA formatting with zero time.
     */
    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted handles 0 seconds`() {
        val speed = DownloadSpeed(1024, 512, 0)
        assertEquals("0 sec", speed.getEstimatedTimeFormatted())
    }

    /**
     * Tests DownloadSpeed ETA formatting at 59 seconds boundary.
     */
    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted at 59 seconds boundary`() {
        val speed = DownloadSpeed(1024, 512, 59000)
        assertEquals("59 sec", speed.getEstimatedTimeFormatted())
    }

    /**
     * Tests DownloadSpeed ETA formatting at 60 seconds boundary (1 minute).
     */
    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted at 60 seconds boundary`() {
        val speed = DownloadSpeed(1024, 512, 60000)
        assertEquals("1 min 0 sec", speed.getEstimatedTimeFormatted())
    }

    /**
     * Tests DownloadSpeed ETA formatting at 3600 seconds boundary (1 hour).
     */
    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted at 3600 seconds boundary`() {
        val speed = DownloadSpeed(1024, 512, 3600000)
        assertEquals("1 hr 0 min", speed.getEstimatedTimeFormatted())
    }

    /**
     * Tests isStalled() at exact threshold (100 bytes/sec).
     */
    @Test
    fun `DownloadSpeed isStalled at exact threshold boundary`() {
        val atThreshold = DownloadSpeed(100, 100, 1000)
        assertFalse(atThreshold.isStalled()) // >= 100 is not stalled
        
        val belowThreshold = DownloadSpeed(99, 100, 1000)
        assertTrue(belowThreshold.isStalled()) // < 100 is stalled
    }

    /**
     * Tests isStalled() with zero speed.
     */
    @Test
    fun `DownloadSpeed isStalled returns true for zero speed`() {
        val speed = DownloadSpeed(0, 0, -1)
        assertTrue(speed.isStalled())
    }

    /**
     * Tests Downloading state with zero progress.
     */
    @Test
    fun `Downloading state handles zero progress`() {
        val progress = DownloadProgress(0, 1000)
        val speed = DownloadSpeed(0, 0, -1)
        val state = DownloadState.Downloading("test-id", progress, speed)
        
        assertEquals(0f, state.getProgressPercentage(), 0.01f)
        assertFalse(state.isAlmostComplete())
    }

    /**
     * Tests Downloading state at 100% progress.
     */
    @Test
    fun `Downloading state handles 100 percent progress`() {
        val progress = DownloadProgress(1000, 1000)
        val speed = DownloadSpeed(1024, 512, 0)
        val state = DownloadState.Downloading("test-id", progress, speed)
        
        assertEquals(100f, state.getProgressPercentage(), 0.01f)
        assertTrue(state.isAlmostComplete())
    }

    /**
     * Tests isAlmostComplete() at exact 99.0% boundary.
     */
    @Test
    fun `Downloading state isAlmostComplete at exact 99 percent boundary`() {
        // Exactly 99%
        val exactProgress = DownloadProgress(99, 100)
        val speed = DownloadSpeed(1024, 512, 100)
        val exactState = DownloadState.Downloading("test-id", exactProgress, speed)
        assertTrue(exactState.isAlmostComplete())
        
        // Just below 99%
        val belowProgress = DownloadProgress(98, 100)
        val belowState = DownloadState.Downloading("test-id", belowProgress, speed)
        assertFalse(belowState.isAlmostComplete())
    }

    /**
     * Tests Paused state with immediate pause (pausedAt = now).
     */
    @Test
    fun `Paused state with immediate pause has near-zero duration`() {
        val progress = DownloadProgress(500, 1000)
        val state = DownloadState.Paused("test-id", progress) // Uses current time
        
        val duration = state.getPausedDuration()
        assertTrue(duration < 50) // Should be very small
    }

    /**
     * Tests Failed state isFinalAttempt() when attempt exceeds max.
     */
    @Test
    fun `Failed state isFinalAttempt returns true when exceeding max retries`() {
        val error = DownloadError.NetworkError("Connection failed")
        val state = DownloadState.Failed(
            downloadId = "test-id",
            error = error,
            attemptNumber = 10
        )
        
        assertTrue(state.isFinalAttempt(5)) // 10 >= 5
    }

    /**
     * Tests Cancelled state with null partial file.
     */
    @Test
    fun `Cancelled state handles null partial file`() {
        val state = DownloadState.Cancelled(
            downloadId = "test-id",
            partialFile = null,
            downloadedBytes = 0
        )
        
        assertNull(state.partialFile)
        assertEquals(0, state.downloadedBytes)
    }

    /**
     * Tests Completed state with zero duration (instant download).
     */
    @Test
    fun `Completed state handles zero duration`() {
        val file = tempFolder.newFile("instant.zip")
        val state = DownloadState.Completed(
            downloadId = "test-id",
            file = file,
            totalBytes = 1000,
            downloadDuration = 0,
            averageSpeed = 0
        )
        
        assertEquals(0, state.downloadDuration)
        assertEquals("0 B/s", state.getFormattedSpeed())
    }

    /**
     * Tests Completed state with very high average speed.
     */
    @Test
    fun `Completed state handles very high speeds`() {
        val file = tempFolder.newFile("fast.zip")
        val gigabytePerSec = 1024L * 1024 * 1024
        val state = DownloadState.Completed(
            downloadId = "test-id",
            file = file,
            totalBytes = gigabytePerSec * 10,
            downloadDuration = 10000,
            averageSpeed = gigabytePerSec
        )
        
        assertEquals("1.00 GB/s", state.getFormattedSpeed())
    }

    /**
     * Tests NetworkError with non-null cause.
     */
    @Test
    fun `NetworkError stores cause exception`() {
        val cause = IOException("Network unreachable")
        val error = DownloadError.NetworkError(
            message = "Download failed",
            cause = cause,
            errorCode = "NETWORK_ERROR"
        )
        
        assertEquals("Download failed", error.message)
        assertEquals(cause, error.cause)
        assertEquals("NETWORK_ERROR", error.errorCode)
    }

    /**
     * Tests HttpError with various status codes.
     */
    @Test
    fun `HttpError handles various HTTP status codes`() {
        val error400 = DownloadError.HttpError(400, "Bad Request")
        assertEquals("HTTP 400: Bad Request", error400.message)
        
        val error500 = DownloadError.HttpError(500, "Internal Server Error")
        assertEquals("HTTP 500: Internal Server Error", error500.message)
        
        val error503 = DownloadError.HttpError(503, "Service Unavailable")
        assertEquals("HTTP 503: Service Unavailable", error503.message)
    }

    /**
     * Tests all FileSystemErrorType values individually.
     */
    @Test
    fun `FileSystemError handles all error types`() {
        val errors = listOf(
            DownloadError.FileSystemError("Disk full", errorType = FileSystemErrorType.DISK_FULL),
            DownloadError.FileSystemError("Permission denied", errorType = FileSystemErrorType.PERMISSION_DENIED),
            DownloadError.FileSystemError("File not found", errorType = FileSystemErrorType.FILE_NOT_FOUND),
            DownloadError.FileSystemError("Directory not found", errorType = FileSystemErrorType.DIRECTORY_NOT_FOUND),
            DownloadError.FileSystemError("File exists", errorType = FileSystemErrorType.FILE_ALREADY_EXISTS),
            DownloadError.FileSystemError("Invalid path", errorType = FileSystemErrorType.INVALID_PATH),
            DownloadError.FileSystemError("IO error", errorType = FileSystemErrorType.IO_ERROR)
        )
        
        assertEquals(7, errors.size)
        errors.forEach { error ->
            assertNotNull(error.message)
            assertNotNull(error.errorType)
        }
    }

    /**
     * Tests all TimeoutType values individually.
     */
    @Test
    fun `TimeoutError handles all timeout types`() {
        val connectTimeout = DownloadError.TimeoutError("Connect timeout", timeoutType = TimeoutType.CONNECT_TIMEOUT)
        assertEquals(TimeoutType.CONNECT_TIMEOUT, connectTimeout.timeoutType)
        
        val readTimeout = DownloadError.TimeoutError("Read timeout", timeoutType = TimeoutType.READ_TIMEOUT)
        assertEquals(TimeoutType.READ_TIMEOUT, readTimeout.timeoutType)
        
        val writeTimeout = DownloadError.TimeoutError("Write timeout", timeoutType = TimeoutType.WRITE_TIMEOUT)
        assertEquals(TimeoutType.WRITE_TIMEOUT, writeTimeout.timeoutType)
    }

    /**
     * Tests AuthenticationError with 403 Forbidden.
     */
    @Test
    fun `AuthenticationError handles 403 Forbidden`() {
        val error = DownloadError.AuthenticationError(
            message = "Forbidden",
            statusCode = 403
        )
        
        assertEquals("Forbidden", error.message)
        assertEquals(403, error.statusCode)
    }

    /**
     * Tests UnknownError with cause.
     */
    @Test
    fun `UnknownError stores cause exception`() {
        val cause = RuntimeException("Unexpected error")
        val error = DownloadError.UnknownError(
            message = "Unknown error",
            cause = cause
        )
        
        assertEquals("Unknown error", error.message)
        assertEquals(cause, error.cause)
    }

    /**
     * Tests DownloadProgress getRemainingBytes() with zero total (unknown).
     */
    @Test
    fun `DownloadProgress getRemainingBytes handles zero total gracefully`() {
        val progress = DownloadProgress(500, 0)
        // When total is 0, should coerce to 0
        assertEquals(0, progress.getRemainingBytes())
    }

    /**
     * Tests isTotalSizeKnown() with zero total.
     */
    @Test
    fun `DownloadProgress isTotalSizeKnown returns false for zero`() {
        val progress = DownloadProgress(100, 0)
        assertFalse(progress.isTotalSizeKnown())
    }

    /**
     * Tests getPercentage() with zero total.
     */
    @Test
    fun `DownloadProgress getPercentage returns -1 for zero total`() {
        val progress = DownloadProgress(100, 0)
        assertEquals(-1f, progress.getPercentage(), 0.01f)
    }

    /**
     * Tests Downloading state getProgressPercentage() with very small values.
     */
    @Test
    fun `Downloading state handles small byte values`() {
        val progress = DownloadProgress(1, 100)
        val speed = DownloadSpeed(1, 1, 99000)
        val state = DownloadState.Downloading("test-id", progress, speed)
        
        assertEquals(1f, state.getProgressPercentage(), 0.01f)
    }

    /**
     * Tests Downloading state getProgressPercentage() with large values.
     */
    @Test
    fun `Downloading state handles large byte values`() {
        val fiveGB = 5L * 1024 * 1024 * 1024
        val tenGB = 10L * 1024 * 1024 * 1024
        val progress = DownloadProgress(fiveGB, tenGB)
        val speed = DownloadSpeed(10 * 1024 * 1024, 10 * 1024 * 1024, 500000)
        val state = DownloadState.Downloading("test-id", progress, speed)
        
        assertEquals(50f, state.getProgressPercentage(), 0.01f)
    }

    /**
     * Tests State equality for same state types.
     */
    @Test
    fun `State equality works correctly`() {
        val idle1 = DownloadState.Idle("test-id")
        val idle2 = DownloadState.Idle("test-id")
        val idle3 = DownloadState.Idle("different-id")
        
        assertEquals(idle1, idle2)
        assertNotEquals(idle1, idle3)
    }

    /**
     * Tests Validating state with empty validationStage.
     */
    @Test
    fun `Validating state handles empty validation stage`() {
        val state = DownloadState.Validating("test-id", "")
        
        assertEquals("test-id", state.downloadId)
        assertEquals("", state.validationStage)
    }

    /**
     * Tests Validating state with very long validation stage message.
     */
    @Test
    fun `Validating state handles long validation stage message`() {
        val longMessage = "Validating ".repeat(100)
        val state = DownloadState.Validating("test-id", longMessage)
        
        assertEquals(longMessage, state.validationStage)
    }

    /**
     * Tests Connecting state with attempt number 1 (default).
     */
    @Test
    fun `Connecting state uses default attempt number 1`() {
        val state = DownloadState.Connecting(
            downloadId = "test-id",
            url = "https://example.com/file"
        )
        
        assertEquals(1, state.attemptNumber)
    }

    /**
     * Tests Connecting state with very high attempt number.
     */
    @Test
    fun `Connecting state handles high attempt number`() {
        val state = DownloadState.Connecting(
            downloadId = "test-id",
            url = "https://example.com/file",
            attemptNumber = 100
        )
        
        assertEquals(100, state.attemptNumber)
    }

    /**
     * Tests Completed state with unverified checksum (default).
     */
    @Test
    fun `Completed state defaults verified to false`() {
        val file = tempFolder.newFile("test.zip")
        val state = DownloadState.Completed(
            downloadId = "test-id",
            file = file,
            totalBytes = 1000,
            downloadDuration = 5000,
            averageSpeed = 200
        )
        
        assertFalse(state.verified)
    }

    /**
     * Tests Completed state with verified checksum.
     */
    @Test
    fun `Completed state handles verified checksum`() {
        val file = tempFolder.newFile("verified.zip")
        val state = DownloadState.Completed(
            downloadId = "test-id",
            file = file,
            totalBytes = 1000,
            downloadDuration = 5000,
            averageSpeed = 200,
            verified = true
        )
        
        assertTrue(state.verified)
    }

    /**
     * Tests Failed state with canRetry false (default is true).
     */
    @Test
    fun `Failed state handles canRetry false`() {
        val error = DownloadError.NetworkError("Fatal error")
        val state = DownloadState.Failed(
            downloadId = "test-id",
            error = error,
            canRetry = false
        )
        
        assertFalse(state.canRetry)
    }

    /**
     * Tests Failed state with default attempt number (1).
     */
    @Test
    fun `Failed state uses default attempt number 1`() {
        val error = DownloadError.NetworkError("Connection failed")
        val state = DownloadState.Failed(
            downloadId = "test-id",
            error = error
        )
        
        assertEquals(1, state.attemptNumber)
    }

    /**
     * Tests Paused state with default pausedAt (current time).
     */
    @Test
    fun `Paused state uses current time as default pausedAt`() {
        val progress = DownloadProgress(500, 1000)
        val beforeCreation = System.currentTimeMillis()
        val state = DownloadState.Paused("test-id", progress)
        val afterCreation = System.currentTimeMillis()
        
        // pausedAt should be between before and after
        assertTrue(state.pausedAt >= beforeCreation)
        assertTrue(state.pausedAt <= afterCreation)
    }

    /**
     * Tests DownloadProgress with very large file size (TB range).
     */
    @Test
    fun `DownloadProgress handles terabyte-sized files`() {
        val oneTB = 1024L * 1024 * 1024 * 1024
        val progress = DownloadProgress(oneTB / 2, oneTB)
        
        assertEquals(50f, progress.getPercentage(), 0.01f)
        assertEquals(oneTB / 2, progress.getRemainingBytes())
    }

    /**
     * Tests DownloadSpeed getEstimatedTimeFormatted() with negative value.
     */
    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted handles negative time correctly`() {
        val speed = DownloadSpeed(1024, 512, -1)
        assertEquals("Unknown", speed.getEstimatedTimeFormatted())
        
        val speed2 = DownloadSpeed(1024, 512, -1000)
        assertEquals("Unknown", speed2.getEstimatedTimeFormatted())
    }
}
