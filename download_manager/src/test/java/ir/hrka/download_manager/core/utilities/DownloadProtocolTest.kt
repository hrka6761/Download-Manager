package ir.hrka.download_manager.core.utilities

import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive test suite for [DownloadProtocol] enum.
 *
 * Tests protocol detection, validation, and capabilities for all supported protocols:
 * HTTP, HTTPS, FTP, FTPS, SFTP, SMB, and WebDAV.
 *
 * **Test Coverage:**
 * - Protocol detection from URLs
 * - Protocol properties (port, authentication, resume support)
 * - Helper methods (isSupported, fromUrl, supportedSchemes)
 * - Case-insensitive protocol matching
 * - Invalid protocol handling
 *
 * **Total Tests:** 25
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
     * Tests SFTP protocol detection.
     */
    @Test
    fun `fromUrl detects SFTP protocol`() {
        assertEquals(DownloadProtocol.SFTP, DownloadProtocol.fromUrl("sftp://ssh.server.com/file"))
    }

    /**
     * Tests SMB protocol detection.
     */
    @Test
    fun `fromUrl detects SMB protocol`() {
        assertEquals(DownloadProtocol.SMB, DownloadProtocol.fromUrl("smb://fileserver/share/file"))
    }

    /**
     * Tests WebDAV protocol detection.
     */
    @Test
    fun `fromUrl detects WEBDAV protocol`() {
        assertEquals(DownloadProtocol.WEBDAV, DownloadProtocol.fromUrl("webdav://server.com/files/doc"))
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

    /**
     * Tests SFTP protocol properties.
     */
    @Test
    fun `SFTP protocol has correct properties`() {
        val sftp = DownloadProtocol.SFTP

        assertEquals("sftp", sftp.scheme)
        assertEquals(22, sftp.defaultPort)
        assertTrue(sftp.requiresAuthentication)
        assertTrue(sftp.supportsResume)
    }

    /**
     * Tests SMB protocol properties.
     */
    @Test
    fun `SMB protocol has correct properties`() {
        val smb = DownloadProtocol.SMB

        assertEquals("smb", smb.scheme)
        assertEquals(445, smb.defaultPort)
        assertTrue(smb.requiresAuthentication)
        assertFalse(smb.supportsResume) // SMB doesn't support partial transfers
    }

    /**
     * Tests WebDAV protocol properties.
     */
    @Test
    fun `WEBDAV protocol has correct properties`() {
        val webdav = DownloadProtocol.WEBDAV

        assertEquals("webdav", webdav.scheme)
        assertEquals(80, webdav.defaultPort)
        assertTrue(webdav.requiresAuthentication)
        assertTrue(webdav.supportsResume)
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
        assertTrue(DownloadProtocol.isSupported("sftp://ssh.server.com/file"))
        assertTrue(DownloadProtocol.isSupported("smb://fileserver/file"))
        assertTrue(DownloadProtocol.isSupported("webdav://webdav.server.com/file"))
    }

    /**
     * Tests isSupported() returns false for unsupported protocols.
     */
    @Test
    fun `isSupported returns false for unsupported protocols`() {
        assertFalse(DownloadProtocol.isSupported("file:///path"))
        assertFalse(DownloadProtocol.isSupported("ws://example.com"))
        assertFalse(DownloadProtocol.isSupported("mailto:user@example.com"))
    }

    /**
     * Tests supportedSchemes() returns all protocol schemes.
     */
    @Test
    fun `supportedSchemes returns all 7 protocols`() {
        val schemes = DownloadProtocol.supportedSchemes()

        assertEquals(7, schemes.size)
        assertTrue(schemes.contains("http"))
        assertTrue(schemes.contains("https"))
        assertTrue(schemes.contains("ftp"))
        assertTrue(schemes.contains("ftps"))
        assertTrue(schemes.contains("sftp"))
        assertTrue(schemes.contains("smb"))
        assertTrue(schemes.contains("webdav"))
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
        assertTrue(protocolString.contains("SFTP"))
        assertTrue(protocolString.contains(", ")) // Comma-separated
    }

    /**
     * Tests protocol enum has exactly 7 values.
     */
    @Test
    fun `enum has exactly 7 supported protocols`() {
        val protocols = DownloadProtocol.values()

        assertEquals(7, protocols.size)
        assertEquals(DownloadProtocol.HTTP, protocols[0])
        assertEquals(DownloadProtocol.HTTPS, protocols[1])
        assertEquals(DownloadProtocol.FTP, protocols[2])
        assertEquals(DownloadProtocol.FTPS, protocols[3])
        assertEquals(DownloadProtocol.SFTP, protocols[4])
        assertEquals(DownloadProtocol.SMB, protocols[5])
        assertEquals(DownloadProtocol.WEBDAV, protocols[6])
    }

    /**
     * Tests case-insensitive protocol matching.
     */
    @Test
    fun `protocol detection is case insensitive`() {
        assertEquals(DownloadProtocol.HTTP, DownloadProtocol.fromUrl("HTTP://EXAMPLE.COM/FILE"))
        assertEquals(DownloadProtocol.HTTPS, DownloadProtocol.fromUrl("HTTPS://EXAMPLE.COM/FILE"))
        assertEquals(DownloadProtocol.FTP, DownloadProtocol.fromUrl("FTP://SERVER.COM/FILE"))
        assertEquals(DownloadProtocol.SFTP, DownloadProtocol.fromUrl("SFTP://SERVER.COM/FILE"))
    }

    /**
     * Tests resume support varies by protocol.
     */
    @Test
    fun `resume support varies by protocol`() {
        // Protocols that support resume
        assertTrue(DownloadProtocol.HTTP.supportsResume)
        assertTrue(DownloadProtocol.HTTPS.supportsResume)
        assertTrue(DownloadProtocol.FTP.supportsResume)
        assertTrue(DownloadProtocol.FTPS.supportsResume)
        assertTrue(DownloadProtocol.SFTP.supportsResume)
        assertTrue(DownloadProtocol.WEBDAV.supportsResume)
        
        // Protocols that don't support resume
        assertFalse(DownloadProtocol.SMB.supportsResume)
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
        assertTrue(DownloadProtocol.SFTP.requiresAuthentication)
        assertTrue(DownloadProtocol.SMB.requiresAuthentication)
        assertTrue(DownloadProtocol.WEBDAV.requiresAuthentication)
    }
}

