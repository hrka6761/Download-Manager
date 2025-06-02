package ir.hrka.download_manager.utilities

/**
 * Enum representing the type of storage location where downloaded files will be saved.
 *
 * This is used to determine whether the download target should be in internal app-specific storage
 * or shared/public external storage (e.g., Downloads folder).
 *
 * @see InternalStorage
 * @see SharedStorage
 */
enum class StorageType {

    /**
     * Saves files in the app-specific internal storage.
     * Files stored here are private to the app and are removed when the app is uninstalled.
     * Uses `Context.getExternalFilesDir()` for storage path resolution.
     */
    InternalStorage,

    /**
     * Saves files in publicly accessible external storage.
     * Suitable for files that should be available to the user or other apps,
     * such as placing files in the Downloads directory.
     * Uses `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)`.
     */
    SharedStorage
}
