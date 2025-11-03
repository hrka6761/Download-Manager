package ir.hrka.download_manager.core

import ir.hrka.download_manager.core.utilities.DownloadError
import ir.hrka.download_manager.core.utilities.DownloadInfo
import ir.hrka.download_manager.core.utilities.DownloadMetadata
import ir.hrka.download_manager.core.utilities.DownloadProgress
import ir.hrka.download_manager.core.utilities.DownloadProgressInfo
import ir.hrka.download_manager.core.utilities.DownloadRequest
import ir.hrka.download_manager.core.utilities.DownloadSpeed
import ir.hrka.download_manager.core.utilities.DownloadState
import ir.hrka.download_manager.core.utilities.DownloadTiming
import ir.hrka.download_manager.core.utilities.DownloadValidation
import ir.hrka.download_manager.core.utilities.ServerInfo
import ir.hrka.download_manager.core.utilities.StateTransition
import ir.hrka.download_manager.core.utilities.ValidationCheck
import ir.hrka.download_manager.core.utilities.ValidationCheckType
import ir.hrka.download_manager.core.utilities.ValidationSeverity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production-ready implementation of [Downloader] using OkHttp.
 *
 * @property client Optional OkHttpClient (will create default if not provided)
 * @property bufferSize Size of buffer for reading/writing (default 64KB)
 * @property progressUpdateInterval Minimum interval between progress updates (default 200ms)
 */
class OkHttpDownloader(
    client: OkHttpClient? = null,
    private val bufferSize: Int = 64 * 1024, // 64KB
    private val progressUpdateInterval: Long = 200 // milliseconds
) : Downloader {

    private val client: OkHttpClient = client ?: createDefaultClient()

    // Thread-safe storage for download contexts
    private val downloads = ConcurrentHashMap<String, DownloadContext>()

    /**
     * Starts a download and returns a Flow of download states.
     *
     * This is a cold Flow - the download starts when the Flow is collected.
     * The Flow emits state updates and completes when download finishes.
     */
    override fun download(request: DownloadRequest): Flow<DownloadState> = flow {
        val downloadId = request.id

        // Check for duplicate ID - prevent silent overwrite
        if (downloads.containsKey(downloadId)) {
            val error = DownloadError.UnknownError(
                "Download with ID '$downloadId' already exists. Use a unique ID or cancel the existing download first."
            )
            emit(DownloadState.Failed(downloadId, error, canRetry = false))
            return@flow
        }

        val context = DownloadContext(request)

        // Register download
        downloads[downloadId] = context

        try {
            // Emit initial state
            emit(DownloadState.Idle(downloadId))

            // Validation phase
            emit(DownloadState.Validating(downloadId, "Checking prerequisites"))
            val validation = validateInternal(request)
            if (!validation.isValid) {
                val errors = validation.getFailedChecks().joinToString(", ") { it.message }
                throw IllegalArgumentException("Validation failed: $errors")
            }

            // Prepare file
            prepareDestination(request)

            // Execute download with retry logic
            var attemptNumber = 0

            while (attemptNumber < request.maxRetries) {
                attemptNumber++

                try {
                    // Execute single attempt
                    executeDownload(request, attemptNumber).collect { state ->
                        context.currentState = state
                        emit(state)

                        // Check if completed successfully
                        if (state is DownloadState.Completed) {
                            return@collect
                        }
                    }

                    // If we reach here, download completed
                    break

                } catch (e: CancellationException) {
                    // Propagate cancellation
                    throw e
                } catch (e: Exception) {
                    val error = mapExceptionToError(e)

                    if (attemptNumber < request.maxRetries) {
                        // Wait before retry
                        val delay = request.retryDelay * attemptNumber
                        delay(delay)
                    } else {
                        // Final attempt failed
                        val failedState = DownloadState.Failed(
                            downloadId = downloadId,
                            error = error,
                            canRetry = false,
                            attemptNumber = attemptNumber
                        )
                        context.currentState = failedState
                        emit(failedState)
                    }
                }
            }

        } catch (e: CancellationException) {
            // Handle cancellation
            val cancelledState = DownloadState.Cancelled(
                downloadId = downloadId,
                partialFile = if (request.destination.exists()) request.destination else null,
                downloadedBytes = request.destination.length()
            )
            context.currentState = cancelledState
            emit(cancelledState)
            throw e
        } catch (e: Exception) {
            // Handle unexpected errors
            val error = mapExceptionToError(e)
            val failedState = DownloadState.Failed(
                downloadId = downloadId,
                error = error,
                canRetry = false,
                attemptNumber = 1
            )
            context.currentState = failedState
            emit(failedState)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Executes a single download attempt.
     */
    private fun executeDownload(
        request: DownloadRequest,
        attemptNumber: Int
    ): Flow<DownloadState> = flow {
        val downloadId = request.id
        val destination = request.destination

        // Calculate starting point for resume
        val existingBytes = if (request.resumeIfPossible && destination.exists()) {
            destination.length()
        } else 0

        // Emit connecting state
        emit(
            DownloadState.Connecting(
                downloadId = downloadId,
                url = request.url,
                attemptNumber = attemptNumber
            )
        )

        // Build HTTP request
        val httpRequest = buildRequest(request, existingBytes)

        // Execute request asynchronously
        val response = executeAsync(httpRequest)

        response.use { resp ->
            // Check response status
            if (!resp.isSuccessful) {
                throw createHttpException(resp.code, resp.message)
            }

            // Get server info
            val serverInfo = extractServerInfo(resp)
            val context =
                downloads[downloadId] ?: throw IllegalStateException("Download context lost")
            context.serverInfo = serverInfo

            // Get total size
            val totalBytes = if (existingBytes > 0) {
                // For resumed downloads, add existing bytes to content length
                val contentLength = resp.body.contentLength()
                if (contentLength > 0) existingBytes + contentLength else request.expectedSize
            } else
                resp.body.contentLength()


            // Download file
            resp.body.byteStream().use { input ->
                FileOutputStream(destination, existingBytes > 0).use { output ->
                    val startTime = System.currentTimeMillis()
                    var downloadedBytes = existingBytes
                    var lastProgressUpdate = 0L
                    // Use ArrayDeque for efficient removal from front (O(1) vs O(n))
                    val speedSamples = ArrayDeque<Pair<Long, Long>>() // (bytes, timestamp)
                    val buffer = ByteArray(bufferSize)

                    while (currentCoroutineContext().isActive) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val currentTime = System.currentTimeMillis()

                        // Update progress periodically
                        if (currentTime - lastProgressUpdate >= progressUpdateInterval) {
                            // Add speed sample
                            speedSamples.addLast(Pair(downloadedBytes, currentTime))

                            // Keep only recent samples (last 5 seconds) - O(1) removal
                            while (speedSamples.size > 1 &&
                                currentTime - speedSamples.first().second > 5000
                            ) {
                                speedSamples.removeFirst()
                            }

                            // Calculate speeds
                            val speed = calculateSpeed(speedSamples)

                            val downloadingState = DownloadState.Downloading(
                                downloadId = downloadId,
                                progress = DownloadProgress(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    startTime = startTime,
                                    lastUpdateTime = currentTime
                                ),
                                speed = speed
                            )

                            emit(downloadingState)
                            lastProgressUpdate = currentTime
                        }

                        // Check if paused
                        context.pauseRequested.withLock {
                            if (context.isPaused) {
                                val pausedState = DownloadState.Paused(
                                    downloadId = downloadId,
                                    progress = DownloadProgress(
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                        startTime = startTime
                                    )
                                )
                                emit(pausedState)

                                // Wait until resumed or cancelled
                                while (context.isPaused && currentCoroutineContext().isActive) {
                                    delay(100)
                                }

                                if (!currentCoroutineContext().isActive) {
                                    throw CancellationException("Download cancelled while paused")
                                }
                            }
                        }
                    }

                    // Download completed
                    val downloadDuration = System.currentTimeMillis() - startTime
                    val averageSpeed = if (downloadDuration > 0) {
                        (downloadedBytes * 1000) / downloadDuration
                    } else 0L

                    // Verify if checksum provided
                    val verified =
                        if (request.checksum != null && request.checksumAlgorithm != null) {
                            verifyChecksum(destination, request.checksum, request.checksumAlgorithm)
                        } else false

                    val completedState = DownloadState.Completed(
                        downloadId = downloadId,
                        file = destination,
                        totalBytes = downloadedBytes,
                        downloadDuration = downloadDuration,
                        averageSpeed = averageSpeed,
                        verified = verified
                    )

                    emit(completedState)
                }
            }
        }
    }

    override suspend fun getState(downloadId: String): DownloadState? =
        downloads[downloadId]?.currentState

    override suspend fun getInfo(downloadId: String): DownloadInfo? {
        val context = downloads[downloadId] ?: return null
        val state = context.currentState

        return DownloadInfo(
            id = downloadId,
            request = context.request,
            state = state,
            file = context.request.destination,
            metadata = context.metadata,
            progress = DownloadProgressInfo(
                bytesDownloaded = when (state) {
                    is DownloadState.Downloading -> state.progress.downloadedBytes
                    is DownloadState.Paused -> state.progress.downloadedBytes
                    is DownloadState.Completed -> state.totalBytes
                    else -> 0L
                },
                totalBytes = context.metadata.contentLength,
                percentage = when (state) {
                    is DownloadState.Downloading -> state.getProgressPercentage()
                    is DownloadState.Completed -> 100f
                    else -> 0f
                },
                currentSpeed = when (state) {
                    is DownloadState.Downloading -> state.speed.currentBytesPerSecond
                    else -> 0L
                },
                averageSpeed = when (state) {
                    is DownloadState.Downloading -> state.speed.averageBytesPerSecond
                    is DownloadState.Completed -> state.averageSpeed
                    else -> 0L
                }
            ),
            timing = context.timing,
            serverInfo = context.serverInfo,
            history = context.stateHistory
        )
    }

    override suspend fun pause(downloadId: String): Result<Unit> {
        val context = downloads[downloadId] ?: return Result.failure(
            IllegalArgumentException("Download not found: $downloadId")
        )

        if (context.currentState !is DownloadState.Downloading) {
            return Result.failure(
                IllegalStateException("Download is not active: $downloadId")
            )
        }

        if (!context.serverInfo.supportsRangeRequests) {
            return Result.failure(
                UnsupportedOperationException("Server does not support pause/resume")
            )
        }

        context.pauseRequested.withLock {
            context.isPaused = true
        }

        return Result.success(Unit)
    }

    override fun resume(downloadId: String): Flow<DownloadState> {
        val context = downloads[downloadId]

        if (context == null) {
            return flow {
                emit(
                    DownloadState.Failed(
                        downloadId = downloadId,
                        error = DownloadError.UnknownError("Download not found: $downloadId"),
                        canRetry = false
                    )
                )
            }
        }

        if (context.currentState !is DownloadState.Paused) {
            return flow {
                emit(
                    DownloadState.Failed(
                        downloadId = downloadId,
                        error = DownloadError.UnsupportedOperationError(
                            message = "Download is not paused",
                            operation = "resume"
                        ),
                        canRetry = false
                    )
                )
            }
        }

        // Unpause
        context.isPaused = false

        // Continue with existing request
        return download(context.request)
    }

    override suspend fun cancel(downloadId: String): Result<Unit> {
        val context = downloads[downloadId] ?: return Result.failure(
            IllegalArgumentException("Download not found: $downloadId")
        )

        context.currentCall?.cancel()
        downloads.remove(downloadId)

        return Result.success(Unit)
    }

    override suspend fun cancelAll(): Result<List<String>> {
        val cancelledIds = downloads.keys.toList()

        downloads.values.forEach { context ->
            context.currentCall?.cancel()
        }

        downloads.clear()

        return Result.success(cancelledIds)
    }

    override suspend fun canPause(downloadId: String): Boolean {
        val context = downloads[downloadId] ?: return false
        return context.currentState is DownloadState.Downloading &&
                context.serverInfo.supportsRangeRequests
    }

    override suspend fun canResume(downloadId: String): Boolean {
        val context = downloads[downloadId] ?: return false
        return context.currentState is DownloadState.Paused &&
                context.serverInfo.supportsRangeRequests &&
                context.request.destination.exists()
    }

    override suspend fun validate(request: DownloadRequest): Result<DownloadValidation> {
        return Result.success(validateInternal(request))
    }

    override suspend fun verify(downloadId: String): Result<Boolean> {
        val context = downloads[downloadId] ?: return Result.failure(
            IllegalArgumentException("Download not found: $downloadId")
        )

        if (context.currentState !is DownloadState.Completed) {
            return Result.failure(
                IllegalStateException("Download is not completed")
            )
        }

        val checksum = context.request.checksum
        val algorithm = context.request.checksumAlgorithm

        if (checksum == null || algorithm == null) {
            return Result.success(true) // No checksum to verify
        }

        return withContext(Dispatchers.IO) {
            try {
                val isValid = verifyChecksum(context.request.destination, checksum, algorithm)
                Result.success(isValid)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun retry(downloadId: String): Flow<DownloadState> {
        val context = downloads[downloadId]

        if (context == null) {
            return flow {
                emit(
                    DownloadState.Failed(
                        downloadId = downloadId,
                        error = DownloadError.UnknownError("Download not found: $downloadId"),
                        canRetry = false
                    )
                )
            }
        }

        if (context.currentState !is DownloadState.Failed) {
            return flow {
                emit(
                    DownloadState.Failed(
                        downloadId = downloadId,
                        error = DownloadError.UnsupportedOperationError(
                            message = "Download has not failed",
                            operation = "retry"
                        ),
                        canRetry = false
                    )
                )
            }
        }

        // Reset context and retry
        context.timing = context.timing.copy(
            retryCount = context.timing.retryCount + 1,
            lastRetryAt = System.currentTimeMillis()
        )

        return download(context.request)
    }

    override suspend fun getActiveDownloads(): List<String> {
        return downloads.filter { (_, context) ->
            context.currentState is DownloadState.Downloading ||
                    context.currentState is DownloadState.Connecting ||
                    context.currentState is DownloadState.Validating
        }.keys.toList()
    }

    override suspend fun clearDownload(downloadId: String): Result<Unit> {
        downloads.remove(downloadId)
        return Result.success(Unit)
    }

    // ==================== Private Helper Methods ====================

    /**
     * Creates default OkHttpClient with sensible defaults.
     */
    private fun createDefaultClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Builds HTTP request with headers and range support.
     */
    private fun buildRequest(request: DownloadRequest, existingBytes: Long): Request {
        val builder = Request.Builder().url(request.url)

        // Add custom headers
        request.headers.forEach { (key, value) ->
            builder.addHeader(key, value)
        }

        // Add range header for resume
        if (existingBytes > 0) {
            builder.addHeader("Range", "bytes=$existingBytes-")
        }

        return builder.build()
    }

    /**
     * Executes HTTP request asynchronously using suspendCancellableCoroutine.
     */
    private suspend fun executeAsync(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)

            // Store call for cancellation in matching download context
            downloads.values.forEach { context ->
                if (context.request.url == request.url.toString()) {
                    context.currentCall = call
                }
            }

            // Handle cancellation
            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }

    /**
     * Prepares destination file and directory.
     */
    private fun prepareDestination(request: DownloadRequest) {
        val destination = request.destination

        // Create parent directories
        destination.parentFile?.mkdirs()

        // Handle existing file
        if (destination.exists()) {
            if (request.overwriteExisting) {
                destination.delete()
            } else if (!request.resumeIfPossible) {
                throw IOException("File already exists: ${destination.absolutePath}")
            }
        }
    }

    /**
     * Extracts server information from response.
     */
    private fun extractServerInfo(response: Response): ServerInfo {
        val headers = response.headers

        return ServerInfo(
            serverName = headers["Server"],
            supportsRangeRequests = headers["Accept-Ranges"]?.equals(
                "bytes",
                ignoreCase = true
            ) == true,
            supportsCompression = headers["Content-Encoding"] != null,
            responseHeaders = headers.toMultimap().mapValues { it.value.joinToString(", ") }
        )
    }

    /**
     * Calculates current and average download speed.
     */
    private fun calculateSpeed(
        samples: Collection<Pair<Long, Long>>
    ): DownloadSpeed {
        if (samples.size < 2) {
            return DownloadSpeed(0, 0, -1)
        }

        // Convert to list for indexed access (samples are small, this is fine)
        val samplesList = samples as? List ?: samples.toList()

        // Current speed (last two samples)
        val lastSample = samplesList.last()
        val previousSample = samplesList[samplesList.size - 2]
        val currentSpeed = if (lastSample.second > previousSample.second) {
            ((lastSample.first - previousSample.first) * 1000) / (lastSample.second - previousSample.second)
        } else {
            0L
        }

        // Average speed (all samples)
        val firstSample = samplesList.first()
        val averageSpeed = if (lastSample.second > firstSample.second) {
            ((lastSample.first - firstSample.first) * 1000) / (lastSample.second - firstSample.second)
        } else {
            0L
        }

        // Estimated time remaining
        val remainingTime = if (averageSpeed > 0 && samples.isNotEmpty()) {
            // This is simplified; actual implementation would need total bytes
            -1L
        } else {
            -1L
        }

        return DownloadSpeed(
            currentBytesPerSecond = currentSpeed,
            averageBytesPerSecond = averageSpeed,
            estimatedTimeRemainingMs = remainingTime
        )
    }

    /**
     * Validates download request.
     */
    private suspend fun validateInternal(request: DownloadRequest): DownloadValidation =
        withContext(Dispatchers.IO) {
            val checks = mutableListOf<ValidationCheck>()

            // URL validity
            checks.add(
                ValidationCheck(
                    type = ValidationCheckType.URL_VALIDITY,
                    passed = request.url.startsWith("http://") || request.url.startsWith("https://"),
                    message = "URL must be HTTP or HTTPS"
                )
            )

            // Destination writable
            val parentDir = request.destination.parentFile
            checks.add(
                ValidationCheck(
                    type = ValidationCheckType.DESTINATION_WRITABLE,
                    passed = parentDir == null || parentDir.exists() || parentDir.mkdirs(),
                    message = "Destination directory is not writable"
                )
            )

            // File exists check
            if (request.destination.exists() && !request.overwriteExisting && !request.resumeIfPossible) {
                checks.add(
                    ValidationCheck(
                        type = ValidationCheckType.FILE_EXISTS,
                        passed = false,
                        message = "File already exists and overwrite/resume is disabled"
                    )
                )
            } else {
                checks.add(
                    ValidationCheck(
                        type = ValidationCheckType.FILE_EXISTS,
                        passed = true,
                        message = "File handling configured correctly"
                    )
                )
            }

            // Disk space check
            // Note: For production, consider using StorageManager#getAllocatableBytes
            // which accounts for clearable cached data
            if (request.expectedSize > 0) {
                val usableSpace = request.destination.parentFile?.usableSpace ?: Long.MAX_VALUE
                checks.add(
                    ValidationCheck(
                        type = ValidationCheckType.DISK_SPACE,
                        passed = usableSpace > request.expectedSize,
                        message = if (usableSpace > request.expectedSize) {
                            "Sufficient disk space available"
                        } else {
                            "Insufficient disk space"
                        }
                    )
                )
            }

            val isValid = checks.none { !it.passed && it.severity == ValidationSeverity.ERROR }

            DownloadValidation(isValid, checks)
        }

    /**
     * Verifies file checksum.
     */
    private fun verifyChecksum(file: File, expectedChecksum: String, algorithm: String): Boolean {
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(bufferSize)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
        return actualChecksum.equals(expectedChecksum, ignoreCase = true)
    }

    /**
     * Maps exceptions to typed DownloadError.
     */
    private fun mapExceptionToError(exception: Exception): DownloadError {
        return when (exception) {
            is IOException -> DownloadError.NetworkError(
                message = exception.message ?: "Network error occurred",
                cause = exception
            )

            is IllegalArgumentException -> DownloadError.UnknownError(
                message = exception.message ?: "Invalid argument",
                cause = exception
            )

            else -> DownloadError.UnknownError(
                message = exception.message ?: "Unknown error occurred",
                cause = exception
            )
        }
    }

    /**
     * Creates HTTP exception from response code.
     */
    private fun createHttpException(code: Int, message: String): Exception {
        return IOException("HTTP $code: $message")
    }

    /**
     * Internal context for managing download state.
     */
    private data class DownloadContext(
        val request: DownloadRequest,
        var currentState: DownloadState = DownloadState.Idle(request.id),
        var currentCall: Call? = null,
        var isPaused: Boolean = false,
        val pauseRequested: Mutex = Mutex(),
        var serverInfo: ServerInfo = ServerInfo(),
        var metadata: DownloadMetadata = DownloadMetadata(),
        var timing: DownloadTiming = DownloadTiming(),
        val stateHistory: MutableList<StateTransition> = mutableListOf()
    )
}
