package ir.hrka.download_manager.entities

/**
 * Data model representing the metadata required to download and manage a file.
 *
 * @property fileUrl The URL from which the file should be downloaded.
 * @property fileName The base name of the file (without suffix).
 * @property fileExtension The file's extension or suffix (e.g., "zip", "apk").
 * @property fileDirName The name of the directory where the file should be stored.
 * @property fileVersion Optional version identifier for the file.
 * @property totalBytes The total size of the file in bytes (used for progress estimation).
 * @property accessToken Optional bearer token used to authorize the file download.
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
     * Returns the full filename with an optional appended string, followed by the file suffix.
     *
     * @param append Optional string to insert between the base filename and suffix.
     * @return The full filename (e.g., "myfile_v2.zip").
     */
    fun getFullName(append: String = ""): String =
        if (append.isNotEmpty()) "${fileName}_$append.$fileExtension" else "$fileName.$fileExtension"
}

