package ir.hrka.download_manager.core.utilities

/**
 * Supported download protocols.
 *
 * Currently supports HTTP, HTTPS, FTP, and FTPS protocols.
 * Each protocol may require different implementation strategies.
 *
 * @property scheme The URL scheme identifier (e.g., "http", "https", "ftp")
 * @property defaultPort The standard port for this protocol (if applicable)
 * @property requiresAuthentication Whether the protocol typically requires authentication
 * @property supportsResume Whether the protocol supports resume/partial downloads
 */
enum class DownloadProtocol(
    val scheme: String,
    val defaultPort: Int,
    val requiresAuthentication: Boolean = false,
    val supportsResume: Boolean = true
) {
    /**
     * HTTP protocol (unencrypted web transfer).
     * Port: 80
     * Supports: Range requests for resume
     */
    HTTP(
        scheme = "http",
        defaultPort = 80,
        supportsResume = true
    ),

    /**
     * HTTPS protocol (encrypted web transfer).
     * Port: 443
     * Supports: Range requests for resume
     */
    HTTPS(
        scheme = "https",
        defaultPort = 443,
        supportsResume = true
    ),

    /**
     * FTP protocol (File Transfer Protocol).
     * Port: 21
     * Supports: REST command for resume
     */
    FTP(
        scheme = "ftp",
        defaultPort = 21,
        requiresAuthentication = false, // Can be anonymous
        supportsResume = true
    ),

    /**
     * FTPS protocol (FTP over SSL/TLS).
     * Port: 990 (implicit) or 21 (explicit)
     * Supports: REST command for resume
     */
    FTPS(
        scheme = "ftps",
        defaultPort = 990,
        requiresAuthentication = true,
        supportsResume = true
    );

    companion object {
        /**
         * Determines the protocol from a URL string.
         *
         * @param url The URL to analyze
         * @return The detected [DownloadProtocol] or null if not supported
         */
        fun fromUrl(url: String): DownloadProtocol? {
            val lowercaseUrl = url.lowercase()
            return values().firstOrNull { lowercaseUrl.startsWith("${it.scheme}://") }
        }

        /**
         * Gets all supported protocol schemes.
         *
         * @return List of supported schemes (e.g., ["http", "https", "ftp"])
         */
        fun supportedSchemes(): List<String> {
            return values().map { it.scheme }
        }

        /**
         * Checks if a protocol is supported.
         *
         * @param url The URL to check
         * @return true if protocol is supported, false otherwise
         */
        fun isSupported(url: String): Boolean {
            return fromUrl(url) != null
        }

        /**
         * Gets a formatted string of all supported protocols.
         *
         * @return Comma-separated list of supported protocols
         */
        fun getSupportedProtocolsString(): String {
            return supportedSchemes().joinToString(", ") { it.uppercase() }
        }
    }
}

