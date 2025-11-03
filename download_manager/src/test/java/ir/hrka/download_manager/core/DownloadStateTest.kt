package ir.hrka.download_manager.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Comprehensive tests for DownloadState and related classes.
 */
class DownloadStateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `Idle state has correct properties`() {
        val state = DownloadState.Idle("test-id")

        assertEquals("test-id", state.downloadId)
    }

    @Test
    fun `Validating state has correct properties`() {
        val state = DownloadState.Validating("test-id", "Checking disk space")

        assertEquals("test-id", state.downloadId)
        assertEquals("Checking disk space", state.validationStage)
    }

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

    @Test
    fun `Paused state calculates paused duration`() {
        val progress = DownloadProgress(500, 1000)
        val pausedAt = System.currentTimeMillis() - 5000
        val state = DownloadState.Paused("test-id", progress, pausedAt)

        val duration = state.getPausedDuration()
        assertTrue(duration >= 4900 && duration <= 5100) // Allow 100ms variance
    }

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

    @Test
    fun `formatBytes formats bytes correctly`() {
        assertEquals("100 B", DownloadState.formatBytes(100))
        assertEquals("1.00 KB", DownloadState.formatBytes(1024))
        assertEquals("1.00 MB", DownloadState.formatBytes(1024 * 1024))
        assertEquals("1.00 GB", DownloadState.formatBytes(1024 * 1024 * 1024))
    }

    @Test
    fun `DownloadProgress calculates percentage correctly`() {
        val progress = DownloadProgress(250, 1000)

        assertEquals(25f, progress.getPercentage(), 0.01f)
    }

    @Test
    fun `DownloadProgress returns -1 for unknown size`() {
        val progress = DownloadProgress(250, -1)

        assertEquals(-1f, progress.getPercentage(), 0.01f)
    }

    @Test
    fun `DownloadProgress calculates elapsed time`() {
        val startTime = System.currentTimeMillis() - 5000
        val progress = DownloadProgress(250, 1000, startTime)

        val elapsed = progress.getElapsedTime()
        assertTrue(elapsed >= 4900 && elapsed <= 5100)
    }

    @Test
    fun `DownloadProgress isTotalSizeKnown returns correct values`() {
        val known = DownloadProgress(250, 1000)
        val unknown = DownloadProgress(250, -1)

        assertTrue(known.isTotalSizeKnown())
        assertFalse(unknown.isTotalSizeKnown())
    }

    @Test
    fun `DownloadProgress getRemainingBytes calculates correctly`() {
        val progress = DownloadProgress(250, 1000)

        assertEquals(750, progress.getRemainingBytes())
    }

    @Test
    fun `DownloadSpeed getCurrentSpeedFormatted returns correct string`() {
        val speed = DownloadSpeed(102400, 51200, 10000)

        assertEquals("100.00 KB/s", speed.getCurrentSpeedFormatted())
    }

    @Test
    fun `DownloadSpeed getAverageSpeedFormatted returns correct string`() {
        val speed = DownloadSpeed(102400, 51200, 10000)

        assertEquals("50.00 KB/s", speed.getAverageSpeedFormatted())
    }

    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted returns correct string for seconds`() {
        val speed = DownloadSpeed(102400, 51200, 45000)

        assertEquals("45 sec", speed.getEstimatedTimeFormatted())
    }

    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted returns correct string for minutes`() {
        val speed = DownloadSpeed(102400, 51200, 90000) // 90 seconds

        assertEquals("1 min 30 sec", speed.getEstimatedTimeFormatted())
    }

    @Test
    fun `DownloadSpeed getEstimatedTimeFormatted returns correct string for hours`() {
        val speed = DownloadSpeed(102400, 51200, 3660000) // 1 hour 1 minute

        assertEquals("1 hr 1 min", speed.getEstimatedTimeFormatted())
    }

    @Test
    fun `DownloadSpeed isStalled returns true for very low speed`() {
        val speed = DownloadSpeed(50, 100, 10000)

        assertTrue(speed.isStalled())
    }

    @Test
    fun `DownloadSpeed isStalled returns false for normal speed`() {
        val speed = DownloadSpeed(102400, 51200, 10000)

        assertFalse(speed.isStalled())
    }

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

    @Test
    fun `FileSystemError has correct properties`() {
        val error = DownloadError.FileSystemError(
            message = "Disk full",
            errorType = FileSystemErrorType.DISK_FULL
        )

        assertEquals("Disk full", error.message)
        assertEquals(FileSystemErrorType.DISK_FULL, error.errorType)
    }

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

    @Test
    fun `UnsupportedOperationError has correct properties`() {
        val error = DownloadError.UnsupportedOperationError(
            message = "Resume not supported",
            operation = "resume"
        )

        assertEquals("Resume not supported", error.message)
        assertEquals("resume", error.operation)
    }

    @Test
    fun `TimeoutError has correct properties`() {
        val error = DownloadError.TimeoutError(
            message = "Connection timeout",
            timeoutType = TimeoutType.CONNECT_TIMEOUT
        )

        assertEquals("Connection timeout", error.message)
        assertEquals(TimeoutType.CONNECT_TIMEOUT, error.timeoutType)
    }

    @Test
    fun `AuthenticationError has correct properties`() {
        val error = DownloadError.AuthenticationError(
            message = "Unauthorized",
            statusCode = 401
        )

        assertEquals("Unauthorized", error.message)
        assertEquals(401, error.statusCode)
    }

    @Test
    fun `UnknownError has correct properties`() {
        val error = DownloadError.UnknownError(
            message = "Unknown error occurred"
        )

        assertEquals("Unknown error occurred", error.message)
        assertNull(error.cause)
    }

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

    @Test
    fun `TimeoutType enum has all values`() {
        val types = TimeoutType.values()

        assertEquals(3, types.size)
        assertTrue(types.contains(TimeoutType.CONNECT_TIMEOUT))
        assertTrue(types.contains(TimeoutType.READ_TIMEOUT))
        assertTrue(types.contains(TimeoutType.WRITE_TIMEOUT))
    }
}