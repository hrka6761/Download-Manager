package ir.hrka.download_manager.core

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Comprehensive test suite for AndroidPermissionChecker.
 *
 * **Note**: Library requires minSdk = 31 (Android 12+). Tests for API < 31 have been
 * removed as they test dead code that can never execute in production.
 *
 * Tests cover all scenarios documented at:
 * https://developer.android.com/training/data-storage
 * https://developer.android.com/training/data-storage/app-specific
 * https://developer.android.com/training/data-storage/shared/media
 *
 * Categories:
 * - Internet permission checks
 * - Internal storage (app-specific, always accessible, sandboxed per package)
 * - External storage - app-specific (scoped storage, API 29+, can return null)
 * - External storage - shared (public directories via MediaStore, API 29+)
 * - MANAGE_EXTERNAL_STORAGE for all-files access (API 30+, special permission)
 * - Notification permission (API 33+)
 * - Package isolation (security: apps cannot access other apps' directories)
 * - Real-world scenarios (null returns, subdirectories, mount point variations)
 * - Edge cases (special characters, unicode, very long paths)
 *
 * Total Tests: 41 (optimized for minSdk = 31, includes critical security & real-world scenarios)
 */
@RunWith(AndroidJUnit4::class)
class AndroidPermissionCheckerTest {
    
    private lateinit var context: Context
    private lateinit var mockContext: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockContext = mockk<Context>(relaxed = true)
        
        // Setup default mock responses
        every { mockContext.applicationInfo } returns ApplicationInfo().apply {
            dataDir = "/data/data/com.example.test"
        }
        every { mockContext.packageName } returns "com.example.test"
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    // ============================================================================
    // INTERNET PERMISSION TESTS (3 tests)
    // ============================================================================
    
    @Test
    fun `hasInternetPermission returns true when permission granted`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasInternetPermission())
    }
    
    @Test
    fun `hasInternetPermission returns false when permission denied`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_DENIED
        
        val checker = AndroidPermissionChecker(mockContext)
        assertFalse(checker.hasInternetPermission())
    }
    
    @Test
    fun `hasInternetPermission returns false when permission revoked by MDM`() {
        // Simulate enterprise MDM revoking INTERNET permission
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_DENIED
        
        val checker = AndroidPermissionChecker(mockContext)
        assertFalse(checker.hasInternetPermission())
    }
    
    // ============================================================================
    // STORAGE PERMISSION - INTERNAL STORAGE (5 tests)
    // Per: https://developer.android.com/training/data-storage/app-specific#internal
    // "Files stored in internal storage are private to your app by default"
    // ============================================================================
    
    @Test
    fun `hasStoragePermission returns true for internal storage files directory`() {
        val internalFile = File("/data/data/com.example.test/files/download.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(internalFile))
    }
    
    @Test
    fun `hasStoragePermission returns true for internal storage cache directory`() {
        val cacheFile = File("/data/data/com.example.test/cache/temp.tmp")
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(cacheFile))
    }
    
    @Test
    fun `hasStoragePermission returns true for context filesDir`() {
        val filesDir = File("/data/data/com.example.test/files")
        every { mockContext.filesDir } returns filesDir
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(filesDir))
    }
    
    @Test
    fun `hasStoragePermission returns true for context cacheDir`() {
        val cacheDir = File("/data/data/com.example.test/cache")
        every { mockContext.cacheDir } returns cacheDir
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(cacheDir))
    }
    
    @Test
    fun `hasStoragePermission rejects other app internal storage - SECURITY CRITICAL`() {
        // Per Android docs: "Files are private to your app and cannot be accessed by other apps"
        // https://developer.android.com/training/data-storage/app-specific#internal
        val otherAppFile = File("/data/data/com.other.app/files/sensitive.dat")
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/storage/emulated/0")
        every { Environment.isExternalStorageManager() } returns false
        
        val checker = AndroidPermissionChecker(mockContext)
        assertFalse(
            "Must reject other app's internal storage (package sandboxing)",
            checker.hasStoragePermission(otherAppFile)
        )
        
        unmockkAll()
    }
    
    // ============================================================================
    // STORAGE PERMISSION - APP-SPECIFIC EXTERNAL (API 31+) (4 tests)
    // Per: https://developer.android.com/training/data-storage/app-specific#external
    // "No permissions are required and the files are removed when your app is uninstalled"
    // "This method returns null if external storage is not currently mounted"
    // ============================================================================
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission on API 31 allows app-specific external files without permission`() {
        val appExternalFiles = File("/sdcard/Android/data/com.example.test/files/download.pdf")
        every { mockContext.getExternalFilesDir(null) } returns File("/sdcard/Android/data/com.example.test/files")
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(appExternalFiles))
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission on API 31 allows app-specific cache no permission required`() {
        val appCache = File("/sdcard/Android/data/com.example.test/cache/image_cache.jpg")
        every { mockContext.externalCacheDir } returns File("/sdcard/Android/data/com.example.test/cache")
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(appCache))
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission when external storage unmounted returns null - REAL WORLD`() {
        // Per Android docs: "This method returns null if external storage is not currently mounted"
        // https://developer.android.com/reference/android/content/Context#getExternalFilesDir(java.lang.String)
        val appExternalPath = File("/sdcard/Android/data/com.example.test/files/file.pdf")
        every { mockContext.getExternalFilesDir(null) } returns null
        every { mockContext.externalCacheDir } returns null
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/sdcard")
        every { Environment.isExternalStorageManager() } returns false
        
        val checker = AndroidPermissionChecker(mockContext)
        assertFalse(
            "When external storage unmounted (SD card removed), path requires MANAGE_EXTERNAL_STORAGE",
            checker.hasStoragePermission(appExternalPath)
        )
        
        unmockkAll()
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission rejects other app external storage - SECURITY CRITICAL`() {
        // Per scoped storage: "Your app has unrestricted access to app-specific storage" (YOUR app only)
        // https://developer.android.com/training/data-storage#scoped
        val otherAppExternal = File("/sdcard/Android/data/com.other.app/files/data.json")
        every { mockContext.getExternalFilesDir(null) } returns File("/sdcard/Android/data/com.example.test/files")
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/sdcard")
        every { Environment.isExternalStorageManager() } returns false
        
        val checker = AndroidPermissionChecker(mockContext)
        assertFalse(
            "Must reject other app's external directory (package sandboxing)",
            checker.hasStoragePermission(otherAppExternal)
        )
        
        unmockkAll()
    }
    
    // ============================================================================
    // STORAGE PERMISSION - PUBLIC DIRECTORIES (API 31+) (4 tests)
    // Per: https://developer.android.com/training/data-storage/shared/media
    // "Apps that target Android 10+ can access media files contributed by other apps"
    // MediaStore supports subdirectories within public directories
    // ============================================================================
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission on API 31 allows Download directory via MediaStore`() {
        val downloadFile = File("/storage/emulated/0/Download/document.pdf")
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/storage/emulated/0")
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(downloadFile))
        
        unmockkAll()
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission on API 31 allows Pictures directory`() {
        val pictureFile = File("/sdcard/Pictures/photo.jpg")
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/sdcard")
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(pictureFile))
        
        unmockkAll()
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission on API 31 allows all public directories (DCIM Movies Music Documents)`() {
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/storage/emulated/0")
        
        val checker = AndroidPermissionChecker(mockContext)
        
        // Test all public directories at once
        val publicDirFiles = listOf(
            File("/storage/emulated/0/DCIM/Camera/IMG_001.jpg"),
            File("/storage/emulated/0/Movies/video.mp4"),
            File("/storage/emulated/0/Music/song.mp3"),
            File("/storage/emulated/0/Documents/report.docx")
        )
        
        publicDirFiles.forEach { file ->
            assertTrue(
                "Public directory ${file.parent} should not require permission",
                checker.hasStoragePermission(file)
            )
        }
        
        unmockkAll()
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission allows subdirectories within public directories - MediaStore`() {
        // Per Android docs: MediaStore allows organizing files in subdirectories
        // https://developer.android.com/training/data-storage/shared/media#add-item
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/storage/emulated/0")
        
        val checker = AndroidPermissionChecker(mockContext)
        
        // Test deep subdirectories within public directories
        val subdirFiles = listOf(
            File("/storage/emulated/0/Download/Work/Projects/2024/report.pdf"),
            File("/storage/emulated/0/Pictures/Vacation/Summer/IMG_001.jpg"),
            File("/storage/emulated/0/Documents/Personal/Finance/tax.docx")
        )
        
        subdirFiles.forEach { file ->
            assertTrue(
                "Public directory subdirectory ${file.path} should not require permission",
                checker.hasStoragePermission(file)
            )
        }
        
        unmockkAll()
    }
    
    // ============================================================================
    // STORAGE PERMISSION - MANAGE_EXTERNAL_STORAGE (API 31+) (4 tests)
    // Per: https://developer.android.com/training/data-storage/manage-all-files
    // Special permission for all-files access
    // ============================================================================
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission on API 31 checks MANAGE_EXTERNAL_STORAGE for custom paths`() {
        val customPath = File("/sdcard/CustomFolder/data.bin")
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/sdcard")
        every { Environment.isExternalStorageManager() } returns true
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(customPath))
        
        unmockkAll()
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission on API 31 returns false when MANAGE_EXTERNAL_STORAGE not granted`() {
        val customPath = File("/storage/emulated/0/MyCustomApp/file.dat")
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/storage/emulated/0")
        every { Environment.isExternalStorageManager() } returns false
        
        val checker = AndroidPermissionChecker(mockContext)
        assertFalse(checker.hasStoragePermission(customPath))
        
        unmockkAll()
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31
    fun `hasStoragePermission on API 31 handles Environment exception gracefully`() {
        val customPath = File("/sdcard/Unknown/file.txt")
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/sdcard")
        every { Environment.isExternalStorageManager() } throws SecurityException("No permission")
        
        val checker = AndroidPermissionChecker(mockContext)
        assertFalse(checker.hasStoragePermission(customPath))
        
        unmockkAll()
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasStoragePermission internal and app-specific work WITHOUT MANAGE_EXTERNAL_STORAGE`() {
        // CRITICAL: Per Android docs, app-specific storage ALWAYS accessible without permissions
        // https://developer.android.com/training/data-storage/app-specific
        mockkStatic(Environment::class)
        every { Environment.isExternalStorageManager() } returns false
        every { Environment.getExternalStorageDirectory() } returns File("/storage/emulated/0")
        every { mockContext.getExternalFilesDir(null) } returns File("/sdcard/Android/data/com.example.test/files")
        
        val checker = AndroidPermissionChecker(mockContext)
        
        // Internal storage must work WITHOUT special permission
        val internalFile = File("/data/data/com.example.test/files/data.json")
        assertTrue(
            "Internal storage accessible without MANAGE_EXTERNAL_STORAGE",
            checker.hasStoragePermission(internalFile)
        )
        
        // App-specific external must work WITHOUT special permission
        val appSpecific = File("/sdcard/Android/data/com.example.test/files/file.pdf")
        assertTrue(
            "App-specific external accessible without MANAGE_EXTERNAL_STORAGE",
            checker.hasStoragePermission(appSpecific)
        )
        
        // Public directories must work WITHOUT special permission
        val publicDir = File("/storage/emulated/0/Download/file.pdf")
        assertTrue(
            "Public directories accessible without MANAGE_EXTERNAL_STORAGE",
            checker.hasStoragePermission(publicDir)
        )
        
        unmockkAll()
    }
    
    // ============================================================================
    // NOTIFICATION PERMISSION (3 tests - API 31+)
    // ============================================================================
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `hasNotificationPermission returns true on API 31 (before API 33 requirement)`() {
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasNotificationPermission())
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // API 33
    fun `hasNotificationPermission on API 33 returns true when granted`() {
        every {
            mockContext.checkPermission(Manifest.permission.POST_NOTIFICATIONS, any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasNotificationPermission())
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // API 33
    fun `hasNotificationPermission on API 33 returns false when denied`() {
        every {
            mockContext.checkPermission(Manifest.permission.POST_NOTIFICATIONS, any(), any())
        } returns PackageManager.PERMISSION_DENIED
        
        val checker = AndroidPermissionChecker(mockContext)
        assertFalse(checker.hasNotificationPermission())
    }
    
    // ============================================================================
    // COMPREHENSIVE checkAllPermissions() (7 tests - API 31+)
    // ============================================================================
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `checkAllPermissions returns all granted when all permissions available`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        val internalFile = File("/data/data/com.example.test/files/download.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        val result = checker.checkAllPermissions(internalFile, requireNotifications = false)
        
        assertTrue(result.allGranted)
        assertTrue(result.hasInternet)
        assertTrue(result.hasStorage)
        assertTrue(result.hasNotifications)
        assertTrue(result.missingPermissions.isEmpty())
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `checkAllPermissions returns missing internet when internet denied`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_DENIED
        val internalFile = File("/data/data/com.example.test/files/download.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        val result = checker.checkAllPermissions(internalFile, requireNotifications = false)
        
        assertFalse(result.allGranted)
        assertFalse(result.hasInternet)
        assertTrue(result.hasStorage)
        assertEquals(listOf("INTERNET"), result.missingPermissions)
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `checkAllPermissions on API 31 returns missing storage for custom paths without MANAGE_EXTERNAL_STORAGE`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/sdcard")
        every { Environment.isExternalStorageManager() } returns false
        val customPath = File("/sdcard/CustomApp/file.dat")
        
        val checker = AndroidPermissionChecker(mockContext)
        val result = checker.checkAllPermissions(customPath, requireNotifications = false)
        
        assertFalse(result.allGranted)
        assertTrue(result.hasInternet)
        assertFalse(result.hasStorage)
        assertTrue(result.missingPermissions.any { it.contains("MANAGE_EXTERNAL_STORAGE") })
        
        unmockkAll()
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // API 33
    fun `checkAllPermissions on API 33 returns missing notification when required`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        every {
            mockContext.checkPermission(Manifest.permission.POST_NOTIFICATIONS, any(), any())
        } returns PackageManager.PERMISSION_DENIED
        val internalFile = File("/data/data/com.example.test/files/download.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        val result = checker.checkAllPermissions(internalFile, requireNotifications = true)
        
        assertFalse(result.allGranted)
        assertTrue(result.hasInternet)
        assertTrue(result.hasStorage)
        assertFalse(result.hasNotifications)
        assertEquals(listOf("POST_NOTIFICATIONS"), result.missingPermissions)
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // API 33
    fun `checkAllPermissions on API 33 ignores notification when not required`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        every {
            mockContext.checkPermission(Manifest.permission.POST_NOTIFICATIONS, any(), any())
        } returns PackageManager.PERMISSION_DENIED
        val internalFile = File("/data/data/com.example.test/files/download.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        val result = checker.checkAllPermissions(internalFile, requireNotifications = false)
        
        assertTrue(result.allGranted)
        assertTrue(result.hasInternet)
        assertTrue(result.hasStorage)
        assertTrue(result.hasNotifications) // Not checked, so defaults to true
        assertTrue(result.missingPermissions.isEmpty())
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `checkAllPermissions returns multiple missing permissions for custom path`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_DENIED
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/sdcard")
        every { Environment.isExternalStorageManager() } returns false
        val customPath = File("/sdcard/CustomApp/file.dat")
        
        val checker = AndroidPermissionChecker(mockContext)
        val result = checker.checkAllPermissions(customPath, requireNotifications = false)
        
        assertFalse(result.allGranted)
        assertFalse(result.hasInternet)
        assertFalse(result.hasStorage)
        assertEquals(2, result.missingPermissions.size)
        assertTrue(result.missingPermissions.contains("INTERNET"))
        assertTrue(result.missingPermissions.any { it.contains("MANAGE_EXTERNAL_STORAGE") })
        
        unmockkAll()
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // API 33
    fun `checkAllPermissions on API 33 with all permissions denied for custom path`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_DENIED
        every {
            mockContext.checkPermission(Manifest.permission.POST_NOTIFICATIONS, any(), any())
        } returns PackageManager.PERMISSION_DENIED
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/sdcard")
        every { Environment.isExternalStorageManager() } returns false
        val externalPath = File("/sdcard/MyApp/file.dat")
        
        val checker = AndroidPermissionChecker(mockContext)
        val result = checker.checkAllPermissions(externalPath, requireNotifications = true)
        
        assertFalse(result.allGranted)
        assertFalse(result.hasInternet)
        assertFalse(result.hasStorage)
        assertFalse(result.hasNotifications)
        assertEquals(3, result.missingPermissions.size)
        
        unmockkAll()
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `checkAllPermissions with requireNotifications false uses default true for notifications`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        val internalFile = File("/data/data/com.example.test/files/download.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        val result = checker.checkAllPermissions(internalFile) // default requireNotifications = false
        
        assertTrue(result.allGranted)
        assertTrue(result.hasNotifications)
    }
    
    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 (minSdk)
    fun `checkAllPermissions data class contains correct values`() {
        every {
            mockContext.checkPermission(Manifest.permission.INTERNET, any(), any())
        } returns PackageManager.PERMISSION_GRANTED
        val internalFile = File("/data/data/com.example.test/files/download.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        val result = checker.checkAllPermissions(internalFile, requireNotifications = false)
        
        // Verify data class structure
        assertTrue(result.hasInternet)
        assertTrue(result.hasStorage)
        assertTrue(result.hasNotifications)
        assertTrue(result.allGranted)
        assertTrue(result.missingPermissions.isEmpty())
    }
    
    // ============================================================================
    // NO-OP PERMISSION CHECKER (5 tests)
    // ============================================================================
    
    @Test
    fun `noOp checker always returns true for internet permission`() {
        val checker = PermissionChecker.noOp()
        assertTrue(checker.hasInternetPermission())
    }
    
    @Test
    fun `noOp checker always returns true for storage permission`() {
        val checker = PermissionChecker.noOp()
        val anyFile = File("/any/path/to/file.txt")
        assertTrue(checker.hasStoragePermission(anyFile))
    }
    
    @Test
    fun `noOp checker always returns true for notification permission`() {
        val checker = PermissionChecker.noOp()
        assertTrue(checker.hasNotificationPermission())
    }
    
    @Test
    fun `noOp checker checkAllPermissions returns all granted`() {
        val checker = PermissionChecker.noOp()
        val anyFile = File("/any/path/file.txt")
        
        val result = checker.checkAllPermissions(anyFile, requireNotifications = true)
        
        assertTrue(result.allGranted)
        assertTrue(result.hasInternet)
        assertTrue(result.hasStorage)
        assertTrue(result.hasNotifications)
        assertTrue(result.missingPermissions.isEmpty())
    }
    
    @Test
    fun `noOp checker is singleton and reusable`() {
        val checker1 = PermissionChecker.noOp()
        val checker2 = PermissionChecker.noOp()
        
        assertSame(checker1, checker2)
    }
    
    // ============================================================================
    // EDGE CASES (4 tests)
    // ============================================================================
    
    @Test
    fun `hasStoragePermission handles non-existent destination path`() {
        val nonExistentPath = File("/data/data/com.example.test/files/nonexistent/deep/path/file.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        // Should still check permission based on path structure, not existence
        assertTrue(checker.hasStoragePermission(nonExistentPath))
    }
    
    @Test
    fun `hasStoragePermission handles destination with special characters`() {
        val specialPath = File("/data/data/com.example.test/files/file with spaces & special-chars.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(specialPath))
    }
    
    @Test
    fun `hasStoragePermission handles very long path`() {
        val longPath = StringBuilder("/data/data/com.example.test/files/")
        repeat(50) { longPath.append("very_long_directory_name/") }
        longPath.append("file.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(File(longPath.toString())))
    }
    
    @Test
    fun `hasStoragePermission handles path with unicode characters`() {
        val unicodePath = File("/data/data/com.example.test/files/文件_файл_ملف.pdf")
        
        val checker = AndroidPermissionChecker(mockContext)
        assertTrue(checker.hasStoragePermission(unicodePath))
    }
}

