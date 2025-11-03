package ir.hrka.download_manager.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Comprehensive tests for DownloadInfo and related data classes.
 */
class DownloadInfoTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    @Test
    fun `DownloadMetadata hasContentLength returns correct values`() {
        val withLength = DownloadMetadata(contentLength = 1024)
        val withoutLength = DownloadMetadata(contentLength = -1)

        assertTrue(withLength.hasContentLength())
        assertFalse(withoutLength.hasContentLength())
    }

    @Test
    fun `DownloadMetadata getFormattedContentLength returns correct string`() {
        val metadata = DownloadMetadata(contentLength = 1024 * 1024)

        assertEquals("1.00 MB", metadata.getFormattedContentLength())
    }

    @Test
    fun `DownloadMetadata getFormattedContentLength returns Unknown for no length`() {
        val metadata = DownloadMetadata(contentLength = -1)

        assertEquals("Unknown", metadata.getFormattedContentLength())
    }

    @Test
    fun `DownloadProgressInfo isSizeKnown returns correct values`() {
        val known = DownloadProgressInfo(totalBytes = 1000)
        val unknown = DownloadProgressInfo(totalBytes = -1)

        assertTrue(known.isSizeKnown())
        assertFalse(unknown.isSizeKnown())
    }

    @Test
    fun `DownloadProgressInfo getRemainingBytes calculates correctly`() {
        val progress = DownloadProgressInfo(bytesDownloaded = 250, totalBytes = 1000)

        assertEquals(750, progress.getRemainingBytes())
    }

    @Test
    fun `DownloadProgressInfo getRemainingBytes returns -1 for unknown size`() {
        val progress = DownloadProgressInfo(bytesDownloaded = 250, totalBytes = -1)

        assertEquals(-1, progress.getRemainingBytes())
    }

    @Test
    fun `DownloadProgressInfo getFormattedCurrentSpeed returns correct string`() {
        val progress = DownloadProgressInfo(currentSpeed = 102400)

        assertEquals("100.00 KB/s", progress.getFormattedCurrentSpeed())
    }

    @Test
    fun `DownloadProgressInfo getFormattedAverageSpeed returns correct string`() {
        val progress = DownloadProgressInfo(averageSpeed = 51200)

        assertEquals("50.00 KB/s", progress.getFormattedAverageSpeed())
    }

    @Test
    fun `DownloadProgressInfo getFormattedTimeRemaining returns correct string`() {
        val progress = DownloadProgressInfo(estimatedTimeRemaining = 90000)

        assertEquals("1 min", progress.getFormattedTimeRemaining())
    }

    @Test
    fun `DownloadProgressInfo getFormattedTimeRemaining returns Unknown for negative time`() {
        val progress = DownloadProgressInfo(estimatedTimeRemaining = -1)

        assertEquals("Unknown", progress.getFormattedTimeRemaining())
    }

    @Test
    fun `DownloadTiming getTotalElapsedTime calculates correctly`() {
        val startTime = System.currentTimeMillis() - 10000
        val timing = DownloadTiming(startedAt = startTime)

        val elapsed = timing.getTotalElapsedTime()
        assertTrue(elapsed >= 9900 && elapsed <= 10100)
    }

    @Test
    fun `DownloadTiming getActiveDownloadTime excludes paused duration`() {
        val startTime = System.currentTimeMillis() - 10000
        val timing = DownloadTiming(startedAt = startTime, pausedDuration = 3000)

        val active = timing.getActiveDownloadTime()
        assertTrue(active >= 6900 && active <= 7100)
    }

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

    @Test
    fun `ServerInfo canResume returns correct value`() {
        val canResume = ServerInfo(supportsRangeRequests = true)
        val cannotResume = ServerInfo(supportsRangeRequests = false)

        assertTrue(canResume.canResume())
        assertFalse(cannotResume.canResume())
    }

    @Test
    fun `ServerInfo supportsParallelDownloads returns correct value`() {
        val supports = ServerInfo(maxConnectionsAllowed = 4)
        val doesNotSupport = ServerInfo(maxConnectionsAllowed = 1)

        assertTrue(supports.supportsParallelDownloads())
        assertFalse(doesNotSupport.supportsParallelDownloads())
    }

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

