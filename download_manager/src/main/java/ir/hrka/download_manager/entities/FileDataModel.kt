package ir.hrka.download_manager.entities

/**
 * Data model representing metadata required to download a file.
 *
 * This class contains all the necessary information for managing the download process,
 * including the file URL, target storage location, file name, and additional configuration
 * such as whether the file is a ZIP and authentication headers.
 *
 * @property fileUrl The direct URL of the file to be downloaded.
 * @property fileName The name to save the downloaded file as.
 * @property fileDirName The directory name (relative) where the file will be stored.
 * @property fileVersion Optional version string to help organize versioned downloads.
 * @property isZip Flag indicating whether the file is a ZIP archive that needs to be unzipped after download.
 * @property unzippedDirName Optional directory name where the contents of the ZIP will be extracted.
 * @property totalBytes Total size of the file in bytes, used for calculating download progress.
 * @property accessToken Optional bearer token for authenticated downloads.
 */
data class FileDataModel(
    val fileUrl: String,
    val fileName: String,
    val fileDirName: String,
    val fileVersion: String?,
    val isZip: Boolean,
    val unzippedDirName: String?,
    val totalBytes: Long,
    val accessToken: String?,
)

