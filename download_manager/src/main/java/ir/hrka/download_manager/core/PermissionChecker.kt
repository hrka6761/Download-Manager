package ir.hrka.download_manager.core

import java.io.File

/**
 * Interface for checking Android runtime permissions required for download operations.
 *
 * This abstraction allows the core download logic to remain Android-agnostic while
 * still supporting proper permission validation when running on Android.
 *
 * **Supported Android Versions**: API 31+ (Android 12+)
 *
 * Implementations should check:
 * - INTERNET permission (required for network access)
 * - Storage permissions (Scoped storage for API 31+, MANAGE_EXTERNAL_STORAGE for custom paths)
 * - POST_NOTIFICATIONS permission (for download notifications on API 33+)
 *
 * @since 1.0.0
 */
interface PermissionChecker {
    
    /**
     * Checks if the app has permission to access the internet.
     *
     * On Android, this typically checks for `android.permission.INTERNET`.
     * This is a normal permission that is granted at install time, but can be
     * revoked by enterprise policies or user action on some devices.
     *
     * @return true if internet access is permitted, false otherwise
     */
    fun hasInternetPermission(): Boolean
    
    /**
     * Checks if the app has permission to write to the specified file location.
     *
     * **Behavior for API 31+ (minSdk = 31):**
     * - **Internal storage** (/data/data/{package}/): No permission required
     * - **App-specific external** (/sdcard/Android/data/{package}/): No permission required (scoped storage)
     * - **Public directories** (Download, Pictures, DCIM, etc.): No permission required (MediaStore API)
     * - **Custom paths** (e.g., /sdcard/MyApp/): Requires MANAGE_EXTERNAL_STORAGE (special permission)
     *
     * @param destination The file path where the download will be saved
     * @return true if write access is permitted, false otherwise
     */
    fun hasStoragePermission(destination: File): Boolean
    
    /**
     * Checks if the app has permission to post download notifications.
     *
     * On Android 13+ (API 33+), this requires the POST_NOTIFICATIONS permission.
     * On earlier versions, this permission is not required and should return true.
     *
     * Note: This is only relevant if the download manager is configured to show notifications.
     *
     * @return true if notification permission is granted or not required, false otherwise
     */
    fun hasNotificationPermission(): Boolean
    
    /**
     * Performs a comprehensive check of all permissions required for a download operation.
     *
     * This is a convenience method that combines checks for internet, storage, and
     * optionally notifications based on the download configuration.
     *
     * @param destination The file path where the download will be saved
     * @param requireNotifications Whether notification permission is required for this download
     * @return A [PermissionCheckResult] containing details about which permissions are granted
     */
    fun checkAllPermissions(
        destination: File,
        requireNotifications: Boolean = false
    ): PermissionCheckResult
    
    /**
     * Result of a comprehensive permission check.
     *
     * @property hasInternet Whether internet permission is granted
     * @property hasStorage Whether storage write permission is granted for the destination
     * @property hasNotifications Whether notification permission is granted (or not required)
     * @property allGranted Whether all required permissions are granted
     * @property missingPermissions List of human-readable names of missing permissions
     */
    data class PermissionCheckResult(
        val hasInternet: Boolean,
        val hasStorage: Boolean,
        val hasNotifications: Boolean,
        val allGranted: Boolean,
        val missingPermissions: List<String>
    )
    
    companion object {
        /**
         * Creates a no-op permission checker that always returns true.
         *
         * This is useful for:
         * - Testing environments
         * - Non-Android platforms
         * - Cases where permission management is handled externally
         *
         * @return A PermissionChecker that always grants all permissions
         */
        fun noOp(): PermissionChecker = NoOpPermissionChecker
    }
}

/**
 * A no-op implementation that always grants all permissions.
 *
 * This is the default implementation used when no Android Context is available.
 */
private object NoOpPermissionChecker : PermissionChecker {
    override fun hasInternetPermission(): Boolean = true
    
    override fun hasStoragePermission(destination: File): Boolean = true
    
    override fun hasNotificationPermission(): Boolean = true
    
    override fun checkAllPermissions(
        destination: File,
        requireNotifications: Boolean
    ): PermissionChecker.PermissionCheckResult {
        return PermissionChecker.PermissionCheckResult(
            hasInternet = true,
            hasStorage = true,
            hasNotifications = true,
            allGranted = true,
            missingPermissions = emptyList()
        )
    }
}

