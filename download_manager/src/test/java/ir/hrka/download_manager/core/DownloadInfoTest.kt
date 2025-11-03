package ir.hrka.download_manager.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Comprehensive test suite for [DownloadInfo] and all related data classes.
 *
 * This test class validates the download information aggregation model, including
 * metadata extraction, progress tracking, timing calculations, server capabilities,
 * validation results, and state history tracking.
 *
 * **Test Coverage:**
 * - DownloadInfo state queries (isActive, isTerminal, canResume, canRetry)
 * - DownloadMetadata content length and formatting
 * - DownloadProgressInfo calculations and formatting
 * - DownloadTiming elapsed and active time calculations
 * - ServerInfo resume and parallel download capabilities
 * - StateTransition duration calculations
 * - DownloadValidation check filtering
 * - ValidationCheck severity and blocking detection
 *
 * **Total Tests:** 25
 *
 * @see DownloadInfo
 * @see DownloadMetadata
 * @see DownloadProgressInfo
 * @see DownloadTiming
 * @see ServerInfo
 * @see DownloadValidation
 * @see ValidationCheck
 */
class DownloadInfoTest {

    /**
     * JUnit rule for creating isolated temporary test files.
     */
    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * Tests that isActive() correctly identifies actively downloading states.
     *
     * **What is tested:**
     * - isActive() returns true for Downloading state
     * - Active states include: Downloading, Connecting, Validating
     * - Used to determine if download is currently in progress
     *
     * **Validates:**
     * - Downloading state → isActive() = true
     * - Active state detection logic
     * - Download in-progress identification
     */
    @Test
    fun `isActive returns true for downloading state`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Downloading(
            "test-id",
            DownloadProgress(500, 1000),
            DownloadSpeed(1024, 512, 5000)
        )

        val info = DownloadInfo(
            id = "test-id",
            request = request,
            state = state,
            file = file,
            metadata = DownloadMetadata(),
            progress = DownloadProgressInfo(),
            timing = DownloadTiming(),
            serverInfo = ServerInfo()
        )

        assertTrue(info.isActive())
    }

    /**
     * Tests that isActive() correctly identifies connecting state as active.
     *
     * **What is tested:**
     * - isActive() returns true for Connecting state
     * - Connection establishment is considered active
     * - Download hasn't started but is in progress
     *
     * **Validates:**
     * - Connecting state → isActive() = true
     * - Pre-download states included in active
     */
    @Test
    fun `isActive returns true for connecting state`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Connecting("test-id", "https://example.com/file.zip")

        val info = DownloadInfo(
            id = "test-id",
            request = request,
            state = state,
            file = file,
            metadata = DownloadMetadata(),
            progress = DownloadProgressInfo(),
            timing = DownloadTiming(),
            serverInfo = ServerInfo()
        )

        assertTrue(info.isActive())
    }

    /**
     * Tests that isActive() returns false for completed downloads.
     *
     * **What is tested:**
     * - isActive() returns false for Completed state
     * - Terminal states are not active
     * - Completed download is no longer in progress
     *
     * **Validates:**
     * - Completed state → isActive() = false
     * - Terminal state detection
     * - Download finished identification
     */
    @Test
    fun `isActive returns false for completed state`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Completed("test-id", file, 1000, 10000, 100)

        val info = DownloadInfo(
            id = "test-id",
            request = request,
            state = state,
            file = file,
            metadata = DownloadMetadata(),
            progress = DownloadProgressInfo(),
            timing = DownloadTiming(),
            serverInfo = ServerInfo()
        )

        assertFalse(info.isActive())
    }

    /**
     * Tests that isTerminal() identifies completed state as terminal.
     *
     * **What is tested:**
     * - isTerminal() returns true for Completed state
     * - Terminal states: Completed, Failed, Cancelled
     * - Download has reached final state (no more transitions)
     *
     * **Validates:**
     * - Completed state → isTerminal() = true
     * - Terminal state detection
     * - No further state transitions expected
     */
    @Test
    fun `isTerminal returns true for completed state`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Completed("test-id", file, 1000, 10000, 100)

        val info = DownloadInfo(
            id = "test-id",
            request = request,
            state = state,
            file = file,
            metadata = DownloadMetadata(),
            progress = DownloadProgressInfo(),
            timing = DownloadTiming(),
            serverInfo = ServerInfo()
        )

        assertTrue(info.isTerminal())
    }

    /**
     * Tests that isTerminal() identifies failed state as terminal.
     *
     * **What is tested:**
     * - isTerminal() returns true for Failed state
     * - Failed downloads are terminal (though may be retryable)
     * - Terminal doesn't mean permanent (can retry)
     *
     * **Validates:**
     * - Failed state → isTerminal() = true
     * - Error states are terminal
     * - Distinction between terminal and retryable
     */
    @Test
    fun `isTerminal returns true for failed state`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val error = DownloadError.NetworkError("Connection failed")
        val state = DownloadState.Failed("test-id", error)

        val info = DownloadInfo(
            id = "test-id",
            request = request,
            state = state,
            file = file,
            metadata = DownloadMetadata(),
            progress = DownloadProgressInfo(),
            timing = DownloadTiming(),
            serverInfo = ServerInfo()
        )

        assertTrue(info.isTerminal())
    }

    /**
     * Tests resume capability detection with all required conditions.
     *
     * **What is tested:**
     * - canResume() requires THREE conditions to be true:
     *   1. State is Paused or Failed
     *   2. Server supports range requests
     *   3. Partial file exists on disk
     * - All conditions checked simultaneously
     *
     * **Validates:**
     * - Paused state → canResume() depends on other conditions
     * - supportsRangeRequests = true → required
     * - file.exists() = true → required
     * - All three → canResume() = true
     */
    @Test
    fun `canResume returns true for paused state with range support and existing file`() {
        val file = tempFolder.newFile("test.zip")
        file.writeText("partial content")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Paused("test-id", DownloadProgress(500, 1000))

        val info = DownloadInfo(
            id = "test-id",
            request = request,
            state = state,
            file = file,
            metadata = DownloadMetadata(),
            progress = DownloadProgressInfo(),
            timing = DownloadTiming(),
            serverInfo = ServerInfo(supportsRangeRequests = true)
        )

        assertTrue(info.canResume())
    }

    /**
     * Tests that resume fails when server doesn't support range requests.
     *
     * **What is tested:**
     * - canResume() returns false when supportsRangeRequests = false
     * - Even if state is Paused and file exists
     * - Server capability is blocking requirement
     *
     * **Validates:**
     * - Server capability check
     * - Resume requires HTTP Range header support
     * - False when server doesn't support resume
     */
    @Test
    fun `canResume returns false when server doesnt support range`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Paused("test-id", DownloadProgress(500, 1000))

        val info = DownloadInfo(
            id = "test-id",
            request = request,
            state = state,
            file = file,
            metadata = DownloadMetadata(),
            progress = DownloadProgressInfo(),
            timing = DownloadTiming(),
            serverInfo = ServerInfo(supportsRangeRequests = false)
        )

        assertFalse(info.canResume())
    }

    /**
     * Tests retry capability detection from failed download state.
     *
     * **What is tested:**
     * - canRetry() checks if Failed state has canRetry flag set
     * - canRetry flag determines if retry is allowed
     * - Returns true only for Failed state with canRetry = true
     *
     * **Validates:**
     * - Failed state with canRetry = true → canRetry() = true
     * - Retry capability from state flag
     * - Automatic retry logic enablement
     */
    @Test
    fun `canRetry returns true for failed state with canRetry flag`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val error = DownloadError.NetworkError("Connection failed")
        val state = DownloadState.Failed("test-id", error, canRetry = true)

        val info = DownloadInfo(
            id = "test-id",
            request = request,
            state = state,
            file = file,
            metadata = DownloadMetadata(),
            progress = DownloadProgressInfo(),
            timing = DownloadTiming(),
            serverInfo = ServerInfo()
        )

        assertTrue(info.canRetry())
    }

    /**
     * Tests content length detection in DownloadMetadata.
     *
     * **What is tested:**
     * - hasContentLength() returns true when contentLength > 0
     * - Returns false when contentLength is -1 (unknown)
     * - Distinguishes known vs unknown file sizes
     *
     * **Validates:**
     * - Known: 1024 bytes → true
     * - Unknown: -1 → false
     * - Content-Length header detection
     */
    @Test
    fun `DownloadMetadata hasContentLength returns correct values`() {
        val withLength = DownloadMetadata(contentLength = 1024)
        val withoutLength = DownloadMetadata(contentLength = -1)

        assertTrue(withLength.hasContentLength())
        assertFalse(withoutLength.hasContentLength())
    }

    /**
     * Tests formatted content length for known sizes.
     *
     * **What is tested:**
     * - getFormattedContentLength() converts bytes to readable format
     * - 1 MB (1024*1024) → "1.00 MB"
     * - Uses formatBytes() utility
     *
     * **Validates:**
     * - Size formatting: 1048576 bytes = 1 MB
     * - Format: "1.00 MB"
     * - Human-readable output
     */
    @Test
    fun `DownloadMetadata getFormattedContentLength returns correct string`() {
        val metadata = DownloadMetadata(contentLength = 1024 * 1024)

        assertEquals("1.00 MB", metadata.getFormattedContentLength())
    }

    /**
     * Tests formatted content length for unknown sizes.
     *
     * **What is tested:**
     * - getFormattedContentLength() returns "Unknown" when size is -1
     * - Graceful handling of missing Content-Length header
     * - User-friendly message instead of negative number
     *
     * **Validates:**
     * - Unknown size → "Unknown" string
     * - No numeric value for unknown
     * - Clear indication of unavailable data
     */
    @Test
    fun `DownloadMetadata getFormattedContentLength returns Unknown for no length`() {
        val metadata = DownloadMetadata(contentLength = -1)

        assertEquals("Unknown", metadata.getFormattedContentLength())
    }

    /**
     * Tests size known detection in DownloadProgressInfo.
     *
     * **What is tested:**
     * - isSizeKnown() returns true when totalBytes > 0
     * - Returns false when totalBytes is -1
     * - Determines if progress percentage can be calculated
     *
     * **Validates:**
     * - Known size: 1000 bytes → true
     * - Unknown size: -1 → false
     * - Prerequisite for percentage calculation
     */
    @Test
    fun `DownloadProgressInfo isSizeKnown returns correct values`() {
        val known = DownloadProgressInfo(totalBytes = 1000)
        val unknown = DownloadProgressInfo(totalBytes = -1)

        assertTrue(known.isSizeKnown())
        assertFalse(unknown.isSizeKnown())
    }

    /**
     * Tests remaining bytes calculation in DownloadProgressInfo.
     *
     * **What is tested:**
     * - getRemainingBytes() = totalBytes - bytesDownloaded
     * - Calculation for known file size
     * - Result is coerced to non-negative
     *
     * **Validates:**
     * - 1000 - 250 = 750 bytes remaining
     * - Subtraction accuracy
     * - Non-negative guarantee
     */
    @Test
    fun `DownloadProgressInfo getRemainingBytes calculates correctly`() {
        val progress = DownloadProgressInfo(bytesDownloaded = 250, totalBytes = 1000)

        assertEquals(750, progress.getRemainingBytes())
    }

    /**
     * Tests remaining bytes when total size is unknown.
     *
     * **What is tested:**
     * - getRemainingBytes() returns -1 for unknown total size
     * - Cannot calculate remaining when total is unknown
     * - Graceful handling of indeterminate progress
     *
     * **Validates:**
     * - Unknown size → -1 returned
     * - Special value indicates unknown
     * - No invalid calculation attempt
     */
    @Test
    fun `DownloadProgressInfo getRemainingBytes returns -1 for unknown size`() {
        val progress = DownloadProgressInfo(bytesDownloaded = 250, totalBytes = -1)

        assertEquals(-1, progress.getRemainingBytes())
    }

    /**
     * Tests current speed formatting in DownloadProgressInfo.
     *
     * **What is tested:**
     * - getFormattedCurrentSpeed() formats bytes/sec with units
     * - 102400 bytes/sec → "100.00 KB/s"
     * - Includes rate suffix "/s"
     *
     * **Validates:**
     * - Speed formatting: 102400 B/s = 100 KB/s
     * - Format: "100.00 KB/s"
     * - Unit and rate indication
     */
    @Test
    fun `DownloadProgressInfo getFormattedCurrentSpeed returns correct string`() {
        val progress = DownloadProgressInfo(currentSpeed = 102400)

        assertEquals("100.00 KB/s", progress.getFormattedCurrentSpeed())
    }

    /**
     * Tests average speed formatting in DownloadProgressInfo.
     *
     * **What is tested:**
     * - getFormattedAverageSpeed() formats average bytes/sec
     * - 51200 bytes/sec → "50.00 KB/s"
     * - Independent from current speed
     *
     * **Validates:**
     * - Average speed formatting: 51200 B/s = 50 KB/s
     * - Format: "50.00 KB/s"
     * - Separate from current speed metric
     */
    @Test
    fun `DownloadProgressInfo getFormattedAverageSpeed returns correct string`() {
        val progress = DownloadProgressInfo(averageSpeed = 51200)

        assertEquals("50.00 KB/s", progress.getFormattedAverageSpeed())
    }

    /**
     * Tests time remaining formatting in DownloadProgressInfo.
     *
     * **What is tested:**
     * - getFormattedTimeRemaining() formats milliseconds to readable time
     * - 90000ms (90 seconds) → "1 min"
     * - Uses appropriate time unit (seconds/minutes/hours)
     *
     * **Validates:**
     * - Time conversion: 90000ms = 1 minute
     * - Format: "1 min" (seconds omitted for minute+ durations)
     * - Human-readable ETA
     */
    @Test
    fun `DownloadProgressInfo getFormattedTimeRemaining returns correct string`() {
        val progress = DownloadProgressInfo(estimatedTimeRemaining = 90000)

        assertEquals("1 min", progress.getFormattedTimeRemaining())
    }

    /**
     * Tests time remaining formatting for unknown/negative values.
     *
     * **What is tested:**
     * - getFormattedTimeRemaining() returns "Unknown" for negative values
     * - -1 indicates time cannot be estimated
     * - Graceful handling of indeterminate ETA
     *
     * **Validates:**
     * - Negative time → "Unknown"
     * - Special case handling
     * - Clear indication of unavailable estimate
     */
    @Test
    fun `DownloadProgressInfo getFormattedTimeRemaining returns Unknown for negative time`() {
        val progress = DownloadProgressInfo(estimatedTimeRemaining = -1)

        assertEquals("Unknown", progress.getFormattedTimeRemaining())
    }

    /**
     * Tests total elapsed time calculation in DownloadTiming.
     *
     * **What is tested:**
     * - getTotalElapsedTime() = currentTime - startedAt
     * - Includes paused time in total
     * - Calculation is in milliseconds
     * - Approximately 10 seconds (allows ±100ms variance)
     *
     * **Validates:**
     * - Time calculation accuracy
     * - Total elapsed includes all time
     * - ~10000ms with variance allowance
     */
    @Test
    fun `DownloadTiming getTotalElapsedTime calculates correctly`() {
        val startTime = System.currentTimeMillis() - 10000
        val timing = DownloadTiming(startedAt = startTime)

        val elapsed = timing.getTotalElapsedTime()
        assertTrue(elapsed >= 9900 && elapsed <= 10100)
    }

    /**
     * Tests active download time calculation excluding paused duration.
     *
     * **What is tested:**
     * - getActiveDownloadTime() = totalElapsed - pausedDuration
     * - Paused time is subtracted from total
     * - Gives actual download time (10s total - 3s paused = 7s active)
     *
     * **Validates:**
     * - Active time = 10000ms - 3000ms = 7000ms
     * - Paused duration exclusion
     * - Accurate active time tracking
     */
    @Test
    fun `DownloadTiming getActiveDownloadTime excludes paused duration`() {
        val startTime = System.currentTimeMillis() - 10000
        val timing = DownloadTiming(startedAt = startTime, pausedDuration = 3000)

        val active = timing.getActiveDownloadTime()
        assertTrue(active >= 6900 && active <= 7100)
    }

    /**
     * Tests in-progress detection based on timing information.
     *
     * **What is tested:**
     * - isInProgress() returns true when startedAt is set but completedAt is null
     * - Returns false when completed (both times set)
     * - Returns false when not started (startedAt is null)
     *
     * **Validates:**
     * - In progress: startedAt != null, completedAt = null → true
     * - Completed: both set → false
     * - Not started: startedAt = null → false
     */
    @Test
    fun `DownloadTiming isInProgress returns correct values`() {
        val inProgress = DownloadTiming(startedAt = System.currentTimeMillis())
        val completed = DownloadTiming(
            startedAt = System.currentTimeMillis() - 10000,
            completedAt = System.currentTimeMillis()
        )
        val notStarted = DownloadTiming()

        assertTrue(inProgress.isInProgress())
        assertFalse(completed.isInProgress())
        assertFalse(notStarted.isInProgress())
    }

    /**
     * Tests resume capability detection from ServerInfo.
     *
     * **What is tested:**
     * - canResume() is alias for supportsRangeRequests
     * - Returns true when server accepts HTTP Range headers
     * - Returns false otherwise
     *
     * **Validates:**
     * - supportsRangeRequests = true → canResume() = true
     * - supportsRangeRequests = false → canResume() = false
     * - Server capability check
     */
    @Test
    fun `ServerInfo canResume returns correct value`() {
        val canResume = ServerInfo(supportsRangeRequests = true)
        val cannotResume = ServerInfo(supportsRangeRequests = false)

        assertTrue(canResume.canResume())
        assertFalse(cannotResume.canResume())
    }

    /**
     * Tests parallel download capability detection from ServerInfo.
     *
     * **What is tested:**
     * - supportsParallelDownloads() returns true when maxConnections > 1
     * - Returns false when maxConnections = 1
     * - Used to determine if file can be split into chunks
     *
     * **Validates:**
     * - maxConnectionsAllowed = 4 → supportsParallelDownloads() = true
     * - maxConnectionsAllowed = 1 → supportsParallelDownloads() = false
     * - Multi-connection capability detection
     */
    @Test
    fun `ServerInfo supportsParallelDownloads returns correct value`() {
        val supports = ServerInfo(maxConnectionsAllowed = 4)
        val doesNotSupport = ServerInfo(maxConnectionsAllowed = 1)

        assertTrue(supports.supportsParallelDownloads())
        assertFalse(doesNotSupport.supportsParallelDownloads())
    }

    /**
     * Tests state duration calculation from StateTransition.
     *
     * **What is tested:**
     * - getStateDuration() calculates time spent in previous state
     * - Duration = current transition time - previous transition time
     * - 6000 - 1000 = 5000ms in previous state
     *
     * **Validates:**
     * - Duration calculation: 6000 - 1000 = 5000
     * - Transition timing tracking
     * - State residence time measurement
     */
    @Test
    fun `StateTransition getStateDuration calculates correctly`() {
        val firstTransition = StateTransition(
            from = DownloadState.Idle("test"),
            to = DownloadState.Connecting("test", "url"),
            timestamp = 1000
        )
        val secondTransition = StateTransition(
            from = DownloadState.Connecting("test", "url"),
            to = DownloadState.Downloading("test", DownloadProgress(0, 1000), DownloadSpeed(0, 0, 0)),
            timestamp = 6000
        )

        assertEquals(5000, secondTransition.getStateDuration(firstTransition))
    }

    /**
     * Tests filtering of failed validation checks.
     *
     * **What is tested:**
     * - getFailedChecks() returns only checks where passed = false
     * - Filters out passed checks
     * - Returns list of validation failures
     *
     * **Validates:**
     * - 3 checks total, 1 failed
     * - Filtered list contains only the failed check (DISK_SPACE)
     * - Correct filtering logic
     */
    @Test
    fun `DownloadValidation getFailedChecks returns only failed checks`() {
        val checks = listOf(
            ValidationCheck(ValidationCheckType.URL_VALIDITY, true, "URL is valid"),
            ValidationCheck(ValidationCheckType.DISK_SPACE, false, "Not enough space"),
            ValidationCheck(ValidationCheckType.NETWORK_AVAILABILITY, true, "Network available")
        )
        val validation = DownloadValidation(false, checks)

        val failedChecks = validation.getFailedChecks()
        assertEquals(1, failedChecks.size)
        assertEquals(ValidationCheckType.DISK_SPACE, failedChecks[0].type)
    }

    /**
     * Tests filtering of passed validation checks.
     *
     * **What is tested:**
     * - getPassedChecks() returns only checks where passed = true
     * - Filters out failed checks
     * - Returns list of successful validations
     *
     * **Validates:**
     * - 3 checks total, 2 passed
     * - Filtered list contains 2 passed checks
     * - Complement of getFailedChecks()
     */
    @Test
    fun `DownloadValidation getPassedChecks returns only passed checks`() {
        val checks = listOf(
            ValidationCheck(ValidationCheckType.URL_VALIDITY, true, "URL is valid"),
            ValidationCheck(ValidationCheckType.DISK_SPACE, false, "Not enough space"),
            ValidationCheck(ValidationCheckType.NETWORK_AVAILABILITY, true, "Network available")
        )
        val validation = DownloadValidation(false, checks)

        val passedChecks = validation.getPassedChecks()
        assertEquals(2, passedChecks.size)
    }

    /**
     * Tests specific validation failure detection.
     *
     * **What is tested:**
     * - hasFailedCheck() detects if specific check type failed
     * - Returns true only for failed checks of specified type
     * - Returns false for passed checks or checks not in list
     *
     * **Validates:**
     * - DISK_SPACE failed → hasFailedCheck(DISK_SPACE) = true
     * - URL_VALIDITY passed → hasFailedCheck(URL_VALIDITY) = false
     * - NETWORK_AVAILABILITY not in list → hasFailedCheck(NETWORK_AVAILABILITY) = false
     */
    @Test
    fun `DownloadValidation hasFailedCheck returns correct value`() {
        val checks = listOf(
            ValidationCheck(ValidationCheckType.URL_VALIDITY, true, "URL is valid"),
            ValidationCheck(ValidationCheckType.DISK_SPACE, false, "Not enough space")
        )
        val validation = DownloadValidation(false, checks)

        assertTrue(validation.hasFailedCheck(ValidationCheckType.DISK_SPACE))
        assertFalse(validation.hasFailedCheck(ValidationCheckType.URL_VALIDITY))
        assertFalse(validation.hasFailedCheck(ValidationCheckType.NETWORK_AVAILABILITY))
    }

    /**
     * Tests blocking error detection in ValidationCheck.
     *
     * **What is tested:**
     * - isBlocking() returns true for failed checks with ERROR severity
     * - Returns false for warnings or passed checks
     * - Blocking errors prevent download from starting
     *
     * **Validates:**
     * - Failed + ERROR severity → isBlocking() = true
     * - Failed + WARNING severity → isBlocking() = false
     * - Passed (regardless of severity) → isBlocking() = false
     */
    @Test
    fun `ValidationCheck isBlocking returns correct values`() {
        val blockingError = ValidationCheck(
            ValidationCheckType.DISK_SPACE,
            false,
            "Error",
            ValidationSeverity.ERROR
        )
        val warning = ValidationCheck(
            ValidationCheckType.DISK_SPACE,
            false,
            "Warning",
            ValidationSeverity.WARNING
        )
        val passed = ValidationCheck(
            ValidationCheckType.DISK_SPACE,
            true,
            "OK",
            ValidationSeverity.ERROR
        )

        assertTrue(blockingError.isBlocking())
        assertFalse(warning.isBlocking())
        assertFalse(passed.isBlocking())
    }

    /**
     * Tests warning detection in ValidationCheck.
     *
     * **What is tested:**
     * - isWarning() returns true only for WARNING severity
     * - Independent of passed/failed status
     * - Used to show non-blocking issues
     *
     * **Validates:**
     * - WARNING severity → isWarning() = true
     * - ERROR severity → isWarning() = false
     * - Severity-based detection
     */
    @Test
    fun `ValidationCheck isWarning returns correct value`() {
        val warning = ValidationCheck(
            ValidationCheckType.DISK_SPACE,
            false,
            "Warning",
            ValidationSeverity.WARNING
        )
        val error = ValidationCheck(
            ValidationCheckType.DISK_SPACE,
            false,
            "Error",
            ValidationSeverity.ERROR
        )

        assertTrue(warning.isWarning())
        assertFalse(error.isWarning())
    }
}
