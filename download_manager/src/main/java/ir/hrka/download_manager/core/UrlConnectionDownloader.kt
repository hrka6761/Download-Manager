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

internal class UrlConnectionDownloader(private val fileProvider: FileProvider) : Downloader {

    override suspend fun download(
        fileData: FileDataModel,
        listener: DownloadListener
    ) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        return withContext(Dispatchers.IO) {
            try {
                // Provide a file to write input bytes into
                val outputFile = fileProvider.provide(fileData)

                listener.onStartDownload(outputFile)

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
                listener.onDownloadFailed(e)
            } finally {
                outputStream?.close()
                inputStream?.close()
            }
        }
    }


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