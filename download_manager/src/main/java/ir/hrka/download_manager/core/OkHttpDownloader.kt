package ir.hrka.download_manager.core

import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.exceptions.NoResponseException
import ir.hrka.download_manager.file.FileProvider
import ir.hrka.download_manager.listeners.DownloadListener
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Implementation of the {@link Downloader} interface using OkHttp for handling file downloads.
 * <p>
 * This class supports resuming downloads by using HTTP range requests and provides download
 * progress updates, including estimated download speed and remaining time.
 * </p>
 *
 * <p>
 * The {@link FileProvider} passed to this downloader is used to determine the local file to write
 * the downloaded data to. The class uses an internal buffer and throttles progress updates to
 * reduce overhead by reporting every 200 milliseconds.
 * </p>
 *
 * <h3>Features:</h3>
 * <ul>
 *     <li>Supports resuming interrupted downloads.</li>
 *     <li>Provides periodic progress updates, including estimated download rate and time left.</li>
 *     <li>Gracefully handles HTTP and I/O failures.</li>
 * </ul>
 *
 * @constructor Creates an instance of the downloader with a specified file provider.
 * @param fileProvider The provider that returns the output {@link File} based on {@link FileDataModel}.
 *
 * @see Downloader
 * @see FileDataModel
 * @see DownloadListener
 */
internal class OkHttpDownloader(
    private val fileProvider: FileProvider
) : Downloader {

    private val TAG = "DM_OkHttpDownloader"
    private var httpInputStream: InputStream? = null
    private var fileOutputStream: FileOutputStream? = null


    /**
     * Starts downloading the file described by the {@link FileDataModel}.
     * <p>
     * The method supports resuming partially downloaded files if supported by the server
     * and reports periodic progress updates to the given {@link DownloadListener}.
     * </p>
     *
     * @param fileData  Metadata about the file to be downloaded, including URL and total size.
     * @param listener  Callback interface to report progress, completion, or failure.
     */
    override suspend fun startDownload(
        fileData: FileDataModel,
        listener: DownloadListener
    ) {
        var outputFile: File? = null
        var client: OkHttpClient? = null
        var request: Request? = null
        var downloadedBytes = 0L
        var deltaBytes = 0L
        var lastSetProgressTs: Long = 0
        val bytesReadSizeBuffer: MutableList<Long> = mutableListOf()
        val bytesReadLatencyBuffer: MutableList<Long> = mutableListOf()

        // Provide a file to write input bytes into
        outputFile = fileProvider.provide(fileData)
        downloadedBytes = outputFile.takeIf { it.exists() }?.length() ?: 0L
        client = provideOkHttpClient()
        request = provideRequest(downloadedBytes, fileData.fileUrl)

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // Open file stream (append if resuming)
                    FileOutputStream(outputFile, downloadedBytes > 0).use { output ->
                        fileOutputStream = output
                        response.body?.byteStream()?.use { input ->
                            httpInputStream = input
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)

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
                        }
                    }
                } else {
                    listener.onDownloadFailed(outputFile, NoResponseException())
                }
            }
        } catch (e: IOException) {
            listener.onDownloadFailed(outputFile, e)
        } finally {
            stopDownload()
        }
    }

    /**
     * Stops the current download operation and closes all open streams.
     * <p>
     * Should be called to clean up resources after download completes or fails.
     * </p>
     */
    override suspend fun stopDownload() {
        fileOutputStream?.close()
        fileOutputStream?.flush()
        httpInputStream?.close()
    }

    /**
     * Creates a configured instance of {@link OkHttpClient} with timeout and retry settings.
     *
     * @return A ready-to-use OkHttpClient instance.
     */
    private fun provideOkHttpClient() =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    /**
     * Builds an OkHttp {@link Request} for the given URL and file state.
     * <p>
     * If the file has already been partially downloaded, it includes a "Range" header
     * to resume the download.
     * </p>
     *
     * @param downloadedBytes Number of bytes already downloaded (for resume support).
     * @param url             The full URL of the file to be downloaded.
     * @return A configured HTTP request.
     */
    private fun provideRequest(downloadedBytes: Long, url: String): Request {
        val requestBuilder = Request.Builder().url(url)

        if (downloadedBytes > 0) {
            // Resume from where we left off
            requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
        }

        return requestBuilder.build()
    }
}