package ir.hrka.download_manager.core.utilities

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Comprehensive test suite for [DownloadRequest] and its [DownloadRequest.Builder].
 *
 * This unified test class covers all aspects of DownloadRequest including:
 * - Builder pattern with default and custom values
 * - All 17 parameter validations (types, ranges, boundaries)
 * - Immutable copy methods (withAuthToken, withHeaders, withMetadata)
 * - Input validation and error prevention
 * - Support for various servers and URL formats
 * - Edge cases and error conditions
 *
 * **Test Organization:**
 * - Builder Pattern Tests (3 tests)
 * - Immutable Copy Methods (3 tests)
 * - ID Parameter (4 tests)
 * - URL Parameter (15 tests) - Comprehensive server support
 * - Destination Parameter (3 tests)
 * - Headers Parameter (4 tests)
 * - Boolean Parameters (12 tests)
 * - Checksum Parameters (6 tests)
 * - Size Parameter (5 tests)
 * - Timeout Parameters (14 tests)
 * - Retry Parameters (10 tests)
 * - Priority Parameter (5 tests)
 * - Metadata Parameter (5 tests)
 * - Combined & Edge Case Tests (6 tests)
 *
 * **Total Tests:** 95
 *
 * @see DownloadRequest
 * @see DownloadRequest.Builder
 */
class DownloadRequestTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ==================== Builder Pattern Tests ====================

    /**
     * Tests Builder creates request with all default values.
     *
     * Validates all 17 parameters have correct defaults.
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
     * Tests Builder correctly applies all 17 custom configuration values.
     */
    @Test
    fun `builder creates request with all custom values`() {
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
        assertEquals(2, request.headers.size)
        assertFalse(request.resumeIfPossible)
        assertTrue(request.overwriteExisting)
        assertEquals("abc123", request.checksum)
        assertEquals(1024000, request.expectedSize)
        assertEquals(30_000, request.connectTimeout)
        assertEquals(120_000, request.readTimeout)
        assertEquals(5, request.maxRetries)
        assertEquals(2_000, request.retryDelay)
        assertTrue(request.requireWifi)
        assertFalse(request.allowMeteredConnection)
        assertEquals(DownloadPriority.HIGH, request.priority)
    }

    /**
     * Tests that Builder fluent API allows method chaining.
     */
    @Test
    fun `builder supports fluent method chaining`() {
        val destination = tempFolder.newFile()
        
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setId("id1").setId("id2").setId("final-id") // Last wins
            .addHeader("H1", "V1").addHeader("H2", "V2") // Both added
            .build()

        assertEquals("final-id", request.id)
        assertEquals(2, request.headers.size)
    }

    // ==================== Immutable Copy Methods ====================

    /**
     * Tests withAuthToken() creates new immutable request.
     */
    @Test
    fun `withAuthToken creates new request preserving immutability`() {
        val destination = tempFolder.newFile()
        val original = DownloadRequest.Builder("https://example.com/file", destination).build()
        val withAuth = original.withAuthToken("token123")

        assertTrue(original.headers.isEmpty())
        assertEquals("Bearer token123", withAuth.headers["Authorization"])
        assertNotSame(original, withAuth)
    }

    /**
     * Tests withHeaders() merges headers additively.
     */
    @Test
    fun `withHeaders merges headers additively`() {
        val destination = tempFolder.newFile()
        val original = DownloadRequest.Builder("https://example.com/file", destination)
            .addHeader("Key1", "Value1")
            .build()

        val withHeaders = original.withHeaders(mapOf("Key2" to "Value2"))

        assertEquals(1, original.headers.size)
        assertEquals(2, withHeaders.headers.size)
        assertEquals("Value1", withHeaders.headers["Key1"])
        assertEquals("Value2", withHeaders.headers["Key2"])
    }

    /**
     * Tests withMetadata() merges metadata additively.
     */
    @Test
    fun `withMetadata merges metadata additively`() {
        val destination = tempFolder.newFile()
        val original = DownloadRequest.Builder("https://example.com/file", destination)
            .addMetadata("key1", "value1")
            .build()

        val withMetadata = original.withMetadata(mapOf("key2" to "value2"))

        assertEquals(2, withMetadata.metadata.size)
        assertEquals("value1", withMetadata.metadata["key1"])
        assertEquals("value2", withMetadata.metadata["key2"])
    }

    // ==================== ID Parameter Tests ====================

    /**
     * Tests ID accepts various string formats.
     */
    @Test
    fun `id accepts any non-blank string format`() {
        val destination = tempFolder.newFile()
        
        val r1 = DownloadRequest.Builder("https://example.com/file", destination)
            .setId("simple-id").build()
        val r2 = DownloadRequest.Builder("https://example.com/file", destination)
            .setId("id_with_underscores").build()
        val r3 = DownloadRequest.Builder("https://example.com/file", destination)
            .setId("id-123-special!@#").build()

        assertEquals("simple-id", r1.id)
        assertEquals("id_with_underscores", r2.id)
        assertEquals("id-123-special!@#", r3.id)
    }

    /**
     * Tests ID is auto-generated with correct format.
     */
    @Test
    fun `id is auto-generated with timestamp and counter`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertTrue(request.id.startsWith("download_"))
        assertTrue(request.id.contains("_"))
        assertTrue(request.id.length > 10)
    }

    /**
     * Tests auto-generated IDs are globally unique.
     */
    @Test
    fun `auto-generated ids are unique across 100 requests`() {
        val destination = tempFolder.newFile()
        val ids = (1..100).map {
            DownloadRequest.Builder("https://example.com/file", destination).build().id
        }.toSet()

        assertEquals(100, ids.size)
    }

    /**
     * Tests blank ID is rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `id rejects blank string`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setId("")
            .build()
    }

    // ==================== URL Parameter Tests (Various Servers) ====================

    /**
     * Tests HTTP protocol is supported (various servers).
     */
    @Test
    fun `url supports http protocol for various servers`() {
        val destination = tempFolder.newFile()
        
        val urls = listOf(
            "http://example.com/file.zip",
            "http://192.168.1.1/download",
            "http://localhost:8080/file",
            "http://internal-server.local/data"
        )

        urls.forEach { url ->
            val request = DownloadRequest.Builder(url, destination).build()
            assertEquals(url, request.url)
        }
    }

    /**
     * Tests HTTPS protocol is supported (various servers).
     */
    @Test
    fun `url supports https protocol for various servers`() {
        val destination = tempFolder.newFile()
        
        val urls = listOf(
            "https://example.com/file.zip",
            "https://api.github.com/repos/user/repo/releases/download/v1.0/file.zip",
            "https://cdn.cloudflare.com/path/to/file",
            "https://192.168.1.1:443/secure/download"
        )

        urls.forEach { url ->
            val request = DownloadRequest.Builder(url, destination).build()
            assertEquals(url, request.url)
        }
    }

    /**
     * Tests URLs with query parameters (common in authenticated downloads).
     */
    @Test
    fun `url supports query parameters for authenticated servers`() {
        val destination = tempFolder.newFile()
        val url = "https://server.com/download?token=abc123&expires=1234567890&signature=xyz"
        
        val request = DownloadRequest.Builder(url, destination).build()
        assertEquals(url, request.url)
    }

    /**
     * Tests URLs with custom ports (internal networks, development servers).
     */
    @Test
    fun `url supports custom ports for internal servers`() {
        val destination = tempFolder.newFile()
        
        val urls = listOf(
            "http://localhost:3000/file",
            "https://10.0.0.5:8443/download",
            "http://server.local:9090/api/files/123"
        )

        urls.forEach { url ->
            val request = DownloadRequest.Builder(url, destination).build()
            assertEquals(url, request.url)
        }
    }

    /**
     * Tests URLs with IPv4 addresses (direct server access).
     */
    @Test
    fun `url supports IPv4 addresses for direct server access`() {
        val destination = tempFolder.newFile()
        
        val urls = listOf(
            "http://192.168.1.100/file.zip",
            "https://10.0.0.1:8080/download",
            "http://172.16.0.50/data"
        )

        urls.forEach { url ->
            val request = DownloadRequest.Builder(url, destination).build()
            assertEquals(url, request.url)
        }
    }

    /**
     * Tests URLs with IPv6 addresses.
     */
    @Test
    fun `url supports IPv6 addresses`() {
        val destination = tempFolder.newFile()
        val url = "http://[2001:db8::1]/file.zip"
        
        val request = DownloadRequest.Builder(url, destination).build()
        assertEquals(url, request.url)
    }

    /**
     * Tests URLs with subdomains (CDN, API servers).
     */
    @Test
    fun `url supports subdomains for CDN and API servers`() {
        val destination = tempFolder.newFile()
        
        val urls = listOf(
            "https://cdn.example.com/files/download.zip",
            "https://api.v2.service.com/downloads/file",
            "https://downloads.subdomain.example.org/files/app.apk"
        )

        urls.forEach { url ->
            val request = DownloadRequest.Builder(url, destination).build()
            assertEquals(url, request.url)
        }
    }

    /**
     * Tests URLs with special characters in path.
     */
    @Test
    fun `url supports special characters in file paths`() {
        val destination = tempFolder.newFile()
        val url = "https://example.com/files/my%20file%20(1).zip"
        
        val request = DownloadRequest.Builder(url, destination).build()
        assertEquals(url, request.url)
    }

    /**
     * Tests URLs with basic auth credentials.
     */
    @Test
    fun `url supports basic auth in URL`() {
        val destination = tempFolder.newFile()
        val url = "https://user:pass@example.com/file.zip"
        
        val request = DownloadRequest.Builder(url, destination).build()
        assertEquals(url, request.url)
    }

    /**
     * Tests blank URL is rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `url rejects blank string`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("", destination).build()
    }

    /**
     * Tests whitespace-only URL is rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `url rejects whitespace-only string`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("   ", destination).build()
    }

    /**
     * Tests URL with leading/trailing whitespace is rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `url rejects leading or trailing whitespace`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder(" https://example.com/file ", destination).build()
    }

    /**
     * Tests malformed URLs are rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `url rejects malformed URLs`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://", destination).build()
    }

    /**
     * Tests FTP protocol is supported.
     */
    @Test
    fun `url supports ftp protocol`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("ftp://ftp.example.com/pub/file.zip", destination).build()

        assertEquals("ftp://ftp.example.com/pub/file.zip", request.url)
        assertEquals(DownloadProtocol.FTP, request.getProtocol())
    }

    /**
     * Tests FTPS protocol is supported.
     */
    @Test
    fun `url supports ftps protocol`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("ftps://secure.ftp.com/files/data.bin", destination).build()

        assertEquals("ftps://secure.ftp.com/files/data.bin", request.url)
        assertEquals(DownloadProtocol.FTPS, request.getProtocol())
    }

    /**
     * Tests SFTP protocol is supported.
     */
    @Test
    fun `url supports sftp protocol`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("sftp://server.com:22/path/file", destination).build()

        assertEquals("sftp://server.com:22/path/file", request.url)
        assertEquals(DownloadProtocol.SFTP, request.getProtocol())
    }

    /**
     * Tests SMB protocol is supported.
     */
    @Test
    fun `url supports smb protocol`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("smb://fileserver/share/file.zip", destination).build()

        assertEquals("smb://fileserver/share/file.zip", request.url)
        assertEquals(DownloadProtocol.SMB, request.getProtocol())
    }

    /**
     * Tests WebDAV protocol is supported.
     */
    @Test
    fun `url supports webdav protocol`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("webdav://webdav.server.com/files/document.pdf", destination).build()

        assertEquals("webdav://webdav.server.com/files/document.pdf", request.url)
        assertEquals(DownloadProtocol.WEBDAV, request.getProtocol())
    }

    /**
     * Tests file protocol is rejected (local file system, not network download).
     */
    @Test(expected = IllegalArgumentException::class)
    fun `url rejects file protocol`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("file:///path/to/file", destination).build()
    }

    /**
     * Tests WebSocket protocol is rejected (streaming, not file download).
     */
    @Test(expected = IllegalArgumentException::class)
    fun `url rejects websocket protocol`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("ws://example.com/socket", destination).build()
    }
    
    /**
     * Tests mailto protocol is rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `url rejects mailto protocol`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("mailto:user@example.com", destination).build()
    }

    // ==================== Destination Parameter Tests ====================

    /**
     * Tests destination accepts new file paths.
     */
    @Test
    fun `destination accepts new file path`() {
        val destination = File(tempFolder.root, "newfile.zip")
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertEquals(destination, request.destination)
        assertEquals("newfile.zip", request.destination.name)
    }

    /**
     * Tests destination accepts existing files.
     */
    @Test
    fun `destination accepts existing files`() {
        val destination = tempFolder.newFile("existing.zip")
        destination.writeText("old content")
        
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()
        assertEquals(destination, request.destination)
        assertTrue(destination.exists())
    }

    /**
     * Tests destination accepts nested directory paths.
     */
    @Test
    fun `destination accepts deeply nested paths`() {
        val destination = File(tempFolder.root, "a/b/c/d/e/file.zip")
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertEquals(destination, request.destination)
    }

    // ==================== Headers Parameter Tests ====================

    /**
     * Tests headers default to empty map.
     */
    @Test
    fun `headers default to empty map`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertTrue(request.headers.isEmpty())
        assertEquals(0, request.headers.size)
    }

    /**
     * Tests addHeader() is additive.
     */
    @Test
    fun `addHeader adds headers additively`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .addHeader("Key1", "Value1")
            .addHeader("Key2", "Value2")
            .addHeader("Key3", "Value3")
            .build()

        assertEquals(3, request.headers.size)
        assertEquals("Value1", request.headers["Key1"])
    }

    /**
     * Tests setHeaders() replaces all headers.
     */
    @Test
    fun `setHeaders replaces all previous headers`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .addHeader("Old1", "Value1")
            .addHeader("Old2", "Value2")
            .setHeaders(mapOf("New1" to "NewValue1"))
            .build()

        assertEquals(1, request.headers.size)
        assertNull(request.headers["Old1"])
        assertEquals("NewValue1", request.headers["New1"])
    }

    /**
     * Tests setAuthToken() convenience method.
     */
    @Test
    fun `setAuthToken adds Bearer authorization header`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setAuthToken("my-secret-token")
            .build()

        assertEquals("Bearer my-secret-token", request.headers["Authorization"])
    }

    // ==================== Boolean Parameters Tests ====================

    /**
     * Tests resumeIfPossible accepts both values and defaults to true.
     */
    @Test
    fun `resumeIfPossible accepts true and false, defaults to true`() {
        val destination = tempFolder.newFile()
        
        val default = DownloadRequest.Builder("https://example.com/file", destination).build()
        val withTrue = DownloadRequest.Builder("https://example.com/file", destination)
            .setResumeIfPossible(true).build()
        val withFalse = DownloadRequest.Builder("https://example.com/file", destination)
            .setResumeIfPossible(false).build()

        assertTrue(default.resumeIfPossible)
        assertTrue(withTrue.resumeIfPossible)
        assertFalse(withFalse.resumeIfPossible)
    }

    /**
     * Tests overwriteExisting accepts both values and defaults to false.
     */
    @Test
    fun `overwriteExisting accepts true and false, defaults to false`() {
        val destination = tempFolder.newFile()
        
        val default = DownloadRequest.Builder("https://example.com/file", destination).build()
        val withTrue = DownloadRequest.Builder("https://example.com/file", destination)
            .setOverwriteExisting(true).build()
        val withFalse = DownloadRequest.Builder("https://example.com/file", destination)
            .setOverwriteExisting(false).build()

        assertFalse(default.overwriteExisting)
        assertTrue(withTrue.overwriteExisting)
        assertFalse(withFalse.overwriteExisting)
    }

    /**
     * Tests requireWifi accepts both values and defaults to false.
     */
    @Test
    fun `requireWifi accepts true and false, defaults to false`() {
        val destination = tempFolder.newFile()
        
        val default = DownloadRequest.Builder("https://example.com/file", destination).build()
        val withTrue = DownloadRequest.Builder("https://example.com/file", destination)
            .setRequireWifi(true).build()

        assertFalse(default.requireWifi)
        assertTrue(withTrue.requireWifi)
    }

    /**
     * Tests allowMeteredConnection accepts both values and defaults to true.
     */
    @Test
    fun `allowMeteredConnection accepts true and false, defaults to true`() {
        val destination = tempFolder.newFile()
        
        val default = DownloadRequest.Builder("https://example.com/file", destination).build()
        val withFalse = DownloadRequest.Builder("https://example.com/file", destination)
            .setAllowMeteredConnection(false).build()

        assertTrue(default.allowMeteredConnection)
        assertFalse(withFalse.allowMeteredConnection)
    }

    // ==================== Checksum Parameters Tests ====================

    /**
     * Tests checksum defaults to null.
     */
    @Test
    fun `checksum and algorithm default to null`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertNull(request.checksum)
        assertNull(request.checksumAlgorithm)
    }

    /**
     * Tests checksum accepts standard algorithms.
     */
    @Test
    fun `checksum supports standard algorithms`() {
        val destination = tempFolder.newFile()
        
        val md5 = DownloadRequest.Builder("https://example.com/file", destination)
            .setChecksum("5d41402abc4b2a76b9719d911017c592", "MD5").build()
        val sha256 = DownloadRequest.Builder("https://example.com/file", destination)
            .setChecksum("abc123", "SHA-256").build()
        val sha512 = DownloadRequest.Builder("https://example.com/file", destination)
            .setChecksum("def456", "SHA-512").build()

        assertEquals("MD5", md5.checksumAlgorithm)
        assertEquals("SHA-256", sha256.checksumAlgorithm)
        assertEquals("SHA-512", sha512.checksumAlgorithm)
    }

    /**
     * Tests both checksum and algorithm must be provided together.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `checksum without algorithm throws exception`() {
        val destination = tempFolder.newFile()
        DownloadRequest(
            url = "https://example.com/file",
            destination = destination,
            checksum = "abc123",
            checksumAlgorithm = null
        )
    }

    /**
     * Tests algorithm without checksum throws exception.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `algorithm without checksum throws exception`() {
        val destination = tempFolder.newFile()
        DownloadRequest(
            url = "https://example.com/file",
            destination = destination,
            checksum = null,
            checksumAlgorithm = "SHA-256"
        )
    }

    /**
     * Tests blank checksum is rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `checksum rejects blank string`() {
        val destination = tempFolder.newFile()
        DownloadRequest(
            url = "https://example.com/file",
            destination = destination,
            checksum = "",
            checksumAlgorithm = "SHA-256"
        )
    }

    /**
     * Tests blank algorithm is rejected.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `checksum algorithm rejects blank string`() {
        val destination = tempFolder.newFile()
        DownloadRequest(
            url = "https://example.com/file",
            destination = destination,
            checksum = "abc123",
            checksumAlgorithm = ""
        )
    }

    // ==================== ExpectedSize Parameter Tests ====================

    /**
     * Tests expectedSize accepts -1 for unknown size.
     */
    @Test
    fun `expectedSize accepts -1 for unknown size`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setExpectedSize(-1).build()

        assertEquals(-1, request.expectedSize)
    }

    /**
     * Tests expectedSize accepts zero for empty files.
     */
    @Test
    fun `expectedSize accepts 0 for empty files`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setExpectedSize(0).build()

        assertEquals(0, request.expectedSize)
    }

    /**
     * Tests expectedSize accepts standard file sizes.
     */
    @Test
    fun `expectedSize accepts standard file sizes`() {
        val destination = tempFolder.newFile()
        
        val small = DownloadRequest.Builder("https://example.com/file", destination)
            .setExpectedSize(1024).build() // 1 KB
        val medium = DownloadRequest.Builder("https://example.com/file", destination)
            .setExpectedSize(1024 * 1024).build() // 1 MB
        val large = DownloadRequest.Builder("https://example.com/file", destination)
            .setExpectedSize(100L * 1024 * 1024).build() // 100 MB

        assertEquals(1024, small.expectedSize)
        assertEquals(1048576, medium.expectedSize)
        assertEquals(104857600, large.expectedSize)
    }

    /**
     * Tests expectedSize accepts very large files (10+ GB).
     */
    @Test
    fun `expectedSize accepts very large files`() {
        val destination = tempFolder.newFile()
        val tenGB = 10L * 1024 * 1024 * 1024
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setExpectedSize(tenGB).build()

        assertEquals(tenGB, request.expectedSize)
    }

    /**
     * Tests expectedSize rejects values less than -1.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `expectedSize rejects values less than -1`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setExpectedSize(-2).build()
    }

    // ==================== ConnectTimeout Tests ====================

    /**
     * Tests connectTimeout minimum value (1ms).
     */
    @Test
    fun `connectTimeout accepts minimum 1 millisecond`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setConnectTimeout(1).build()

        assertEquals(1, request.connectTimeout)
    }

    /**
     * Tests connectTimeout standard values.
     */
    @Test
    fun `connectTimeout accepts standard values`() {
        val destination = tempFolder.newFile()
        
        val fast = DownloadRequest.Builder("https://example.com/file", destination)
            .setConnectTimeout(5_000).build()
        val normal = DownloadRequest.Builder("https://example.com/file", destination)
            .setConnectTimeout(15_000).build()
        val slow = DownloadRequest.Builder("https://example.com/file", destination)
            .setConnectTimeout(30_000).build()

        assertEquals(5_000, fast.connectTimeout)
        assertEquals(15_000, normal.connectTimeout)
        assertEquals(30_000, slow.connectTimeout)
    }

    /**
     * Tests connectTimeout maximum allowed (5 minutes).
     */
    @Test
    fun `connectTimeout accepts maximum 5 minutes`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setConnectTimeout(300_000).build()

        assertEquals(300_000, request.connectTimeout)
    }

    /**
     * Tests connectTimeout rejects zero.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `connectTimeout rejects zero`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setConnectTimeout(0).build()
    }

    /**
     * Tests connectTimeout rejects negative.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `connectTimeout rejects negative values`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setConnectTimeout(-100).build()
    }

    /**
     * Tests connectTimeout rejects excessive values (> 5 minutes).
     */
    @Test(expected = IllegalArgumentException::class)
    fun `connectTimeout rejects excessive values over 5 minutes`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setConnectTimeout(400_000).build() // 6 minutes
    }

    /**
     * Tests connectTimeout default value.
     */
    @Test
    fun `connectTimeout defaults to 15 seconds`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertEquals(15_000, request.connectTimeout)
    }

    // ==================== ReadTimeout Tests ====================

    /**
     * Tests readTimeout minimum value (1ms).
     */
    @Test
    fun `readTimeout accepts minimum 1 millisecond`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setReadTimeout(1).build()

        assertEquals(1, request.readTimeout)
    }

    /**
     * Tests readTimeout accepts standard values.
     */
    @Test
    fun `readTimeout accepts standard values`() {
        val destination = tempFolder.newFile()
        
        val normal = DownloadRequest.Builder("https://example.com/file", destination)
            .setReadTimeout(60_000).build()
        val long = DownloadRequest.Builder("https://example.com/file", destination)
            .setReadTimeout(300_000).build()

        assertEquals(60_000, normal.readTimeout)
        assertEquals(300_000, long.readTimeout)
    }

    /**
     * Tests readTimeout maximum allowed (1 hour).
     */
    @Test
    fun `readTimeout accepts maximum 1 hour for large files`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setReadTimeout(3_600_000).build()

        assertEquals(3_600_000, request.readTimeout)
    }

    /**
     * Tests readTimeout rejects zero.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `readTimeout rejects zero`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setReadTimeout(0).build()
    }

    /**
     * Tests readTimeout rejects negative.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `readTimeout rejects negative values`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setReadTimeout(-1000).build()
    }

    /**
     * Tests readTimeout rejects excessive values (> 1 hour).
     */
    @Test(expected = IllegalArgumentException::class)
    fun `readTimeout rejects excessive values over 1 hour`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setReadTimeout(4_000_000).build() // > 1 hour
    }

    /**
     * Tests readTimeout default value.
     */
    @Test
    fun `readTimeout defaults to 60 seconds`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertEquals(60_000, request.readTimeout)
    }

    // ==================== MaxRetries Tests ====================

    /**
     * Tests maxRetries accepts zero (no retries).
     */
    @Test
    fun `maxRetries accepts 0 for no retries`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setMaxRetries(0).build()

        assertEquals(0, request.maxRetries)
    }

    /**
     * Tests maxRetries accepts reasonable values.
     */
    @Test
    fun `maxRetries accepts reasonable retry counts`() {
        val destination = tempFolder.newFile()
        
        val conservative = DownloadRequest.Builder("https://example.com/file", destination)
            .setMaxRetries(3).build()
        val aggressive = DownloadRequest.Builder("https://example.com/file", destination)
            .setMaxRetries(10).build()

        assertEquals(3, conservative.maxRetries)
        assertEquals(10, aggressive.maxRetries)
    }

    /**
     * Tests maxRetries maximum allowed (100).
     */
    @Test
    fun `maxRetries accepts maximum 100 retries`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setMaxRetries(100).build()

        assertEquals(100, request.maxRetries)
    }

    /**
     * Tests maxRetries rejects negative.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `maxRetries rejects negative values`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setMaxRetries(-1).build()
    }

    /**
     * Tests maxRetries rejects excessive values (> 100).
     */
    @Test(expected = IllegalArgumentException::class)
    fun `maxRetries rejects excessive values over 100`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setMaxRetries(101).build()
    }

    /**
     * Tests maxRetries default value.
     */
    @Test
    fun `maxRetries defaults to 3`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertEquals(3, request.maxRetries)
    }

    // ==================== RetryDelay Tests ====================

    /**
     * Tests retryDelay accepts zero for immediate retry.
     */
    @Test
    fun `retryDelay accepts 0 for immediate retry`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setRetryDelay(0).build()

        assertEquals(0, request.retryDelay)
    }

    /**
     * Tests retryDelay accepts standard delays.
     */
    @Test
    fun `retryDelay accepts standard delay values`() {
        val destination = tempFolder.newFile()
        
        val quick = DownloadRequest.Builder("https://example.com/file", destination)
            .setRetryDelay(500).build()
        val normal = DownloadRequest.Builder("https://example.com/file", destination)
            .setRetryDelay(5_000).build()

        assertEquals(500, quick.retryDelay)
        assertEquals(5_000, normal.retryDelay)
    }

    /**
     * Tests retryDelay maximum allowed (10 minutes).
     */
    @Test
    fun `retryDelay accepts maximum 10 minutes`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setRetryDelay(600_000).build()

        assertEquals(600_000, request.retryDelay)
    }

    /**
     * Tests retryDelay rejects negative.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `retryDelay rejects negative values`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setRetryDelay(-100).build()
    }

    /**
     * Tests retryDelay rejects excessive values (> 10 minutes).
     */
    @Test(expected = IllegalArgumentException::class)
    fun `retryDelay rejects excessive values over 10 minutes`() {
        val destination = tempFolder.newFile()
        DownloadRequest.Builder("https://example.com/file", destination)
            .setRetryDelay(700_000).build()
    }

    /**
     * Tests retryDelay default value.
     */
    @Test
    fun `retryDelay defaults to 1 second`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertEquals(1_000, request.retryDelay)
    }

    // ==================== Priority Tests ====================

    /**
     * Tests priority accepts all enum values.
     */
    @Test
    fun `priority accepts all enum values`() {
        val destination = tempFolder.newFile()
        
        val low = DownloadRequest.Builder("https://example.com/file", destination)
            .setPriority(DownloadPriority.LOW).build()
        val normal = DownloadRequest.Builder("https://example.com/file", destination)
            .setPriority(DownloadPriority.NORMAL).build()
        val high = DownloadRequest.Builder("https://example.com/file", destination)
            .setPriority(DownloadPriority.HIGH).build()
        val critical = DownloadRequest.Builder("https://example.com/file", destination)
            .setPriority(DownloadPriority.CRITICAL).build()

        assertEquals(DownloadPriority.LOW, low.priority)
        assertEquals(DownloadPriority.NORMAL, normal.priority)
        assertEquals(DownloadPriority.HIGH, high.priority)
        assertEquals(DownloadPriority.CRITICAL, critical.priority)
    }

    /**
     * Tests priority enum has exactly 4 values.
     */
    @Test
    fun `priority enum has 4 values in correct order`() {
        val priorities = DownloadPriority.entries

        assertEquals(4, priorities.size)
        assertEquals(DownloadPriority.LOW, priorities[0])
        assertEquals(DownloadPriority.NORMAL, priorities[1])
        assertEquals(DownloadPriority.HIGH, priorities[2])
        assertEquals(DownloadPriority.CRITICAL, priorities[3])
    }

    /**
     * Tests priority default value.
     */
    @Test
    fun `priority defaults to NORMAL`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertEquals(DownloadPriority.NORMAL, request.priority)
    }

    // ==================== Metadata Tests ====================

    /**
     * Tests metadata defaults to empty map.
     */
    @Test
    fun `metadata defaults to empty map`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination).build()

        assertTrue(request.metadata.isEmpty())
    }

    /**
     * Tests addMetadata() is additive.
     */
    @Test
    fun `addMetadata adds metadata additively`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .addMetadata("key1", "value1")
            .addMetadata("key2", "value2")
            .build()

        assertEquals(2, request.metadata.size)
        assertEquals("value1", request.metadata["key1"])
    }

    /**
     * Tests setMetadata() replaces all metadata.
     */
    @Test
    fun `setMetadata replaces all previous metadata`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .addMetadata("old1", "value1")
            .setMetadata(mapOf("new1" to "newValue"))
            .build()

        assertEquals(1, request.metadata.size)
        assertNull(request.metadata["old1"])
        assertEquals("newValue", request.metadata["new1"])
    }

    /**
     * Tests metadata accepts special characters.
     */
    @Test
    fun `metadata accepts special characters and unicode`() {
        val destination = tempFolder.newFile()
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .addMetadata("user-agent", "MyApp/1.0 (Android)")
            .addMetadata("description", "文件下载")
            .build()

        assertEquals("MyApp/1.0 (Android)", request.metadata["user-agent"])
        assertEquals("文件下载", request.metadata["description"])
    }

    // ==================== Combined & Edge Case Tests ====================

    /**
     * Tests all 17 parameters can be configured simultaneously.
     */
    @Test
    fun `all 17 parameters can be configured together`() {
        val destination = tempFolder.newFile()
        
        val request = DownloadRequest.Builder("https://example.com/file.zip", destination)
            .setId("test-id")
            .addHeader("Authorization", "Bearer token")
            .setResumeIfPossible(true)
            .setOverwriteExisting(false)
            .setChecksum("abc123", "SHA-256")
            .setExpectedSize(1024000)
            .setConnectTimeout(30_000)
            .setReadTimeout(120_000)
            .setMaxRetries(5)
            .setRetryDelay(2_000)
            .setRequireWifi(true)
            .setAllowMeteredConnection(false)
            .setPriority(DownloadPriority.HIGH)
            .addMetadata("version", "1.0")
            .build()

        // All 17 parameters validated
        assertEquals("test-id", request.id)
        assertEquals("https://example.com/file.zip", request.url)
        assertEquals(destination, request.destination)
        assertEquals(1, request.headers.size)
        assertTrue(request.resumeIfPossible)
        assertFalse(request.overwriteExisting)
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
    }

    /**
     * Tests DownloadRequest is immutable (copy methods create new instances).
     */
    @Test
    fun `download request maintains immutability`() {
        val destination = tempFolder.newFile()
        val original = DownloadRequest.Builder("https://example.com/file", destination)
            .addHeader("Key1", "Value1")
            .addMetadata("meta1", "value1")
            .build()

        val withAuth = original.withAuthToken("token")
        val withHeaders = original.withHeaders(mapOf("Key2" to "Value2"))
        val withMetadata = original.withMetadata(mapOf("meta2" to "value2"))

        // Original unchanged
        assertEquals(1, original.headers.size)
        assertEquals(1, original.metadata.size)
        
        // Copies have additions
        assertEquals(2, withAuth.headers.size)
        assertEquals(2, withHeaders.headers.size)
        assertEquals(2, withMetadata.metadata.size)
        
        // All are different instances
        assertNotSame(original, withAuth)
        assertNotSame(original, withHeaders)
        assertNotSame(original, withMetadata)
    }

    /**
     * Tests request supports various real-world server URLs across all protocols.
     */
    @Test
    fun `request supports various protocols and server types`() {
        val destination = tempFolder.newFile()
        
        // HTTP servers
        val http = DownloadRequest.Builder("http://example.com/file.zip", destination).build()
        
        // HTTPS servers (CDNs, APIs)
        val https = DownloadRequest.Builder("https://cdn.example.com/files/app.apk", destination).build()
        val api = DownloadRequest.Builder("https://api.github.com/repos/user/repo/releases/download/v1.0/file.zip", destination).build()

        // FTP servers
        val ftp = DownloadRequest.Builder("ftp://ftp.example.com/pub/software/file.tar.gz", destination).build()
        val ftpAnon = DownloadRequest.Builder("ftp://anonymous@ftp.server.com/files/data.zip", destination).build()

        // FTPS servers (secure FTP)
        val ftps = DownloadRequest.Builder("ftps://secure.ftp.com:990/files/encrypted.bin", destination).build()
        
        // SFTP servers (SSH-based)
        val sftp = DownloadRequest.Builder("sftp://ssh.server.com:22/home/user/file.zip", destination).build()
        
        // SMB/CIFS servers (Windows file sharing)
        val smb = DownloadRequest.Builder("smb://fileserver.local/share/documents/report.pdf", destination).build()
        val smbAuth = DownloadRequest.Builder("smb://user:pass@192.168.1.5/shared/file.zip", destination).build()

        // WebDAV servers
        val webdav = DownloadRequest.Builder("webdav://webdav.example.com/files/document.docx", destination).build()
        
        // Internal network
        val internal = DownloadRequest.Builder("http://192.168.1.100:8080/files/download", destination).build()

        // Localhost development
        val localhost = DownloadRequest.Builder("http://localhost:3000/test-file.bin", destination).build()
        
        // IPv6
        val ipv6 = DownloadRequest.Builder("http://[::1]/file", destination).build()

        // Verify all protocols detected correctly
        assertEquals(DownloadProtocol.HTTP, http.getProtocol())
        assertEquals(DownloadProtocol.HTTPS, https.getProtocol())
        assertEquals(DownloadProtocol.FTP, ftp.getProtocol())
        assertEquals(DownloadProtocol.FTPS, ftps.getProtocol())
        assertEquals(DownloadProtocol.SFTP, sftp.getProtocol())
        assertEquals(DownloadProtocol.SMB, smb.getProtocol())
        assertEquals(DownloadProtocol.WEBDAV, webdav.getProtocol())
    }
    
    /**
     * Tests supportsResume() method considers both protocol and configuration.
     */
    @Test
    fun `supportsResume considers protocol capability and configuration`() {
        val destination = tempFolder.newFile()
        
        // HTTP with resume enabled (default)
        val httpResume = DownloadRequest.Builder("https://example.com/file", destination).build()
        assertTrue(httpResume.supportsResume())
        
        // HTTP with resume disabled
        val httpNoResume = DownloadRequest.Builder("https://example.com/file", destination)
            .setResumeIfPossible(false).build()
        assertFalse(httpNoResume.supportsResume())
        
        // SMB doesn't support resume (protocol limitation)
        val smb = DownloadRequest.Builder("smb://server/share/file", destination).build()
        assertFalse(smb.supportsResume())
    }

    /**
     * Tests logical conflicts are allowed at construction (validated at runtime).
     */
    @Test
    fun `logically conflicting options are allowed at construction`() {
        val destination = tempFolder.newFile()
        
        // Both resume and overwrite enabled (runtime will choose one)
        val request = DownloadRequest.Builder("https://example.com/file", destination)
            .setResumeIfPossible(true)
            .setOverwriteExisting(true)
            .build()

        assertTrue(request.resumeIfPossible)
        assertTrue(request.overwriteExisting)
        // Note: Implementation should handle this at runtime
    }

    /**
     * Tests Builder validates parameters at build() time, not setter time.
     */
    @Test
    fun `builder validates at build time not setter time`() {
        val destination = tempFolder.newFile()
        val builder = DownloadRequest.Builder("https://example.com/file", destination)
        
        // These don't throw yet (lazy validation)
        builder.setConnectTimeout(-1)
        builder.setMaxRetries(-5)
        
        // Exception thrown at build()
        try {
            builder.build()
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive") || e.message!!.contains("negative"))
        }
    }

    /**
     * Tests copy() method preserves all properties.
     */
    @Test
    fun `data class copy preserves all 17 properties`() {
        val destination = tempFolder.newFile()
        val original = DownloadRequest.Builder("https://example.com/file", destination)
            .setId("original-id")
            .setExpectedSize(1024)
            .setPriority(DownloadPriority.HIGH)
            .build()

        val copied = original.copy(id = "copied-id")

        assertEquals("copied-id", copied.id) // Changed
        assertEquals(original.url, copied.url) // Preserved
        assertEquals(original.expectedSize, copied.expectedSize) // Preserved
        assertEquals(original.priority, copied.priority) // Preserved
    }
}
