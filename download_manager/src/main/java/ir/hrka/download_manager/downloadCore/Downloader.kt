package ir.hrka.download_manager.downloadCore

import android.content.Context
import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.utilities.DownloadListener

/**
 * A contract for downloading files in a customizable way.
 *
 * Implementations of this interface define how files should be downloaded,
 * allowing flexibility in storage location, network handling, and progress updates.
 *
 * This interface is designed to be used with coroutine-based asynchronous download logic.
 */
interface Downloader {

    /**
     * Downloads a file asynchronously.
     *
     * This method performs the download operation, reporting progress and completion
     * through the provided [DownloadListener]. The destination and file metadata
     * are specified in [fileDataModel], and the [context] may be used to determine
     * where the file is saved (e.g., internal or shared storage).
     *
     * @param context The context used for file system access or permissions.
     * @param fileDataModel The model containing the file URL, name, version, and storage details.
     * @param listener A listener to receive download start, progress, completion, or failure callbacks.
     */
    suspend fun download(
        context: Context,
        fileDataModel: FileDataModel,
        listener: DownloadListener
    )
}