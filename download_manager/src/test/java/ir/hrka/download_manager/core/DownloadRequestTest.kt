package ir.hrka.download_manager.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Comprehensive tests for DownloadRequest and its Builder.
 */
class DownloadRequestTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    @Test
    fun `setAuthToken adds authorization header`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")

        val request = DownloadRequest.Builder(url, destination)
            .setAuthToken("my-token")
            .build()

        assertEquals("Bearer my-token", request.headers["Authorization"])
    }

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

    @Test(expected = IllegalArgumentException::class)
    fun `blank url throws exception`() {
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder("", destination).build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-http url throws exception`() {
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder("ftp://example.com/file.zip", destination).build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative connect timeout throws exception`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder(url, destination)
            .setConnectTimeout(-1)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero connect timeout throws exception`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder(url, destination)
            .setConnectTimeout(0)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative read timeout throws exception`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder(url, destination)
            .setReadTimeout(-1)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative max retries throws exception`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder(url, destination)
            .setMaxRetries(-1)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative retry delay throws exception`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        DownloadRequest.Builder(url, destination)
            .setRetryDelay(-1)
            .build()
    }

    @Test
    fun `http url is valid`() {
        val url = "http://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder(url, destination).build()

        assertEquals(url, request.url)
    }

    @Test
    fun `https url is valid`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder(url, destination).build()

        assertEquals(url, request.url)
    }

    @Test
    fun `zero max retries is valid`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder(url, destination)
            .setMaxRetries(0)
            .build()

        assertEquals(0, request.maxRetries)
    }

    @Test
    fun `zero retry delay is valid`() {
        val url = "https://example.com/file.zip"
        val destination = tempFolder.newFile("test.zip")
        val request = DownloadRequest.Builder(url, destination)
            .setRetryDelay(0)
            .build()

        assertEquals(0, request.retryDelay)
    }

    @Test
    fun `download priority enum has correct values`() {
        val priorities = DownloadPriority.values()

        assertEquals(4, priorities.size)
        assertEquals(DownloadPriority.LOW, priorities[0])
        assertEquals(DownloadPriority.NORMAL, priorities[1])
        assertEquals(DownloadPriority.HIGH, priorities[2])
        assertEquals(DownloadPriority.CRITICAL, priorities[3])
    }

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

