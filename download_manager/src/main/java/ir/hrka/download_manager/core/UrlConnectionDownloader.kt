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
 * A concrete implementation of the [Downloader] interface that downloads files using [HttpURLConnection].
 *
 * This class supports resuming interrupted downloads by using HTTP Range requests. It also
 * provides real-time progress updates including download rate and estimated time remaining.
 *
 * @property fileProvider Provides the output [File] where the downloaded content should be saved.
 */
internal class UrlConnectionDownloader(
    private val fileProvider: FileProvider
) : Downloader {

    private val TAG = "DM_UrlConnectionDownloader"
    private lateinit var connection: HttpURLConnection
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    /**
     * Starts downloading a file using [HttpURLConnection]. Supports progress reporting and
     * resuming downloads if a partial file already exists.
     *
     * @param fileData Metadata for the file to download including URL, token, and total size.
     * @param listener Callback listener for download status updates such as start, progress, success, and failure.
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

    override suspend fun stopDownload() {
        outputStream?.close()
        inputStream?.close()
        connection.disconnect()
    }

    /**
     * Creates and configures a [HttpURLConnection] for the provided file URL.
     *
     * If an access token is provided, it is set as a Bearer token in the Authorization header.
     * If a partial file already exists, a `Range` header is added to resume the download.
     *
     * @param fileUrl The URL of the file to be downloaded.
     * @param accessToken Optional bearer token used for authorization.
     * @param outputFile The file where data is being downloaded.
     * @return A configured [HttpURLConnection] instance ready to connect.
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
     * Parses the `Content-Range` header of the response to extract the starting byte position.
     *
     * This is useful for resuming partially downloaded files.
     *
     * @param connection The [HttpURLConnection] with a response containing the Content-Range header.
     * @return The starting byte position from the server response.
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