package ir.hrka.download_manager.entities

/**
 * Data model representing the metadata required to download and manage a file.
 *
 * @property fileUrl The URL from which the file should be downloaded.
 * @property fileName The base name of the file (without suffix).
 * @property fileSuffix The file's extension or suffix (e.g., "zip", "apk").
 * @property fileDirName The name of the directory where the file should be stored.
 * @property fileVersion Optional version identifier for the file.
 * @property fileMimeType Optional MIME type of the file (e.g., "application/zip").
 * @property isZip Whether the file is a ZIP archive that needs to be extracted after download.
 * @property unzippedDirName Optional name of the directory to extract the ZIP contents into.
 * @property totalBytes The total size of the file in bytes (used for progress estimation).
 * @property accessToken Optional bearer token used to authorize the file download.
 */
data class FileDataModel(
    val fileUrl: String,
    val fileName: String,
    val fileSuffix: String,
    val fileDirName: String,
    val fileVersion: String?,
    val fileMimeType: String?,
    val isZip: Boolean,
    val unzippedDirName: String?,
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
        if (append.isNotEmpty()) "${fileName}_$append.$fileSuffix" else "$fileName.$fileSuffix"
}

