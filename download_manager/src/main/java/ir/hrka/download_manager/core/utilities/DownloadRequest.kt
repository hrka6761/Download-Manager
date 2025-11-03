package ir.hrka.download_manager.core.utilities

import java.io.File
import java.net.MalformedURLException
import java.net.URL

/**
 * Immutable request object for initiating a download operation.
 *
 * This class encapsulates all configuration needed to perform a download,
 * following the Builder pattern for flexible construction.
 *
 * @property id Unique identifier for this download (auto-generated if not provided)
 * @property url The URL to download from (must be valid HTTP/HTTPS URL)
 * @property destination The target file where downloaded content will be saved
 * @property headers HTTP headers to include in the request
 * @property resumeIfPossible Whether to attempt resuming if partial download exists
 * @property overwriteExisting Whether to overwrite existing file at destination
 * @property checksum Optional checksum for verification after download
 * @property checksumAlgorithm Algorithm used for checksum (e.g., "MD5", "SHA-256")
 * @property expectedSize Expected file size in bytes (-1 if unknown)
 * @property connectTimeout Connection timeout in milliseconds
 * @property readTimeout Read timeout in milliseconds
 * @property maxRetries Maximum number of automatic retry attempts on failure
 * @property retryDelay Delay between retry attempts in milliseconds
 * @property requireWifi Whether to require WiFi connection for this download
 * @property allowMeteredConnection Whether to allow download on metered connections
 * @property priority Download priority for queue management
 * @property metadata Custom metadata associated with this download
 */
data class DownloadRequest(
    val id: String = generateDownloadId(),
    val url: String,
    val destination: File,
    val headers: Map<String, String> = emptyMap(),
    val resumeIfPossible: Boolean = true,
    val overwriteExisting: Boolean = false,
    val checksum: String? = null,
    val checksumAlgorithm: String? = null,
    val expectedSize: Long = -1,
    val connectTimeout: Long = 15_000,
    val readTimeout: Long = 60_000,
    val maxRetries: Int = 3,
    val retryDelay: Long = 1_000,
    val requireWifi: Boolean = false,
    val allowMeteredConnection: Boolean = true,
    val priority: DownloadPriority = DownloadPriority.NORMAL,
    val metadata: Map<String, String> = emptyMap()
) {

    init {
        // URL validation
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(url.trim() == url) { "URL cannot have leading or trailing whitespace" }
        
        // Protocol validation - support multiple protocols
        require(DownloadProtocol.isSupported(url)) {
            "URL protocol not supported. Supported protocols: ${DownloadProtocol.getSupportedProtocolsString()}"
        }
        
        // Validate URL format (for http/https/ftp protocols)
        val protocol = DownloadProtocol.fromUrl(url)
        if (protocol in listOf(DownloadProtocol.HTTP, DownloadProtocol.HTTPS, DownloadProtocol.FTP, DownloadProtocol.FTPS)) {
            try {
                URL(url)
            } catch (e: MalformedURLException) {
                throw IllegalArgumentException("URL is malformed: ${e.message}", e)
            }
        }
        
        // ID validation
        require(id.isNotBlank()) { "ID cannot be blank" }
        
        // Timeout validation
        require(connectTimeout > 0) { "Connect timeout must be positive" }
        require(readTimeout > 0) { "Read timeout must be positive" }
        require(connectTimeout <= 300_000) { "Connect timeout cannot exceed 5 minutes (300000ms)" }
        require(readTimeout <= 3_600_000) { "Read timeout cannot exceed 1 hour (3600000ms)" }
        
        // Retry validation
        require(maxRetries >= 0) { "Max retries cannot be negative" }
        require(maxRetries <= 100) { "Max retries cannot exceed 100" }
        require(retryDelay >= 0) { "Retry delay cannot be negative" }
        require(retryDelay <= 600_000) { "Retry delay cannot exceed 10 minutes (600000ms)" }
        
        // Size validation
        require(expectedSize >= -1) { "Expected size must be -1 (unknown) or non-negative" }
        
        // Checksum validation
        if (checksum != null || checksumAlgorithm != null) {
            require(checksum != null && checksumAlgorithm != null) {
                "Both checksum and checksumAlgorithm must be provided together"
            }
            require(checksum.isNotBlank()) { "Checksum cannot be blank" }
            require(checksumAlgorithm.isNotBlank()) { "Checksum algorithm cannot be blank" }
        }
    }
    
    /**
     * Gets the protocol of this download request.
     *
     * @return The [DownloadProtocol] for this request
     */
    fun getProtocol(): DownloadProtocol {
        return DownloadProtocol.fromUrl(url) 
            ?: throw IllegalStateException("Protocol should have been validated in init block")
    }
    
    /**
     * Checks if this request supports resume functionality.
     *
     * @return true if the protocol supports resuming downloads
     */
    fun supportsResume(): Boolean {
        return getProtocol().supportsResume && resumeIfPossible
    }

    /**
     * Creates a copy of this request with an authorization header.
     *
     * @param token The bearer token to use for authentication
     * @return A new DownloadRequest with Authorization header added
     */
    fun withAuthToken(token: String): DownloadRequest {
        return copy(headers = headers + ("Authorization" to "Bearer $token"))
    }

    /**
     * Creates a copy of this request with additional headers.
     *
     * @param additionalHeaders Headers to add or override
     * @return A new DownloadRequest with updated headers
     */
    fun withHeaders(additionalHeaders: Map<String, String>): DownloadRequest {
        return copy(headers = headers + additionalHeaders)
    }

    /**
     * Creates a copy of this request with custom metadata.
     *
     * @param additionalMetadata Metadata to add or override
     * @return A new DownloadRequest with updated metadata
     */
    fun withMetadata(additionalMetadata: Map<String, String>): DownloadRequest {
        return copy(metadata = metadata + additionalMetadata)
    }

    companion object {
        private var idCounter = 0L

        /**
         * Generates a unique download ID.
         */
        private fun generateDownloadId(): String {
            return "download_${System.currentTimeMillis()}_${idCounter++}"
        }
    }

    /**
     * Builder for constructing DownloadRequest instances.
     *
     * Provides a fluent API for configuring download requests.
     *
     * Example:
     * ```kotlin
     * val request = DownloadRequest.Builder(url, destination)
     *     .setAuthToken("abc123")
     *     .setExpectedSize(1024000)
     *     .setResumeIfPossible(true)
     *     .setPriority(DownloadPriority.HIGH)
     *     .build()
     * ```
     */
    class Builder(
        private val url: String,
        private val destination: File
    ) {
        private var id: String = generateDownloadId()
        private var headers: MutableMap<String, String> = mutableMapOf()
        private var resumeIfPossible: Boolean = true
        private var overwriteExisting: Boolean = false
        private var checksum: String? = null
        private var checksumAlgorithm: String? = null
        private var expectedSize: Long = -1
        private var connectTimeout: Long = 15_000
        private var readTimeout: Long = 60_000
        private var maxRetries: Int = 3
        private var retryDelay: Long = 1_000
        private var requireWifi: Boolean = false
        private var allowMeteredConnection: Boolean = true
        private var priority: DownloadPriority = DownloadPriority.NORMAL
        private var metadata: MutableMap<String, String> = mutableMapOf()

        fun setId(id: String) = apply { this.id = id }
        fun addHeader(key: String, value: String) = apply { headers[key] = value }
        fun setHeaders(headers: Map<String, String>) = apply { this.headers = headers.toMutableMap() }
        fun setAuthToken(token: String) = apply { headers["Authorization"] = "Bearer $token" }
        fun setResumeIfPossible(resume: Boolean) = apply { this.resumeIfPossible = resume }
        fun setOverwriteExisting(overwrite: Boolean) = apply { this.overwriteExisting = overwrite }
        fun setChecksum(checksum: String, algorithm: String) = apply {
            this.checksum = checksum
            this.checksumAlgorithm = algorithm
        }
        fun setExpectedSize(size: Long) = apply { this.expectedSize = size }
        fun setConnectTimeout(timeout: Long) = apply { this.connectTimeout = timeout }
        fun setReadTimeout(timeout: Long) = apply { this.readTimeout = timeout }
        fun setMaxRetries(retries: Int) = apply { this.maxRetries = retries }
        fun setRetryDelay(delay: Long) = apply { this.retryDelay = delay }
        fun setRequireWifi(require: Boolean) = apply { this.requireWifi = require }
        fun setAllowMeteredConnection(allow: Boolean) = apply { this.allowMeteredConnection = allow }
        fun setPriority(priority: DownloadPriority) = apply { this.priority = priority }
        fun addMetadata(key: String, value: String) = apply { metadata[key] = value }
        fun setMetadata(metadata: Map<String, String>) = apply { this.metadata = metadata.toMutableMap() }

        fun build(): DownloadRequest {
            return DownloadRequest(
                id = id,
                url = url,
                destination = destination,
                headers = headers.toMap(),
                resumeIfPossible = resumeIfPossible,
                overwriteExisting = overwriteExisting,
                checksum = checksum,
                checksumAlgorithm = checksumAlgorithm,
                expectedSize = expectedSize,
                connectTimeout = connectTimeout,
                readTimeout = readTimeout,
                maxRetries = maxRetries,
                retryDelay = retryDelay,
                requireWifi = requireWifi,
                allowMeteredConnection = allowMeteredConnection,
                priority = priority,
                metadata = metadata.toMap()
            )
        }
    }
}

/**
 * Priority levels for download queue management.
 */
enum class DownloadPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

