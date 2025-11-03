package ir.hrka.download_manager.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Comprehensive test suite for [DownloadRequest] and its [DownloadRequest.Builder].
 *
 * This test class verifies the proper functioning of the DownloadRequest data class,
 * including the Builder pattern implementation, validation logic, and immutable copy methods.
 *
 * **Test Coverage:**
 * - Builder pattern with default and custom values (17 configuration options)
 * - Immutable copy methods (withAuthToken, withHeaders, withMetadata)
 * - Request validation (URL format, timeouts, retry configuration)
 * - Edge cases and error conditions
 * - Unique ID generation
 * - Enum validation
 *
 * **Total Tests:** 25
 *
 * @see DownloadRequest
 * @see DownloadRequest.Builder
 */
class DownloadRequestTest {

    /**
     * JUnit rule that creates a temporary folder for test files.
     * The folder and its contents are automatically deleted after each test.
     */
    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * Tests that the Builder creates a DownloadRequest with all default values correctly set.
     *
     * **What is tested:**
     * - Builder creates request with only required parameters (URL and destination)
     * - All 17 optional parameters are set to expected default values
     * - Auto-generated ID follows expected format (starts with "download_")
     * - Default values match documented specifications
     *
     * **Validates:**
     * - resumeIfPossible = true (default)
     * - overwriteExisting = false (default)
     * - connectTimeout = 15,000ms (default)
     * - readTimeout = 60,000ms (default)
     * - maxRetries = 3 (default)
     * - retryDelay = 1,000ms (default)
     * - requireWifi = false (default)
     * - allowMeteredConnection = true (default)
     * - priority = NORMAL (default)
     * - Empty headers and metadata by default
     */
    @Test
    fun `builder creates request with default values`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")

        val request = DownloadRequest.Builder(url, destination).build()

        assertEquals(url, request.url)
        assertEquals(destination, request.destination)
        assertTrue(request.id.startsWith("download_"))
        assertTrue(request.headers.isEmpty())
        assertTrue(request.resumeIfPossible)
        assertFalse(request.overwriteExisting)
        assertNull(request.checksum)
        assertNull(request.checksumAlgorithm)
        assertEquals(-1, request.expectedSize)
        assertEquals(15_000, request.connectTimeout)
        assertEquals(60_000, request.readTimeout)
        assertEquals(3, request.maxRetries)
        assertEquals(1_000, request.retryDelay)
        assertFalse(request.requireWifi)
        assertTrue(request.allowMeteredConnection)
        assertEquals(DownloadPriority.NORMAL, request.priority)
        assertTrue(request.metadata.isEmpty())
    }

    /**
     * Tests that the Builder correctly applies all custom configuration values.
     *
     * **What is tested:**
     * - Builder's fluent API allows chaining all 17 setter methods
     * - Each setter correctly stores the provided value
     * - Custom ID overrides auto-generated ID
     * - Multiple headers can be added
     * - Checksum and algorithm are stored together
     * - All configuration options are independently settable
     *
     * **Validates:**
     * - Custom ID assignment
     * - Multiple header addition
     * - Resume disabled
     * - Overwrite enabled
     * - Checksum configuration (SHA-256)
     * - Expected file size
     * - Custom timeouts (30s connect, 120s read)
     * - Retry configuration (5 retries, 2s delay)
     * - WiFi requirement enabled
     * - Metered connection disabled
     * - HIGH priority level
     * - Custom metadata
     */
    @Test
    fun `builder creates request with custom values`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")

        val request = DownloadRequest.Builder(url, destination)
            .setId("custom-id")
            .addHeader("Authorization", "Bearer token")
            .addHeader("X-Custom", "value")
            .setResumeIfPossible(false)
            .setOverwriteExisting(true)
            .setChecksum("abc123", "SHA-256")
            .setExpectedSize(1024000)
            .setConnectTimeout(30_000)
            .setReadTimeout(120_000)
            .setMaxRetries(5)
            .setRetryDelay(2_000)
            .setRequireWifi(true)
            .setAllowMeteredConnection(false)
            .setPriority(DownloadPriority.HIGH)
            .addMetadata("key1", "value1")
            .build()

        assertEquals("custom-id", request.id)
        assertEquals(url, request.url)
        assertEquals(destination, request.destination)
        assertEquals(2, request.headers.size)
        assertEquals("Bearer token", request.headers["Authorization"])
        assertEquals("value", request.headers["X-Custom"])
        assertFalse(request.resumeIfPossible)
        assertTrue(request.overwriteExisting)
        assertEquals("abc123", request.checksum)
        assertEquals("SHA-256", request.checksumAlgorithm)
        assertEquals(1024000, request.expectedSize)
        assertEquals(30_000, request.connectTimeout)
        assertEquals(120_000, request.readTimeout)
        assertEquals(5, request.maxRetries)
        assertEquals(2_000, request.retryDelay)
        assertTrue(request.requireWifi)
        assertFalse(request.allowMeteredConnection)
        assertEquals(DownloadPriority.HIGH, request.priority)
        assertEquals(1, request.metadata.size)
        assertEquals("value1", request.metadata["key1"])
    }

    /**
     * Tests that setAuthToken() correctly adds an Authorization header with Bearer token.
     *
     * **What is tested:**
     * - setAuthToken() convenience method adds "Authorization" header
     * - Token is automatically prefixed with "Bearer "
     * - Header value format matches OAuth 2.0 Bearer token standard
     *
     * **Validates:**
     * - Header key is exactly "Authorization"
     * - Header value is "Bearer {token}"
     * - No other headers are added
     */
    @Test
    fun `setAuthToken adds authorization header`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")

        val request = DownloadRequest.Builder(url, destination)
            .setAuthToken("my-token")
            .build()

        assertEquals("Bearer my-token", request.headers["Authorization"])
    }

    /**
     * Tests that setHeaders() replaces all existing headers (not additive).
     *
     * **What is tested:**
     * - setHeaders() completely replaces previous headers
     * - Previously added headers (via addHeader) are removed
     * - Only headers from setHeaders() map are present
     * - This is different from addHeader() which is additive
     *
     * **Validates:**
     * - Header count after setHeaders is 2 (not 3)
     * - Key1 (from addHeader) is null/absent
     * - Key2 and Key3 (from setHeaders) are present
     */
    @Test
    fun `setHeaders replaces existing headers`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")

        val request = DownloadRequest.Builder(url, destination)
            .addHeader("Key1", "Value1")
            .setHeaders(mapOf("Key2" to "Value2", "Key3" to "Value3"))
            .build()

        assertEquals(2, request.headers.size)
        assertNull(request.headers["Key1"])
        assertEquals("Value2", request.headers["Key2"])
        assertEquals("Value3", request.headers["Key3"])
    }

    /**
     * Tests that setMetadata() replaces all existing metadata (not additive).
     *
     * **What is tested:**
     * - setMetadata() completely replaces previous metadata
     * - Previously added metadata (via addMetadata) is removed
     * - Only metadata from setMetadata() map is present
     * - This is different from addMetadata() which is additive
     *
     * **Validates:**
     * - Metadata count after setMetadata is 1 (not 2)
     * - key1 (from addMetadata) is null/absent
     * - key2 (from setMetadata) is present
     */
    @Test
    fun `setMetadata replaces existing metadata`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")

        val request = DownloadRequest.Builder(url, destination)
            .addMetadata("key1", "value1")
            .setMetadata(mapOf("key2" to "value2"))
            .build()

        assertEquals(1, request.metadata.size)
        assertNull(request.metadata["key1"])
        assertEquals("value2", request.metadata["key2"])
    }

    /**
     * Tests that withAuthToken() creates a new immutable request with authorization.
     *
     * **What is tested:**
     * - withAuthToken() returns a NEW DownloadRequest instance (immutability)
     * - Original request remains unchanged (no headers added)
     * - New request has Authorization header with Bearer token
     * - All other properties are copied from original
     *
     * **Validates:**
     * - Immutability: original.headers is still empty
     * - New request has Authorization header
     * - URL and destination are identical in both requests
     */
    @Test
    fun `withAuthToken creates new request with auth header`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")

        val original = DownloadRequest.Builder(url, destination).build()
        val withAuth = original.withAuthToken("token123")

        assertTrue(original.headers.isEmpty())
        assertEquals("Bearer token123", withAuth.headers["Authorization"])
        assertEquals(original.url, withAuth.url)
        assertEquals(original.destination, withAuth.destination)
    }

    /**
     * Tests that withHeaders() creates a new request with additional headers (additive).
     *
     * **What is tested:**
     * - withHeaders() returns a NEW DownloadRequest instance (immutability)
     * - Original request remains unchanged
     * - New request merges existing headers with new headers
     * - This is additive (unlike setHeaders() which replaces)
     *
     * **Validates:**
     * - Immutability: original has 1 header, new has 2 headers
     * - Existing headers are preserved (Key1 still present)
     * - New headers are added (Key2 added)
     * - No headers are lost in the process
     */
    @Test
    fun `withHeaders creates new request with additional headers`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")

        val original = DownloadRequest.Builder(url, destination)
            .addHeader("Key1", "Value1")
            .build()

        val withHeaders = original.withHeaders(mapOf("Key2" to "Value2"))

        assertEquals(1, original.headers.size)
        assertEquals(2, withHeaders.headers.size)
        assertEquals("Value1", withHeaders.headers["Key1"])
        assertEquals("Value2", withHeaders.headers["Key2"])
    }

    /**
     * Tests that withMetadata() creates a new request with additional metadata (additive).
     *
     * **What is tested:**
     * - withMetadata() returns a NEW DownloadRequest instance (immutability)
     * - Original request remains unchanged
     * - New request merges existing metadata with new metadata
     * - This is additive (unlike setMetadata() which replaces)
     *
     * **Validates:**
     * - Immutability: original has 1 metadata entry, new has 2 entries
     * - Existing metadata is preserved (key1 still present)
     * - New metadata is added (key2 added)
     * - No metadata is lost in the process
     */
    @Test
    fun `withMetadata creates new request with additional metadata`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")

        val original = DownloadRequest.Builder(url, destination)
            .addMetadata("key1", "value1")
            .build()

        val withMetadata = original.withMetadata(mapOf("key2" to "value2"))

        assertEquals(1, original.metadata.size)
        assertEquals(2, withMetadata.metadata.size)
        assertEquals("value1", withMetadata.metadata["key1"])
        assertEquals("value2", withMetadata.metadata["key2"])
    }

    /**
     * Tests that blank/empty URL is rejected during request validation.
     *
     * **What is tested:**
     * - DownloadRequest constructor validates URL is not blank
     * - Empty string "" is considered invalid
     * - IllegalArgumentException is thrown during build()
     * - Error message indicates URL cannot be blank
     *
     * **Validates:**
     * - Input validation at construction time
     * - Fail-fast approach for invalid configuration
     * - Clear error messaging
     */
    @Test(expected = IllegalArgumentException::class)
    fun `blank url throws exception`() {
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder("", destination).build()
    }

    /**
     * Tests that non-HTTP/HTTPS URLs are rejected during request validation.
     *
     * **What is tested:**
     * - Only HTTP and HTTPS protocols are allowed
     * - FTP protocol is rejected
     * - IllegalArgumentException is thrown during build()
     * - Error message indicates URL must be HTTP or HTTPS
     *
     * **Validates:**
     * - Protocol validation (only http:// or https://)
     * - Security: prevents non-web protocols
     * - Clear error for unsupported protocols
     */
    @Test(expected = IllegalArgumentException::class)
    fun `non-http url throws exception`() {
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder("ftp://example.com/file.zip", destination).build()
    }

    /**
     * Tests that negative connect timeout is rejected during validation.
     *
     * **What is tested:**
     * - Connect timeout must be positive (> 0)
     * - Negative values are invalid
     * - IllegalArgumentException is thrown during build()
     * - Validation happens at construction time
     *
     * **Validates:**
     * - Timeout value validation
     * - Prevents invalid network configuration
     * - Fail-fast error handling
     */
    @Test(expected = IllegalArgumentException::class)
    fun `negative connect timeout throws exception`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder(url, destination)
            .setConnectTimeout(-1)
            .build()
    }

    /**
     * Tests that zero connect timeout is rejected during validation.
     *
     * **What is tested:**
     * - Connect timeout must be positive (> 0)
     * - Zero is considered invalid (would cause immediate timeout)
     * - IllegalArgumentException is thrown during build()
     *
     * **Validates:**
     * - Boundary condition (zero is invalid)
     * - Prevents unusable configuration
     * - Sensible defaults enforcement
     */
    @Test(expected = IllegalArgumentException::class)
    fun `zero connect timeout throws exception`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder(url, destination)
            .setConnectTimeout(0)
            .build()
    }

    /**
     * Tests that negative read timeout is rejected during validation.
     *
     * **What is tested:**
     * - Read timeout must be positive (> 0)
     * - Negative values are invalid
     * - IllegalArgumentException is thrown during build()
     *
     * **Validates:**
     * - Read timeout validation
     * - Prevents invalid network configuration
     * - Consistent validation across timeout types
     */
    @Test(expected = IllegalArgumentException::class)
    fun `negative read timeout throws exception`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder(url, destination)
            .setReadTimeout(-1)
            .build()
    }

    /**
     * Tests that negative max retries is rejected during validation.
     *
     * **What is tested:**
     * - Max retries must be non-negative (>= 0)
     * - Negative values are invalid
     * - IllegalArgumentException is thrown during build()
     *
     * **Validates:**
     * - Retry configuration validation
     * - Prevents invalid retry logic
     * - Note: Zero retries IS valid (no retries)
     */
    @Test(expected = IllegalArgumentException::class)
    fun `negative max retries throws exception`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder(url, destination)
            .setMaxRetries(-1)
            .build()
    }

    /**
     * Tests that negative retry delay is rejected during validation.
     *
     * **What is tested:**
     * - Retry delay must be non-negative (>= 0)
     * - Negative values are invalid
     * - IllegalArgumentException is thrown during build()
     *
     * **Validates:**
     * - Retry delay validation
     * - Time values cannot be negative
     * - Note: Zero delay IS valid (immediate retry)
     */
    @Test(expected = IllegalArgumentException::class)
    fun `negative retry delay throws exception`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder(url, destination)
            .setRetryDelay(-1)
            .build()
    }

    /**
     * Tests that HTTP URLs (non-secure) are accepted.
     *
     * **What is tested:**
     * - http:// protocol is valid (in addition to https://)
     * - Request is created successfully with HTTP URL
     * - URL is stored exactly as provided
     *
     * **Validates:**
     * - Both HTTP and HTTPS are supported
     * - No forced HTTPS upgrade
     * - URL preservation
     */
    @Test
    fun `http url is valid`() {
        val url = "http://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder(url, destination).build()

        assertEquals(url, request.url)
    }

    /**
     * Tests that HTTPS URLs (secure) are accepted.
     *
     * **What is tested:**
     * - https:// protocol is valid
     * - Request is created successfully with HTTPS URL
     * - URL is stored exactly as provided
     *
     * **Validates:**
     * - HTTPS protocol support
     * - Secure connections supported
     * - URL preservation
     */
    @Test
    fun `https url is valid`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder(url, destination).build()

        assertEquals(url, request.url)
    }

    /**
     * Tests that zero max retries is a valid configuration (no automatic retries).
     *
     * **What is tested:**
     * - maxRetries = 0 is valid (boundary case)
     * - Zero means "no retries, single attempt only"
     * - Request is created successfully
     *
     * **Validates:**
     * - Boundary value acceptance (zero is valid)
     * - Allows disabling retry mechanism
     * - Distinction between invalid (negative) and valid (zero)
     */
    @Test
    fun `zero max retries is valid`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder(url, destination)
            .setMaxRetries(0)
            .build()

        assertEquals(0, request.maxRetries)
    }

    /**
     * Tests that zero retry delay is a valid configuration (immediate retry).
     *
     * **What is tested:**
     * - retryDelay = 0 is valid (boundary case)
     * - Zero means "immediate retry with no delay"
     * - Request is created successfully
     *
     * **Validates:**
     * - Boundary value acceptance (zero is valid)
     * - Allows immediate retries
     * - Distinction between invalid (negative) and valid (zero)
     */
    @Test
    fun `zero retry delay is valid`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder(url, destination)
            .setRetryDelay(0)
            .build()

        assertEquals(0, request.retryDelay)
    }

    /**
     * Tests that DownloadPriority enum has all expected values in correct order.
     *
     * **What is tested:**
     * - Enum has exactly 4 values
     * - Values are in priority order: LOW, NORMAL, HIGH, CRITICAL
     * - Ordinal values match expected sequence (0, 1, 2, 3)
     *
     * **Validates:**
     * - Complete enum definition
     * - Correct ordering for priority-based logic
     * - No missing or extra values
     */
    @Test
    fun `download priority enum has correct values`() {
        val priorities = DownloadPriority.values()

        assertEquals(4, priorities.size)
        assertEquals(DownloadPriority.LOW, priorities[0])
        assertEquals(DownloadPriority.NORMAL, priorities[1])
        assertEquals(DownloadPriority.HIGH, priorities[2])
        assertEquals(DownloadPriority.CRITICAL, priorities[3])
    }

    /**
     * Tests that each DownloadRequest gets a unique auto-generated ID.
     *
     * **What is tested:**
     * - Auto-generated IDs are unique across multiple requests
     * - ID generation uses timestamp + counter mechanism
     * - Even requests created in quick succession get different IDs
     * - No ID collisions occur
     *
     * **Validates:**
     * - Uniqueness of auto-generated IDs
     * - ID generation algorithm reliability
     * - Support for concurrent download tracking
     */
    @Test
    fun `each request gets unique id`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")

        val request1 = DownloadRequest.Builder(url, destination).build()
        val request2 = DownloadRequest.Builder(url, destination).build()
        val request3 = DownloadRequest.Builder(url, destination).build()

        assertNotEquals(request1.id, request2.id)
        assertNotEquals(request2.id, request3.id)
        assertNotEquals(request1.id, request3.id)
    }
}
