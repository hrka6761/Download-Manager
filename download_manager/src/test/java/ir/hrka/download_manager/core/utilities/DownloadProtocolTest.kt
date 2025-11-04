package ir.hrka.download_manager.core.utilities

import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive test suite for [DownloadProtocol] enum.
 *
 * Tests protocol detection, validation, and capabilities for all supported protocols:
 * HTTP, HTTPS, FTP, and FTPS.
 *
 * **Test Coverage:**
 * - Protocol detection from URLs
 * - Protocol properties (port, authentication, resume support)
 * - Helper methods (isSupported, fromUrl, supportedSchemes)
 * - Case-insensitive protocol matching
 * - Invalid protocol handling
 *
 * **Total Tests:** 36
 *
 * @see DownloadProtocol
 */
class DownloadProtocolTest {

    // ==================== Protocol Detection Tests ====================

    /**
     * Tests HTTP protocol detection from various URLs.
     */
    @Test
    fun `fromUrl detects HTTP protocol`() {
        val urls = listOf(
            "http://example.com/file",
            "HTTP://EXAMPLE.COM/FILE", // Case insensitive
            "http://192.168.1.1/download"
        )

        urls.forEach { url ->
            assertEquals(DownloadProtocol.HTTP, DownloadProtocol.fromUrl(url))
        }
    }

    /**
     * Tests HTTPS protocol detection from various URLs.
     */
    @Test
    fun `fromUrl detects HTTPS protocol`() {
        val urls = listOf(
            "https://example.com/file",
            "HTTPS://EXAMPLE.COM/FILE",
            "https://api.github.com/repos/user/file"
        )

        urls.forEach { url ->
            assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl(url))
        }
    }

    /**
     * Tests FTP protocol detection.
     */
    @Test
    fun `fromUrl detects FTP protocol`() {
        assertEquals(DownloadProtocol.FTP, DownloadProtocol.fromUrl("ftp://ftp.example.com/file"))
        assertEquals(DownloadProtocol.FTP, DownloadProtocol.fromUrl("FTP://SERVER.COM/pub/file"))
    }

    /**
     * Tests FTPS protocol detection.
     */
    @Test
    fun `fromUrl detects FTPS protocol`() {
        assertEquals(DownloadProtocol.FTPS, DownloadProtocol.fromUrl("ftps://secure.ftp.com/file"))
    }


    /**
     * Tests unsupported protocol returns null.
     */
    @Test
    fun `fromUrl returns null for unsupported protocols`() {
        assertNull(DownloadProtocol.fromUrl("file:///path/to/file"))
        assertNull(DownloadProtocol.fromUrl("ws://example.com/socket"))
        assertNull(DownloadProtocol.fromUrl("mailto:user@example.com"))
        assertNull(DownloadProtocol.fromUrl("tel:+1234567890"))
        assertNull(DownloadProtocol.fromUrl("sftp://server.com/file"))
        assertNull(DownloadProtocol.fromUrl("smb://server/share/file"))
        assertNull(DownloadProtocol.fromUrl("webdav://server.com/files"))
    }

    // ==================== Protocol Properties Tests ====================

    /**
     * Tests HTTP protocol properties.
     */
    @Test
    fun `HTTP protocol has correct properties`() {
        val http = DownloadProtocol.HTTP

        assertEquals("http", http.scheme)
        assertEquals(80, http.defaultPort)
        assertFalse(http.requiresAuthentication)
        assertTrue(http.supportsResume)
    }

    /**
     * Tests HTTPS protocol properties.
     */
    @Test
    fun `HTTPS protocol has correct properties`() {
        val https = DownloadProtocol.HTTPS

        assertEquals("https", https.scheme)
        assertEquals(443, https.defaultPort)
        assertFalse(https.requiresAuthentication)
        assertTrue(https.supportsResume)
    }

    /**
     * Tests FTP protocol properties.
     */
    @Test
    fun `FTP protocol has correct properties`() {
        val ftp = DownloadProtocol.FTP

        assertEquals("ftp", ftp.scheme)
        assertEquals(21, ftp.defaultPort)
        assertFalse(ftp.requiresAuthentication) // Can be anonymous
        assertTrue(ftp.supportsResume)
    }

    /**
     * Tests FTPS protocol properties.
     */
    @Test
    fun `FTPS protocol has correct properties`() {
        val ftps = DownloadProtocol.FTPS

        assertEquals("ftps", ftps.scheme)
        assertEquals(990, ftps.defaultPort)
        assertTrue(ftps.requiresAuthentication)
        assertTrue(ftps.supportsResume)
    }


    // ==================== Helper Methods Tests ====================

    /**
     * Tests isSupported() for all supported protocols.
     */
    @Test
    fun `isSupported returns true for all supported protocols`() {
        assertTrue(DownloadProtocol.isSupported("http://example.com/file"))
        assertTrue(DownloadProtocol.isSupported("https://example.com/file"))
        assertTrue(DownloadProtocol.isSupported("ftp://ftp.example.com/file"))
        assertTrue(DownloadProtocol.isSupported("ftps://secure.ftp.com/file"))
    }

    /**
     * Tests isSupported() returns false for unsupported protocols.
     */
    @Test
    fun `isSupported returns false for unsupported protocols`() {
        assertFalse(DownloadProtocol.isSupported("file:///path"))
        assertFalse(DownloadProtocol.isSupported("ws://example.com"))
        assertFalse(DownloadProtocol.isSupported("mailto:user@example.com"))
        assertFalse(DownloadProtocol.isSupported("sftp://server.com/file"))
        assertFalse(DownloadProtocol.isSupported("smb://server/share"))
        assertFalse(DownloadProtocol.isSupported("webdav://server.com/dav"))
    }

    /**
     * Tests supportedSchemes() returns all protocol schemes.
     */
    @Test
    fun `supportedSchemes returns all 4 protocols`() {
        val schemes = DownloadProtocol.supportedSchemes()

        assertEquals(4, schemes.size)
        assertTrue(schemes.contains("http"))
        assertTrue(schemes.contains("https"))
        assertTrue(schemes.contains("ftp"))
        assertTrue(schemes.contains("ftps"))
    }

    /**
     * Tests getSupportedProtocolsString() formats correctly.
     */
    @Test
    fun `getSupportedProtocolsString returns formatted list`() {
        val protocolString = DownloadProtocol.getSupportedProtocolsString()

        assertTrue(protocolString.contains("HTTP"))
        assertTrue(protocolString.contains("HTTPS"))
        assertTrue(protocolString.contains("FTP"))
        assertTrue(protocolString.contains("FTPS"))
        assertTrue(protocolString.contains(", ")) // Comma-separated
    }

    /**
     * Tests protocol enum has exactly 4 values.
     */
    @Test
    fun `enum has exactly 4 supported protocols`() {
        val protocols = DownloadProtocol.values()

        assertEquals(4, protocols.size)
        assertEquals(DownloadProtocol.HTTP, protocols[0])
        assertEquals(DownloadProtocol.HTTPS, protocols[1])
        assertEquals(DownloadProtocol.FTP, protocols[2])
        assertEquals(DownloadProtocol.FTPS, protocols[3])
    }

    /**
     * Tests case-insensitive protocol matching.
     */
    @Test
    fun `protocol detection is case insensitive`() {
        assertEquals(DownloadProtocol.HTTP, DownloadProtocol.fromUrl("HTTP://EXAMPLE.COM/FILE"))
        assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl("HTTPS://EXAMPLE.COM/FILE"))
        assertEquals(DownloadProtocol.FTP, DownloadProtocol.fromUrl("FTP://SERVER.COM/FILE"))
        assertEquals(DownloadProtocol.FTPS, DownloadProtocol.fromUrl("FTPS://SERVER.COM/FILE"))
    }

    /**
     * Tests resume support for all protocols.
     */
    @Test
    fun `all protocols support resume`() {
        // All protocols support resume
        assertTrue(DownloadProtocol.HTTP.supportsResume)
        assertTrue(DownloadProtocol.HTTPS.supportsResume)
        assertTrue(DownloadProtocol.FTP.supportsResume)
        assertTrue(DownloadProtocol.FTPS.supportsResume)
    }

    /**
     * Tests authentication requirements vary by protocol.
     */
    @Test
    fun `authentication requirements vary by protocol`() {
        // Protocols that don't require auth (can be public/anonymous)
        assertFalse(DownloadProtocol.HTTP.requiresAuthentication)
        assertFalse(DownloadProtocol.HTTPS.requiresAuthentication)
        assertFalse(DownloadProtocol.FTP.requiresAuthentication)
        
        // Protocols that typically require auth
        assertTrue(DownloadProtocol.FTPS.requiresAuthentication)
    }

    // ==================== Edge Cases & Boundary Tests ====================

    /**
     * Tests fromUrl() handles empty string correctly.
     */
    @Test
    fun `fromUrl returns null for empty string`() {
        assertNull(DownloadProtocol.fromUrl(""))
    }

    /**
     * Tests fromUrl() handles blank string correctly.
     */
    @Test
    fun `fromUrl returns null for blank string`() {
        assertNull(DownloadProtocol.fromUrl("   "))
    }

    /**
     * Tests fromUrl() handles URL without slashes.
     */
    @Test
    fun `fromUrl returns null for scheme without slashes`() {
        assertNull(DownloadProtocol.fromUrl("http:example.com"))
        assertNull(DownloadProtocol.fromUrl("https:example.com/file"))
    }

    /**
     * Tests fromUrl() handles URL with only scheme and slashes.
     */
    @Test
    fun `fromUrl returns null for incomplete URL`() {
        assertNull(DownloadProtocol.fromUrl("http://"))
        assertNull(DownloadProtocol.fromUrl("https://"))
    }

    /**
     * Tests fromUrl() handles misspelled protocols.
     */
    @Test
    fun `fromUrl returns null for typo in protocol`() {
        assertNull(DownloadProtocol.fromUrl("htttp://example.com/file"))
        assertNull(DownloadProtocol.fromUrl("htps://example.com/file"))
        assertNull(DownloadProtocol.fromUrl("htp://example.com/file"))
    }

    /**
     * Tests fromUrl() detects protocol with mixed case in URL path.
     */
    @Test
    fun `fromUrl detects protocol with mixed case URL path`() {
        assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl("https://ExAmPlE.CoM/FiLe"))
        assertEquals(DownloadProtocol.HTTP, DownloadProtocol.fromUrl("HTTP://example.com/FiLe"))
    }

    /**
     * Tests fromUrl() with URL containing query parameters.
     */
    @Test
    fun `fromUrl detects protocol with query parameters`() {
        assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl("https://example.com/file?key=value&token=abc"))
        assertEquals(DownloadProtocol.FTP, DownloadProtocol.fromUrl("ftp://ftp.example.com/path?param=value"))
    }

    /**
     * Tests fromUrl() with URL containing anchor/fragment.
     */
    @Test
    fun `fromUrl detects protocol with anchor fragment`() {
        assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl("https://example.com/file.zip#section"))
    }

    /**
     * Tests fromUrl() with URL containing authentication credentials.
     */
    @Test
    fun `fromUrl detects protocol with credentials in URL`() {
        assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl("https://user:pass@example.com/file"))
        assertEquals(DownloadProtocol.FTP, DownloadProtocol.fromUrl("ftp://anonymous:email@ftp.server.com/file"))
        assertEquals(DownloadProtocol.FTPS, DownloadProtocol.fromUrl("ftps://user@secure.server.com/file"))
    }

    /**
     * Tests fromUrl() with very long URL.
     */
    @Test
    fun `fromUrl detects protocol with very long URL`() {
        val longPath = "a".repeat(1000)
        assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl("https://example.com/$longPath/file.zip"))
    }

    /**
     * Tests isSupported() handles empty string.
     */
    @Test
    fun `isSupported returns false for empty string`() {
        assertFalse(DownloadProtocol.isSupported(""))
    }

    /**
     * Tests isSupported() handles blank string.
     */
    @Test
    fun `isSupported returns false for blank string`() {
        assertFalse(DownloadProtocol.isSupported("   "))
    }

    /**
     * Tests getSupportedProtocolsString() contains all protocols.
     */
    @Test
    fun `getSupportedProtocolsString contains all 4 protocols`() {
        val protocolString = DownloadProtocol.getSupportedProtocolsString()
        
        val protocols = protocolString.split(", ")
        assertEquals(4, protocols.size)
        assertEquals("HTTP", protocols[0])
        assertEquals("HTTPS", protocols[1])
        assertEquals("FTP", protocols[2])
        assertEquals("FTPS", protocols[3])
    }

    /**
     * Tests supportedSchemes() returns protocols in correct order.
     */
    @Test
    fun `supportedSchemes returns protocols in enum order`() {
        val schemes = DownloadProtocol.supportedSchemes()
        
        assertEquals("http", schemes[0])
        assertEquals("https", schemes[1])
        assertEquals("ftp", schemes[2])
        assertEquals("ftps", schemes[3])
    }

    /**
     * Tests protocol detection with URL containing port number.
     */
    @Test
    fun `fromUrl detects protocol with custom port`() {
        assertEquals(DownloadProtocol.HTTP, DownloadProtocol.fromUrl("http://example.com:8080/file"))
        assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl("https://example.com:443/file"))
        assertEquals(DownloadProtocol.FTP, DownloadProtocol.fromUrl("ftp://ftp.server.com:2121/file"))
        assertEquals(DownloadProtocol.FTPS, DownloadProtocol.fromUrl("ftps://secure.server.com:990/file"))
    }

    /**
     * Tests fromUrl() with IPv6 addresses.
     */
    @Test
    fun `fromUrl detects protocol with IPv6 address`() {
        assertEquals(DownloadProtocol.HTTP, DownloadProtocol.fromUrl("http://[2001:db8::1]/file"))
        assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl("https://[::1]:8443/file"))
    }

    /**
     * Tests fromUrl() handles URL with special characters.
     */
    @Test
    fun `fromUrl detects protocol with URL encoded characters`() {
        assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl("https://example.com/file%20name.zip"))
        assertEquals(DownloadProtocol.FTP, DownloadProtocol.fromUrl("ftp://ftp.server.com/path%2Fto%2Ffile"))
    }

    /**
     * Tests fromUrl() handles protocol at start of longer string.
     */
    @Test
    fun `fromUrl only checks protocol prefix`() {
        // Should detect based on prefix only
        assertEquals(DownloadProtocol.HTTP, DownloadProtocol.fromUrl("http://example.com https://other.com"))
        assertNull(DownloadProtocol.fromUrl("text before http://example.com"))
    }
}

