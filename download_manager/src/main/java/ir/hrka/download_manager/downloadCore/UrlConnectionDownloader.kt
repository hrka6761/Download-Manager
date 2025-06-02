package ir.hrka.download_manager.downloadCore

import android.content.Context
import android.os.Environment
import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.utilities.DownloadListener
import ir.hrka.download_manager.utilities.StorageType
import ir.hrka.download_manager.utilities.StorageType.InternalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.jvm.Throws

/**
 * A concrete implementation of the [Downloader] interface that performs file downloads
 * using [HttpURLConnection].
 *
 * This class handles file downloading over HTTP/HTTPS based on the specified [StorageType],
 * which determines where the downloaded files are stored (e.g., app-specific storage or
 * public Downloads directory).
 *
 * ## Features:
 * - Supports download resumption via "Range" header.
 * - Monitors and reports progress at regular intervals.
 * - Stores files in internal or shared external storage based on configuration.
 *
 * ## Usage Example:
 * ```
 * val downloader = UrlConnectionDownloader(StorageType.PUBLIC_DOWNLOAD)
 * downloader.download(context, fileData, listener)
 * ```
 *
 * @property storageType Determines where downloaded files will be saved (internal or shared storage).
 *
 * @constructor Initializes the downloader with the specified storage location.
 *
 * @see Downloader Interface for base contract.
 * @see HttpURLConnection Used for performing the HTTP download.
 */
internal class UrlConnectionDownloader(val storageType: StorageType) : Downloader {

    /**
     * Downloads a file from the specified URL using [HttpURLConnection] and saves it
     * to the appropriate storage location determined by [storageType].
     *
     * Progress updates and completion status are communicated through [DownloadListener].
     *
     * @param context The application context used for internal storage access.
     * @param fileData The metadata describing the file to be downloaded.
     * @param listener The callback listener to receive progress and result updates.
     */
    override suspend fun download(
        context: Context,
        fileData: FileDataModel,
        listener: DownloadListener
    ) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        return withContext(Dispatchers.IO) {
            try {
                listener.onStartDownload()

                // Create a file to write input bytes into
                val outputFile =
                    if (storageType == InternalStorage)
                        createFileInInternalStorage(
                            context = context,
                            directoryName = fileData.fileDirName,
                            version = fileData.fileVersion,
                            fileName = fileData.fileName
                        )
                    else
                        createFileInSharedStorage(
                            directoryName = fileData.fileDirName,
                            version = fileData.fileVersion,
                            fileName = fileData.fileName
                        )

                // Create a connection with server to read bytes from
                val connection = provideConnection(
                    fileUrl = fileData.fileUrl,
                    accessToken = fileData.accessToken,
                    outputFile = outputFile
                )

                connection.connect()

                var downloadedBytes = if (
                    connection.responseCode == HttpURLConnection.HTTP_OK ||
                    connection.responseCode == HttpURLConnection.HTTP_PARTIAL
                ) getStartByte(connection) else 0L

                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(outputFile, true)
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                var lastSetProgressTs: Long = 0
                var deltaBytes = 0L
                val bytesReadSizeBuffer: MutableList<Long> = mutableListOf()
                val bytesReadLatencyBuffer: MutableList<Long> = mutableListOf()

                // Start reading bytes from the file and writing them to the local file.
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    deltaBytes += bytesRead

                    // Report progress every 200 ms.
                    val curTs = System.currentTimeMillis()

                    if (curTs - lastSetProgressTs > 200) {
                        // Calculate download rate.
                        var bytesPerMs = 0f
                        if (lastSetProgressTs != 0L) {
                            if (bytesReadSizeBuffer.size == 5) {
                                bytesReadSizeBuffer.removeAt(0)
                            }
                            bytesReadSizeBuffer.add(deltaBytes)
                            if (bytesReadLatencyBuffer.size == 5) {
                                bytesReadLatencyBuffer.removeAt(0)
                            }
                            bytesReadLatencyBuffer.add(curTs - lastSetProgressTs)
                            deltaBytes = 0L
                            bytesPerMs = bytesReadSizeBuffer.sum()
                                .toFloat() / bytesReadLatencyBuffer.sum()
                        }

                        // Calculate remaining seconds
                        var remainingMs = 0f
                        if (bytesPerMs > 0f && fileData.totalBytes > 0L) {
                            remainingMs = (fileData.totalBytes - downloadedBytes) / bytesPerMs
                        }

                        listener.onProgressUpdate(
                            fileName = fileData.fileName,
                            downloadedBytes = downloadedBytes,
                            downloadRate = bytesPerMs * 1000,
                            remainingTime = remainingMs
                        )

                        lastSetProgressTs = curTs
                    }
                }

                listener.onDownloadCompleted()
            } catch (e: Exception) {
                listener.onDownloadFailed(e)
            } finally {
                outputStream?.close()
                inputStream?.close()
            }
        }
    }


    /**
     * Creates a file in the app's internal (private) storage directory.
     *
     * @param context The application context.
     * @param directoryName The name of the subdirectory.
     * @param version Optional version subfolder.
     * @param fileName Name of the file to be created.
     * @return A [File] reference for writing the download data.
     */
    private fun createFileInInternalStorage(
        context: Context,
        directoryName: String,
        version: String?,
        fileName: String
    ): File {
        val directories = mutableListOf(directoryName)
        version?.let { directories.add(it) }

        val outputDir = File(
            context.getExternalFilesDir(null),
            directories.joinToString(separator = File.separator)
        )

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        return File(listOf(outputDir, fileName).joinToString(separator = File.separator))
    }

    /**
     * Creates a file within the shared public Downloads directory on external storage.
     *
     * This method builds a directory path using the provided `directoryName` and optional `version`,
     * and attempts to create the necessary directory structure under
     * [Environment.DIRECTORY_DOWNLOADS]. The final file will be located at:
     * `Downloads/directoryName/version/fileName` (if `version` is not null).
     *
     * This function requires the appropriate shared storage permissions depending on
     * the Android version:
     * - For Android 10 (API 29) and below: `WRITE_EXTERNAL_STORAGE` (with `android:requestLegacyExternalStorage="true"` in manifest).
     * - For Android 11 (API 30) and above: `MANAGE_EXTERNAL_STORAGE` and the user must grant "All files access".
     *
     * If the required permissions are not granted, a [SecurityException] is thrown.
     *
     * @param directoryName The name of the subdirectory inside the Downloads folder.
     * @param version Optional version string used to further nest the directory.
     * @param fileName The name of the file to create.
     * @return A [File] object pointing to the created file location.
     * @throws SecurityException If the app lacks permission to write to shared storage.
     *
     * @see Environment.getExternalStoragePublicDirectory
     * @see Environment.DIRECTORY_DOWNLOADS
     * @see Environment.isExternalStorageManager
     */
    @Throws(SecurityException::class)
    private fun createFileInSharedStorage(
        directoryName: String,
        version: String?,
        fileName: String
    ): File {
        if (!Environment.isExternalStorageManager()) {
            throw SecurityException(
                "Missing permission to access shared storage. " +
                        "Ensure MANAGE_EXTERNAL_STORAGE or WRITE_EXTERNAL_STORAGE permission is granted."
            )
        }

        val directories = mutableListOf(directoryName)
        version?.let { directories.add(it) }

        val outputDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            directories.joinToString(separator = File.separator)
        )

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        return File(outputDir.absolutePath, fileName)
    }


    /**
     * Provides an [HttpURLConnection] with optional authorization and download range headers.
     *
     * @param fileUrl The file URL to connect to.
     * @param accessToken Optional bearer token for authenticated requests.
     * @param outputFile File to be written to; used for resuming partial downloads.
     * @return A configured [HttpURLConnection] instance.
     */
    private fun provideConnection(
        fileUrl: String,
        accessToken: String?,
        outputFile: File
    ): HttpURLConnection {
        val url = URL(fileUrl)
        val connection = url.openConnection() as HttpURLConnection

        if (accessToken != null)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")

        val outputFileBytes = outputFile.length()
        if (outputFileBytes > 0)
            connection.setRequestProperty("Range", "bytes=$outputFileBytes-")

        return connection
    }

    /**
     * Parses the `Content-Range` header to determine the starting byte position of the download.
     *
     * @param connection An active [HttpURLConnection] with a response.
     * @return The starting byte position for a partial download.
     */
    private fun getStartByte(connection: HttpURLConnection): Long {
        val contentRange = connection.getHeaderField("Content-Range")
        var startByte = 0L
        var endByte = 0L

        contentRange?.let {
            // Parse the Content-Range header
            val rangeParts = contentRange.substringAfter("bytes ").split("/")
            val byteRange = rangeParts[0].split("-")
            startByte = byteRange[0].toLong()
            endByte = byteRange[1].toLong()
        }

        return startByte
    }
}