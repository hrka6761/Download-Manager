package ir.hrka.download_manager.entities

/**
 * Data class representing metadata for a file to be downloaded.
 *
 * This model holds all necessary information required to locate, identify, and store
 * a file during the download process.
 *
 * @property fileUrl The direct URL from which the file should be downloaded.
 * @property fileName The base name of the file, without extension.
 * @property fileExtension The extension of the file (e.g., "pdf", "zip").
 * @property fileDirName Optional name of the directory to save the file in.
 * @property fileVersion Optional version string to distinguish different versions of the file.
 * @property totalBytes The total size of the file in bytes, used for progress calculation.
 * @property accessToken Optional access token for authenticated download requests.
 *
 * @see Downloader
 * @see DownloadListener
 * @see FileProvider
 */
data class FileDataModel(
    val fileUrl: String,
    val fileName: String,
    val fileExtension: String,
    val fileDirName: String?,
    val fileVersion: String?,
    val totalBytes: Long,
    val accessToken: String?,
) {

    /**
     * Constructs the full filename, optionally appending a suffix before the extension.
     *
     * @param append A suffix to be added between the file name and its extension (e.g., version or identifier).
     * @return The full filename in the format "fileName[_append].extension".
     *
     * Examples:
     * - `getFullName()` → "report.pdf"
     * - `getFullName("v2")` → "report_v2.pdf"
     */
    fun getFullName(append: String = ""): String =
        if (append.isNotEmpty()) "${fileName}_$append.$fileExtension" else "$fileName.$fileExtension"
}

