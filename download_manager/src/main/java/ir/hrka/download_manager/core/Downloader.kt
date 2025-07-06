package ir.hrka.download_manager.core

import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.listeners.DownloadListener

/**
 * Interface representing a file downloader.
 *
 * Implementations of this interface are responsible for downloading files asynchronously.
 *
 * @see FileDataModel for details on the file information needed to initiate a download.
 * @see DownloadListener for handling download progress, success, and failure callbacks.
 */
internal interface Downloader {

    /**
     * Starts downloading the specified file.
     *
     * @param fileData The metadata of the file to be downloaded.
     * @param listener The listener to receive callbacks for progress, success, and failure.
     */
    suspend fun startDownload(fileData: FileDataModel, listener: DownloadListener)

    suspend fun stopDownload()
}