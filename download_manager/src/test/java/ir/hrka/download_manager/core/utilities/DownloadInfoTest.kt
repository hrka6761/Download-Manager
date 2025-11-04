package ir.hrka.download_manager.core.utilities

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
 * **Total Tests:** 52
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
            to = DownloadState.Downloading(
                "test",
                DownloadProgress(0, 1000),
                DownloadSpeed(0, 0, 0)
            ),
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

    // ==================== Additional Edge Cases & Comprehensive Tests ====================

    /**
     * Tests isActive() returns false for Idle state.
     */
    @Test
    fun `isActive returns false for idle state`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Idle("test-id")

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
     * Tests isActive() returns false for Paused state.
     */
    @Test
    fun `isActive returns false for paused state`() {
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
            serverInfo = ServerInfo()
        )

        assertFalse(info.isActive())
    }

    /**
     * Tests isActive() returns true for Validating state.
     */
    @Test
    fun `isActive returns true for validating state`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Validating("test-id", "Checking prerequisites")

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
     * Tests isTerminal() returns true for Cancelled state.
     */
    @Test
    fun `isTerminal returns true for cancelled state`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Cancelled("test-id", file, 500)

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
     * Tests isTerminal() returns false for non-terminal states.
     */
    @Test
    fun `isTerminal returns false for downloading state`() {
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

        assertFalse(info.isTerminal())
    }

    /**
     * Tests canResume() with Failed state and all conditions met.
     */
    @Test
    fun `canResume returns true for failed state with range support and existing file`() {
        val file = tempFolder.newFile("test.zip")
        file.writeText("partial content")
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
            serverInfo = ServerInfo(supportsRangeRequests = true)
        )

        assertTrue(info.canResume())
    }

    /**
     * Tests canResume() returns false when file doesn't exist.
     */
    @Test
    fun `canResume returns false when partial file does not exist`() {
        val file = tempFolder.newFile("test.zip")
        file.delete() // File doesn't exist
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

        assertFalse(info.canResume())
    }

    /**
     * Tests canRetry() returns false when canRetry flag is false.
     */
    @Test
    fun `canRetry returns false for failed state with canRetry false`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val error = DownloadError.NetworkError("Fatal error")
        val state = DownloadState.Failed("test-id", error, canRetry = false)

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

        assertFalse(info.canRetry())
    }

    /**
     * Tests canRetry() returns false for non-Failed states.
     */
    @Test
    fun `canRetry returns false for completed state`() {
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

        assertFalse(info.canRetry())
    }

    /**
     * Tests DownloadMetadata with all null values.
     */
    @Test
    fun `DownloadMetadata handles all null values`() {
        val metadata = DownloadMetadata()
        
        assertNull(metadata.mimeType)
        assertEquals(-1, metadata.contentLength)
        assertNull(metadata.fileName)
        assertNull(metadata.lastModified)
        assertNull(metadata.etag)
        assertNull(metadata.contentEncoding)
        assertNull(metadata.contentDisposition)
        assertTrue(metadata.customMetadata.isEmpty())
    }

    /**
     * Tests DownloadMetadata with all values populated.
     */
    @Test
    fun `DownloadMetadata stores all properties correctly`() {
        val metadata = DownloadMetadata(
            mimeType = "application/zip",
            contentLength = 1024000,
            fileName = "file.zip",
            lastModified = 1234567890L,
            etag = "abc123",
            contentEncoding = "gzip",
            contentDisposition = "attachment; filename=\"file.zip\"",
            customMetadata = mapOf("key1" to "value1")
        )
        
        assertEquals("application/zip", metadata.mimeType)
        assertEquals(1024000, metadata.contentLength)
        assertEquals("file.zip", metadata.fileName)
        assertEquals(1234567890L, metadata.lastModified)
        assertEquals("abc123", metadata.etag)
        assertEquals("gzip", metadata.contentEncoding)
        assertEquals("attachment; filename=\"file.zip\"", metadata.contentDisposition)
        assertEquals(1, metadata.customMetadata.size)
    }

    /**
     * Tests DownloadProgressInfo with default values.
     */
    @Test
    fun `DownloadProgressInfo defaults to zero/unknown values`() {
        val progress = DownloadProgressInfo()
        
        assertEquals(0, progress.bytesDownloaded)
        assertEquals(-1, progress.totalBytes)
        assertEquals(0f, progress.percentage, 0.01f)
        assertEquals(0, progress.currentSpeed)
        assertEquals(0, progress.averageSpeed)
        assertEquals(-1, progress.estimatedTimeRemaining)
        assertEquals(0, progress.chunksDownloaded)
        assertEquals(-1, progress.totalChunks)
    }

    /**
     * Tests DownloadProgressInfo getRemainingBytes() with unknown size.
     */
    @Test
    fun `DownloadProgressInfo getRemainingBytes returns -1 for unknown size`() {
        val progress = DownloadProgressInfo(bytesDownloaded = 500, totalBytes = -1)
        assertEquals(-1, progress.getRemainingBytes())
    }

    /**
     * Tests DownloadProgressInfo getFormattedTimeRemaining() edge cases.
     */
    @Test
    fun `DownloadProgressInfo getFormattedTimeRemaining handles edge values`() {
        val unknown = DownloadProgressInfo(estimatedTimeRemaining = -1)
        assertEquals("Unknown", unknown.getFormattedTimeRemaining())
        
        val zero = DownloadProgressInfo(estimatedTimeRemaining = 0)
        assertEquals("0 sec", zero.getFormattedTimeRemaining())
        
        val largeTime = DownloadProgressInfo(estimatedTimeRemaining = 7200000) // 2 hours
        assertEquals("2 hr 0 min", largeTime.getFormattedTimeRemaining())
    }

    /**
     * Tests DownloadTiming with default values (only createdAt set).
     */
    @Test
    fun `DownloadTiming defaults correctly`() {
        val timing = DownloadTiming()
        
        assertNotNull(timing.createdAt)
        assertNull(timing.startedAt)
        assertNull(timing.completedAt)
        assertEquals(0, timing.pausedDuration)
        assertEquals(0, timing.retryCount)
        assertNull(timing.lastRetryAt)
    }

    /**
     * Tests DownloadTiming isInProgress() when never started.
     */
    @Test
    fun `DownloadTiming isInProgress returns false when never started`() {
        val timing = DownloadTiming()
        assertFalse(timing.isInProgress())
    }

    /**
     * Tests DownloadTiming getTotalElapsedTime() when not started uses createdAt.
     */
    @Test
    fun `DownloadTiming getTotalElapsedTime uses createdAt when not started`() {
        val createdAt = System.currentTimeMillis() - 10000
        val timing = DownloadTiming(createdAt = createdAt)
        
        val elapsed = timing.getTotalElapsedTime()
        assertTrue(elapsed >= 9900 && elapsed <= 10100)
    }

    /**
     * Tests DownloadTiming getActiveDownloadTime() with paused duration.
     */
    @Test
    fun `DownloadTiming getActiveDownloadTime correctly subtracts paused duration`() {
        val startedAt = System.currentTimeMillis() - 20000
        val completedAt = System.currentTimeMillis()
        val timing = DownloadTiming(
            startedAt = startedAt,
            completedAt = completedAt,
            pausedDuration = 5000
        )
        
        val totalElapsed = timing.getTotalElapsedTime()
        val activeTime = timing.getActiveDownloadTime()
        
        // Active time should be about 5 seconds less than total
        assertTrue(activeTime < totalElapsed)
        assertTrue(totalElapsed - activeTime >= 4900 && totalElapsed - activeTime <= 5100)
    }

    /**
     * Tests DownloadTiming with multiple retries.
     */
    @Test
    fun `DownloadTiming tracks retry count and last retry time`() {
        val lastRetryTime = System.currentTimeMillis()
        val timing = DownloadTiming(
            retryCount = 5,
            lastRetryAt = lastRetryTime
        )
        
        assertEquals(5, timing.retryCount)
        assertEquals(lastRetryTime, timing.lastRetryAt)
    }

    /**
     * Tests ServerInfo with all properties set.
     */
    @Test
    fun `ServerInfo stores all properties correctly`() {
        val serverInfo = ServerInfo(
            serverName = "Apache/2.4.41",
            serverVersion = "2.4.41",
            supportsRangeRequests = true,
            supportsCompression = true,
            maxConnectionsAllowed = 6,
            requiresAuthentication = true,
            redirectUrl = "https://redirect.example.com",
            responseHeaders = mapOf("Content-Type" to "application/zip")
        )
        
        assertEquals("Apache/2.4.41", serverInfo.serverName)
        assertEquals("2.4.41", serverInfo.serverVersion)
        assertTrue(serverInfo.supportsRangeRequests)
        assertTrue(serverInfo.supportsCompression)
        assertEquals(6, serverInfo.maxConnectionsAllowed)
        assertTrue(serverInfo.requiresAuthentication)
        assertEquals("https://redirect.example.com", serverInfo.redirectUrl)
        assertEquals(1, serverInfo.responseHeaders.size)
    }

    /**
     * Tests ServerInfo with default values (all false/null).
     */
    @Test
    fun `ServerInfo defaults correctly`() {
        val serverInfo = ServerInfo()
        
        assertNull(serverInfo.serverName)
        assertNull(serverInfo.serverVersion)
        assertFalse(serverInfo.supportsRangeRequests)
        assertFalse(serverInfo.supportsCompression)
        assertEquals(1, serverInfo.maxConnectionsAllowed)
        assertFalse(serverInfo.requiresAuthentication)
        assertNull(serverInfo.redirectUrl)
        assertTrue(serverInfo.responseHeaders.isEmpty())
    }

    /**
     * Tests ServerInfo supportsParallelDownloads() with various connection limits.
     */
    @Test
    fun `ServerInfo supportsParallelDownloads with various limits`() {
        val single = ServerInfo(maxConnectionsAllowed = 1)
        assertFalse(single.supportsParallelDownloads())
        
        val dual = ServerInfo(maxConnectionsAllowed = 2)
        assertTrue(dual.supportsParallelDownloads())
        
        val multi = ServerInfo(maxConnectionsAllowed = 8)
        assertTrue(multi.supportsParallelDownloads())
    }

    /**
     * Tests StateTransition getStateDuration() with null previous transition.
     */
    @Test
    fun `StateTransition getStateDuration returns 0 for null previous`() {
        val transition = StateTransition(
            from = DownloadState.Idle("test"),
            to = DownloadState.Connecting("test", "url"),
            timestamp = 5000
        )
        
        assertEquals(0, transition.getStateDuration(null))
    }

    /**
     * Tests StateTransition with reason provided.
     */
    @Test
    fun `StateTransition stores reason correctly`() {
        val transition = StateTransition(
            from = DownloadState.Downloading("test", DownloadProgress(500, 1000), DownloadSpeed(0, 0, 0)),
            to = DownloadState.Failed("test", DownloadError.NetworkError("Error"), false),
            timestamp = 1000,
            reason = "Network connection lost"
        )
        
        assertEquals("Network connection lost", transition.reason)
    }

    /**
     * Tests StateTransition with null reason (default).
     */
    @Test
    fun `StateTransition defaults reason to null`() {
        val transition = StateTransition(
            from = DownloadState.Idle("test"),
            to = DownloadState.Validating("test", "Checking")
        )
        
        assertNull(transition.reason)
    }

    /**
     * Tests DownloadValidation with all checks passed.
     */
    @Test
    fun `DownloadValidation with all passed checks isValid true`() {
        val checks = listOf(
            ValidationCheck(ValidationCheckType.URL_VALIDITY, true, "Valid"),
            ValidationCheck(ValidationCheckType.DISK_SPACE, true, "Sufficient space"),
            ValidationCheck(ValidationCheckType.NETWORK_AVAILABILITY, true, "Network available")
        )
        val validation = DownloadValidation(true, checks)
        
        assertTrue(validation.isValid)
        assertEquals(0, validation.getFailedChecks().size)
        assertEquals(3, validation.getPassedChecks().size)
    }

    /**
     * Tests DownloadValidation with mixed severities.
     */
    @Test
    fun `DownloadValidation handles mixed severity checks`() {
        val checks = listOf(
            ValidationCheck(ValidationCheckType.URL_VALIDITY, true, "Valid"),
            ValidationCheck(ValidationCheckType.DISK_SPACE, false, "Low space", ValidationSeverity.WARNING),
            ValidationCheck(ValidationCheckType.NETWORK_AVAILABILITY, true, "Network OK")
        )
        val validation = DownloadValidation(true, checks) // Valid because only WARNING failed
        
        assertTrue(validation.isValid)
        assertEquals(1, validation.getFailedChecks().size)
        assertEquals(2, validation.getPassedChecks().size)
    }

    /**
     * Tests ValidationCheck with INFO severity.
     */
    @Test
    fun `ValidationCheck handles INFO severity`() {
        val info = ValidationCheck(
            ValidationCheckType.URL_REACHABILITY,
            true,
            "Server is reachable",
            ValidationSeverity.INFO
        )
        
        assertFalse(info.isBlocking())
        assertFalse(info.isWarning())
        assertEquals(ValidationSeverity.INFO, info.severity)
    }

    /**
     * Tests ValidationCheck isBlocking() with passed check.
     */
    @Test
    fun `ValidationCheck isBlocking returns false for passed checks`() {
        val passed = ValidationCheck(
            ValidationCheckType.DISK_SPACE,
            true,
            "OK",
            ValidationSeverity.ERROR
        )
        
        assertFalse(passed.isBlocking())
    }

    /**
     * Tests all 11 ValidationCheckType values exist.
     */
    @Test
    fun `ValidationCheckType enum has all 11 values`() {
        val types = ValidationCheckType.entries
        
        assertEquals(11, types.size)
        assertTrue(types.contains(ValidationCheckType.URL_VALIDITY))
        assertTrue(types.contains(ValidationCheckType.URL_REACHABILITY))
        assertTrue(types.contains(ValidationCheckType.DISK_SPACE))
        assertTrue(types.contains(ValidationCheckType.NETWORK_AVAILABILITY))
        assertTrue(types.contains(ValidationCheckType.WIFI_REQUIREMENT))
        assertTrue(types.contains(ValidationCheckType.METERED_CONNECTION))
        assertTrue(types.contains(ValidationCheckType.DESTINATION_WRITABLE))
        assertTrue(types.contains(ValidationCheckType.FILE_EXISTS))
        assertTrue(types.contains(ValidationCheckType.AUTHENTICATION))
        assertTrue(types.contains(ValidationCheckType.SERVER_SUPPORT))
        assertTrue(types.contains(ValidationCheckType.FILE_SIZE))
    }

    /**
     * Tests all 3 ValidationSeverity values exist.
     */
    @Test
    fun `ValidationSeverity enum has all 3 values`() {
        val severities = ValidationSeverity.entries
        
        assertEquals(3, severities.size)
        assertTrue(severities.contains(ValidationSeverity.INFO))
        assertTrue(severities.contains(ValidationSeverity.WARNING))
        assertTrue(severities.contains(ValidationSeverity.ERROR))
    }

    /**
     * Tests DownloadInfo with state history populated.
     */
    @Test
    fun `DownloadInfo stores state history correctly`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Downloading(
            "test-id",
            DownloadProgress(500, 1000),
            DownloadSpeed(1024, 512, 5000)
        )
        
        val history = listOf(
            StateTransition(DownloadState.Idle("test-id"), DownloadState.Validating("test-id", "Checking"), 1000),
            StateTransition(DownloadState.Validating("test-id", "Checking"), DownloadState.Connecting("test-id", "url"), 2000)
        )

        val info = DownloadInfo(
            id = "test-id",
            request = request,
            state = state,
            file = file,
            metadata = DownloadMetadata(),
            progress = DownloadProgressInfo(),
            timing = DownloadTiming(),
            serverInfo = ServerInfo(),
            history = history
        )
        
        assertEquals(2, info.history.size)
        assertEquals(history, info.history)
    }

    /**
     * Tests DownloadInfo with empty state history (default).
     */
    @Test
    fun `DownloadInfo defaults history to empty list`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Idle("test-id")

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
        
        assertTrue(info.history.isEmpty())
    }

    /**
     * Tests DownloadProgressInfo with chunking information.
     */
    @Test
    fun `DownloadProgressInfo tracks chunk progress`() {
        val progress = DownloadProgressInfo(
            bytesDownloaded = 5000000,
            totalBytes = 10000000,
            chunksDownloaded = 3,
            totalChunks = 6
        )
        
        assertEquals(3, progress.chunksDownloaded)
        assertEquals(6, progress.totalChunks)
    }

    /**
     * Tests DownloadTiming getFormattedElapsedTime() for various durations.
     */
    @Test
    fun `DownloadTiming getFormattedElapsedTime formats correctly for all ranges`() {
        // Short duration
        val short = DownloadTiming(startedAt = System.currentTimeMillis() - 30000)
        val shortFormatted = short.getFormattedElapsedTime()
        assertTrue(shortFormatted.contains("sec"))
        
        // Medium duration
        val medium = DownloadTiming(startedAt = System.currentTimeMillis() - 120000)
        val mediumFormatted = medium.getFormattedElapsedTime()
        assertTrue(mediumFormatted.contains("min"))
        
        // Long duration
        val long = DownloadTiming(startedAt = System.currentTimeMillis() - 7200000)
        val longFormatted = long.getFormattedElapsedTime()
        assertTrue(longFormatted.contains("hr"))
    }

    /**
     * Tests canResume() returns false for wrong state types.
     */
    @Test
    fun `canResume returns false for idle state`() {
        val file = tempFolder.newFile("test.zip")
        file.writeText("content")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        val state = DownloadState.Idle("test-id")

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

        assertFalse(info.canResume())
    }

    /**
     * Tests DownloadMetadata getFormattedContentLength() for various sizes.
     */
    @Test
    fun `DownloadMetadata getFormattedContentLength handles various sizes`() {
        val small = DownloadMetadata(contentLength = 512)
        assertEquals("512 B", small.getFormattedContentLength())
        
        val medium = DownloadMetadata(contentLength = 5 * 1024 * 1024)
        assertEquals("5.00 MB", medium.getFormattedContentLength())
        
        val large = DownloadMetadata(contentLength = 2L * 1024 * 1024 * 1024)
        assertEquals("2.00 GB", large.getFormattedContentLength())
    }

    /**
     * Tests DownloadProgressInfo isSizeKnown() with zero (edge case).
     */
    @Test
    fun `DownloadProgressInfo isSizeKnown returns false for zero total`() {
        val progress = DownloadProgressInfo(totalBytes = 0)
        assertFalse(progress.isSizeKnown())
    }

    /**
     * Tests ValidationCheck default severity is ERROR.
     */
    @Test
    fun `ValidationCheck defaults severity to ERROR`() {
        val check = ValidationCheck(
            ValidationCheckType.URL_VALIDITY,
            true,
            "Valid URL"
        )
        
        assertEquals(ValidationSeverity.ERROR, check.severity)
    }

    /**
     * Tests DownloadValidation hasFailedCheck() for multiple check types.
     */
    @Test
    fun `DownloadValidation hasFailedCheck works with multiple failures`() {
        val checks = listOf(
            ValidationCheck(ValidationCheckType.URL_VALIDITY, false, "Invalid URL"),
            ValidationCheck(ValidationCheckType.DISK_SPACE, false, "No space"),
            ValidationCheck(ValidationCheckType.NETWORK_AVAILABILITY, true, "Network OK")
        )
        val validation = DownloadValidation(false, checks)
        
        assertTrue(validation.hasFailedCheck(ValidationCheckType.URL_VALIDITY))
        assertTrue(validation.hasFailedCheck(ValidationCheckType.DISK_SPACE))
        assertFalse(validation.hasFailedCheck(ValidationCheckType.NETWORK_AVAILABILITY))
        assertFalse(validation.hasFailedCheck(ValidationCheckType.FILE_SIZE))
    }

    /**
     * Tests StateTransition timestamp defaults to current time.
     */
    @Test
    fun `StateTransition uses current time as default timestamp`() {
        val before = System.currentTimeMillis()
        val transition = StateTransition(
            from = DownloadState.Idle("test"),
            to = DownloadState.Validating("test", "Checking")
        )
        val after = System.currentTimeMillis()
        
        assertTrue(transition.timestamp >= before)
        assertTrue(transition.timestamp <= after)
    }

    /**
     * Tests DownloadInfo isActive() and isTerminal() are mutually exclusive.
     */
    @Test
    fun `DownloadInfo isActive and isTerminal are mutually exclusive`() {
        val file = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file).build()
        
        // Create info for each state and verify mutual exclusivity
        val states = listOf(
            DownloadState.Idle("test-id"),
            DownloadState.Validating("test-id", "Checking"),
            DownloadState.Connecting("test-id", "url"),
            DownloadState.Downloading("test-id", DownloadProgress(500, 1000), DownloadSpeed(1024, 512, 5000)),
            DownloadState.Paused("test-id", DownloadProgress(500, 1000)),
            DownloadState.Completed("test-id", file, 1000, 10000, 100),
            DownloadState.Failed("test-id", DownloadError.NetworkError("Error")),
            DownloadState.Cancelled("test-id", file, 500)
        )
        
        states.forEach { state ->
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
            
            // A state cannot be both active and terminal
            val bothTrue = info.isActive() && info.isTerminal()
            assertFalse("State $state should not be both active and terminal", bothTrue)
        }
    }

    /**
     * Tests DownloadInfo with all components populated.
     */
    @Test
    fun `DownloadInfo aggregates all components correctly`() {
        val file = tempFolder.newFile("complete.zip")
        file.writeText("content")
        val request = DownloadRequest.Builder("https://example.com/file.zip", file)
            .setChecksum("abc123", "SHA-256")
            .build()
        
        val state = DownloadState.Downloading(
            "full-test",
            DownloadProgress(500, 1000),
            DownloadSpeed(1024, 512, 5000)
        )
        
        val metadata = DownloadMetadata(
            mimeType = "application/zip",
            contentLength = 1000,
            fileName = "file.zip"
        )
        
        val progress = DownloadProgressInfo(
            bytesDownloaded = 500,
            totalBytes = 1000,
            percentage = 50f,
            currentSpeed = 1024,
            averageSpeed = 512
        )
        
        val timing = DownloadTiming(
            startedAt = System.currentTimeMillis() - 10000,
            retryCount = 2
        )
        
        val serverInfo = ServerInfo(
            serverName = "nginx",
            supportsRangeRequests = true
        )
        
        val history = listOf(
            StateTransition(DownloadState.Idle("full-test"), state, System.currentTimeMillis())
        )
        
        val info = DownloadInfo(
            id = "full-test",
            request = request,
            state = state,
            file = file,
            metadata = metadata,
            progress = progress,
            timing = timing,
            serverInfo = serverInfo,
            history = history
        )
        
        assertEquals("full-test", info.id)
        assertEquals(request, info.request)
        assertEquals(state, info.state)
        assertEquals(file, info.file)
        assertEquals(metadata, info.metadata)
        assertEquals(progress, info.progress)
        assertEquals(timing, info.timing)
        assertEquals(serverInfo, info.serverInfo)
        assertEquals(1, info.history.size)
        assertTrue(info.isActive())
    }
}
