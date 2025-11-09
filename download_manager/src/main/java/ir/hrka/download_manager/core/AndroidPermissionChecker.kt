package ir.hrka.download_manager.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Android-specific implementation of [PermissionChecker] that checks actual runtime permissions.
 *
 * This class handles Android's permission model for API 31+ (minSdk = 31):
 * - API 31+: Scoped storage with app-specific directories (no permission needed)
 * - API 31+: Public directories via MediaStore (no permission needed)
 * - API 31+: Custom paths require MANAGE_EXTERNAL_STORAGE (special permission)
 * - API 33+: POST_NOTIFICATIONS for notifications (runtime permission)
 *
 * **Note**: Library requires minSdk = 31 (Android 12). For older devices, the permission
 * checks for API < 31 have been removed as dead code.
 *
 * Usage:
 * ```kotlin
 * val permissionChecker = AndroidPermissionChecker(context)
 * val downloader = OkHttpDownloader(permissionChecker = permissionChecker)
 * ```
 *
 * @property context Android application or activity context
 * @since 1.0.0
 */
class AndroidPermissionChecker(
    private val context: Context
) : PermissionChecker {

    /**
     * Checks if the INTERNET permission is granted.
     *
     * On Android, INTERNET is a normal permission granted at install time.
     * However, it can be revoked by:
     * - Enterprise Device Management (MDM) policies
     * - User action on certain devices/ROMs
     * - App permission management tools
     *
     * @return true if INTERNET permission is granted
     */
    override fun hasInternetPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED


    /**
     * Checks if storage write permission is granted for the destination file.
     *
     * Permission requirements vary by Android version and storage location:
     *
     * **Internal Storage (app-specific directories):**
     * - /data/data/{package}/files/ - NO permission required (any API level)
     * - /data/data/{package}/cache/ - NO permission required (any API level)
     * - context.filesDir - NO permission required
     * - context.cacheDir - NO permission required
     *
     * **External Storage (minSdk = 31, API 31+):**
     * - App-specific external directories - NO permission required:
     *   - context.getExternalFilesDir() - /sdcard/Android/data/{package}/files/
     *   - context.getExternalCacheDir() - /sdcard/Android/data/{package}/cache/
     * - Shared storage (Downloads, Pictures, etc.) - Uses MediaStore API, no permission
     * - Other external paths - Requires MANAGE_EXTERNAL_STORAGE (special permission)
     *
     * @param destination The file path where the download will be saved
     * @return true if write permission is granted or not required
     */
    override fun hasStoragePermission(destination: File): Boolean {
        // Check if destination is in app-specific internal storage (no permission needed)
        if (isInternalStorage(destination)) return true

        // Check if destination is in app-specific external storage (no permission needed on API 29+)
        // minSdk is 31, so this is always true - simplified check
        if (isAppSpecificExternalStorage(destination)) return true

        // For API 30+ (minSdk = 31 means we're always >= 30)
        // Check if destination is in public directories (Download, Pictures, etc.)
        // These use MediaStore API which doesn't require permissions
        if (isPublicDirectory(destination)) return true

        // For other paths, check MANAGE_EXTERNAL_STORAGE (special permission)
        return try {
            Environment.isExternalStorageManager()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the POST_NOTIFICATIONS permission is granted (API 33+).
     *
     * This permission is only required on Android 13 (API 33) and above.
     * On earlier versions, this method always returns true.
     *
     * @return true if notification permission is granted or not required
     */
    override fun hasNotificationPermission(): Boolean {
        // Notification permission is only required on API 33+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Performs a comprehensive check of all required permissions.
     *
     * @param destination The file path where the download will be saved
     * @param requireNotifications Whether notification permission should be checked
     * @return PermissionCheckResult with details about all permission checks
     */
    override fun checkAllPermissions(
        destination: File,
        requireNotifications: Boolean
    ): PermissionChecker.PermissionCheckResult {
        val hasInternet = hasInternetPermission()
        val hasStorage = hasStoragePermission(destination)
        val hasNotifications = if (requireNotifications) hasNotificationPermission() else true

        val missingPermissions = mutableListOf<String>()
        if (!hasInternet) missingPermissions.add("INTERNET")
        if (!hasStorage) {
            // minSdk = 31, so always >= API 30 (R)
            missingPermissions.add("Storage access (may require MANAGE_EXTERNAL_STORAGE)")
        }
        if (requireNotifications && !hasNotifications) {
            missingPermissions.add("POST_NOTIFICATIONS")
        }

        return PermissionChecker.PermissionCheckResult(
            hasInternet = hasInternet,
            hasStorage = hasStorage,
            hasNotifications = hasNotifications,
            allGranted = hasInternet && hasStorage && hasNotifications,
            missingPermissions = missingPermissions
        )
    }

    /**
     * Checks if the destination is in app-specific internal storage.
     *
     * Internal storage paths:
     * - /data/data/{package}/files/
     * - /data/data/{package}/cache/
     * - /data/user/0/{package}/
     *
     * @param destination The destination file
     * @return true if in internal storage
     */
    private fun isInternalStorage(destination: File): Boolean {
        val dataDir = context.applicationInfo.dataDir
        val canonicalDataDir = File(dataDir).canonicalPath
        val canonicalDestination = try {
            destination.canonicalPath
        } catch (e: Exception) {
            destination.absolutePath
        }

        return canonicalDestination.startsWith(canonicalDataDir)
    }

    /**
     * Checks if the destination is in app-specific external storage.
     *
     * App-specific external paths (no permission required on API 29+):
     * - /sdcard/Android/data/{package}/files/
     * - /sdcard/Android/data/{package}/cache/
     *
     * @param destination The destination file
     * @return true if in app-specific external storage
     */
    private fun isAppSpecificExternalStorage(destination: File): Boolean {
        val externalFilesDir = context.getExternalFilesDir(null)
        val externalCacheDir = context.externalCacheDir

        val canonicalDestination = try {
            destination.canonicalPath
        } catch (e: Exception) {
            destination.absolutePath
        }

        // Check if in external files directory
        if (externalFilesDir != null) {
            val canonicalFilesDir = try {
                externalFilesDir.canonicalPath
            } catch (e: Exception) {
                externalFilesDir.absolutePath
            }
            if (canonicalDestination.startsWith(canonicalFilesDir)) {
                return true
            }
        }

        // Check if in external cache directory
        if (externalCacheDir != null) {
            val canonicalCacheDir = try {
                externalCacheDir.canonicalPath
            } catch (e: Exception) {
                externalCacheDir.absolutePath
            }
            if (canonicalDestination.startsWith(canonicalCacheDir)) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if the destination is in a public directory accessible via MediaStore.
     *
     * Public directories (API 29+):
     * - Download/
     * - Pictures/
     * - DCIM/
     * - Movies/
     * - Music/
     * - Documents/
     *
     * These are accessible via MediaStore API without permissions.
     *
     * @param destination The destination file
     * @return true if in a public directory
     */
    private fun isPublicDirectory(destination: File): Boolean {
        val canonicalDestination = try {
            destination.canonicalPath
        } catch (e: Exception) {
            destination.absolutePath
        }.lowercase()

        // Get external storage root
        val externalStorageRoot = try {
            Environment.getExternalStorageDirectory().canonicalPath.lowercase()
        } catch (e: Exception) {
            "/storage/emulated/0"
        }

        // Check if in public directories
        val publicDirs = listOf(
            "$externalStorageRoot/download",
            "$externalStorageRoot/pictures",
            "$externalStorageRoot/dcim",
            "$externalStorageRoot/movies",
            "$externalStorageRoot/music",
            "$externalStorageRoot/documents"
        )

        return publicDirs.any { canonicalDestination.startsWith(it) }
    }
}

