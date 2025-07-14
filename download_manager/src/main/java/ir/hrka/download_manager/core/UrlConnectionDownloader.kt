package ir.hrka.download_manager.core

import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.file.FileProvider
import ir.hrka.download_manager.listeners.DownloadListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * @deprecated UrlConnectionDownloader is deprecated in favor of {@link OkHttpDownloader},
 * which provides better performance, improved error handling, and support for resuming downloads.
 *
 * <p>
 * Implementation of the {@link Downloader} interface using {@link HttpURLConnection}.
 * This class supports partial download resumption via HTTP range headers and reports
 * download progress periodically.
 * </p>
 *
 * <p>
 * The download runs on a background thread (via {@link kotlinx.coroutines.Dispatchers.IO}),
 * reads data from the input stream, writes it to the file system using {@link FileOutputStream},
 * and notifies a {@link DownloadListener} of progress, completion, or failure.
 * </p>
 *
 * @constructor Constructs a new instance of UrlConnectionDownloader with a file provider.
 * @param fileProvider Provides the destination {@link File} based on the {@link FileDataModel}.
 *
 * @see Downloader
 * @see OkHttpDownloader
 * @see FileDataModel
 * @see DownloadListener
 */
@Deprecated(
    message = "UrlConnectionDownloader is deprecated. Use OkHttpDownloader for better performance and resume support.",
    replaceWith = ReplaceWith("OkHttpDownloader(fileProvider)"),
    level = DeprecationLevel.WARNING
)
internal class UrlConnectionDownloader(
    private val fileProvider: FileProvider
) : Downloader {

    private val TAG = "DM_UrlConnectionDownloader"
    private lateinit var connection: HttpURLConnection
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null


    /**
     * Starts downloading the file described by {@link FileDataModel}.
     * <p>
     * The method supports resuming interrupted downloads if the server supports
     * byte-range requests and updates the {@link DownloadListener} with download rate
     * and estimated time remaining at 200ms intervals.
     * </p>
     *
     * @param fileData Metadata about the file to download, including URL and access token.
     * @param listener Callback for reporting download progress, completion, or failure.
     */
    override suspend fun startDownload(
        fileData: FileDataModel,
        listener: DownloadListener
    ) {
        var outputFile: File? = null

        withContext(Dispatchers.IO) {
            try {
                // Provide a file to write input bytes into
                outputFile = fileProvider.provide(fileData)

                listener.onStartDownload(outputFile)

                // Create a connection with server to read bytes from
                connection = provideConnection(
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
                            file = outputFile,
                            downloadedBytes = downloadedBytes,
                            downloadRate = bytesPerMs * 1000,
                            remainingTime = remainingMs
                        )

                        lastSetProgressTs = curTs
                    }
                }

                listener.onDownloadCompleted(outputFile)
            } catch (e: Exception) {
                listener.onDownloadFailed(outputFile, e)
            } finally {
                stopDownload()
            }
        }
    }

    /**
     * Stops the ongoing download operation.
     * <p>
     * Closes any open streams and disconnects the HTTP connection.
     * Should be called to release resources during cancellation or completion.
     * </p>
     */
    override suspend fun stopDownload() {
        outputStream?.close()
        inputStream?.close()
        connection.disconnect()
    }

    /**
     * Creates and configures a {@link HttpURLConnection} for the given file URL.
     * <p>
     * Adds an "Authorization" header if an access token is provided and includes
     * a "Range" header for resuming downloads based on the output file's current size.
     * </p>
     *
     * @param fileUrl     The URL of the file to download.
     * @param accessToken Optional access token for authenticated requests.
     * @param outputFile  The file to write the download data to.
     * @return A configured {@link HttpURLConnection} instance.
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

        connection.connectTimeout = 15_000
        connection.readTimeout = 0

        return connection
    }

    /**
     * Parses the "Content-Range" response header to determine the starting byte offset.
     * <p>
     * Used for resuming downloads from the correct position.
     * </p>
     *
     * @param connection The active {@link HttpURLConnection}.
     * @return The byte offset from which to resume the download.
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