package ir.hrka.download_manager.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import ir.hrka.download_manager.core.UrlConnectionDownloader
import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.file.FileProvider
import ir.hrka.download_manager.file.InternalFileProvider
import ir.hrka.download_manager.utilities.Constants.FOREGROUND_NOTIFICATION_CHANNEL_ID
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_ACCESS_TOKEN
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DIRECTORY_NAME
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_IS_ZIP
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_NAME
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_SUFFIX
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_TOTAL_BYTES
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_UNZIPPED_DIRECTORY
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_URL
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_VERSION_NAME
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DOWNLOAD_RATE
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DOWNLOAD_RECEIVED_BYTES
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DOWNLOAD_REMAINING_MS
import ir.hrka.download_manager.listeners.DownloadListener
import ir.hrka.download_manager.utilities.Constants.KEY_DOWNLOADED_FILE_PATH
import ir.hrka.download_manager.utilities.Constants.KEY_DOWNLOAD_FAILED_MESSAGE
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_CREATION_MODE
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_MIME_TYPE
import ir.hrka.download_manager.utilities.Constants.KEY_RUN_IN_SERVICE
import ir.hrka.download_manager.utilities.FileCreationMode
import java.io.File

/**
 * A [CoroutineWorker] that downloads a file in the background using [UrlConnectionDownloader]
 * and saves it internally using [InternalFileProvider].
 *
 * This worker supports running as a foreground service with a persistent notification
 * displaying download progress.
 *
 * The worker reads required parameters from the [inputData] bundle, including:
 * - URL, file name, suffix, directory name, version, mime type, zip flag, unzipped directory,
 *   total file size, and access token for the file.
 * - Flags to indicate if the worker should run in a foreground service and the file creation mode.
 *
 * @property context The application context.
 * @property params Worker parameters containing input data and runtime info.
 */
internal class InternalDownloadWorker(
    private val context: Context,
    private val params: WorkerParameters
) : CoroutineWorker(context, params) {

    private var channelCreated = false
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId: Int = params.id.hashCode() // Unique notification id
    private val runInService = inputData.getBoolean(KEY_RUN_IN_SERVICE, false)
    private val writeType = inputData.getInt(KEY_FILE_CREATION_MODE, 0)
    private val internalFileProvider: FileProvider =
        InternalFileProvider(context, FileCreationMode.entries[writeType])
    private val urlConnectionDownloader = UrlConnectionDownloader(internalFileProvider)
    private val fileData = FileDataModel(
        fileUrl = inputData.getString(KEY_FILE_URL) ?: "",
        fileName = inputData.getString(KEY_FILE_NAME) ?: "",
        fileSuffix = inputData.getString(KEY_FILE_SUFFIX) ?: "",
        fileDirName = inputData.getString(KEY_FILE_DIRECTORY_NAME) ?: "",
        fileVersion = inputData.getString(KEY_FILE_VERSION_NAME),
        fileMimeType = inputData.getString(KEY_FILE_MIME_TYPE),
        isZip = inputData.getBoolean(KEY_FILE_IS_ZIP, false),
        unzippedDirName = inputData.getString(KEY_FILE_UNZIPPED_DIRECTORY),
        totalBytes = inputData.getLong(KEY_FILE_TOTAL_BYTES, 0L),
        accessToken = inputData.getString(KEY_FILE_ACCESS_TOKEN)
    )

    /**
     * Initializes the notification channel required for foreground service notifications.
     *
     * This block creates a notification channel with the ID [FOREGROUND_NOTIFICATION_CHANNEL_ID]
     * named "Download manager" with low importance to be used for download notifications.
     * The channel creation happens only once when the worker is set to run in a foreground service
     * mode ([runInService] is true) and the channel has not yet been created ([channelCreated] is false).
     *
     * Notification channels are mandatory on Android 8.0 (API level 26) and above for notifications
     * to appear properly.
     */
    init {
        if (runInService && !channelCreated) {
            val channel = NotificationChannel(
                FOREGROUND_NOTIFICATION_CHANNEL_ID,
                "Download manager",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Download manager notifications" }
            notificationManager.createNotificationChannel(channel)
            channelCreated = true
        }
    }


    /**
     * Performs the download work asynchronously.
     *
     * Uses [UrlConnectionDownloader] to download the file specified by [fileData].
     * Reports progress via a [DownloadListener] and updates the foreground notification
     * if running as a foreground service.
     *
     * @return [Result.success] with the downloaded file path on success,
     * or [Result.failure] with an error message on failure.
     */
    override suspend fun doWork(): Result {
        var downloadedFile: File? = null
        var failedMsg: String? = null
        var isWorkSuccess = false

        urlConnectionDownloader.download(
            fileData = fileData,
            listener = object : DownloadListener {
                override suspend fun onStartDownload(file: File) {
                    if (runInService)
                        setForeground(
                            createRunningForegroundInfo(
                                progress = 0,
                                filename = file.name
                            )
                        )
                }

                override suspend fun onProgressUpdate(
                    file: File,
                    downloadedBytes: Long,
                    downloadRate: Float,
                    remainingTime: Float
                ) {
                    val data = Data.Builder()
                        .putLong(KEY_FILE_DOWNLOAD_RECEIVED_BYTES, downloadedBytes)
                        .putLong(KEY_FILE_DOWNLOAD_RATE, (downloadRate * 1000).toLong())
                        .putLong(KEY_FILE_DOWNLOAD_REMAINING_MS, remainingTime.toLong())
                        .build()

                    setProgress(data)

                    if (runInService)
                        setForeground(
                            createRunningForegroundInfo(
                                progress = (downloadedBytes * 100 / fileData.totalBytes).toInt(),
                                filename = file.name
                            )
                        )
                }

                override suspend fun onDownloadCompleted(file: File) {
                    downloadedFile = file
                    isWorkSuccess = true
                }

                override suspend fun onDownloadFailed(e: Exception) {
                    failedMsg = e.message
                }
            }
        )

        return if (isWorkSuccess) {
            val successData = Data.Builder()
                .putString(KEY_DOWNLOADED_FILE_PATH, downloadedFile?.name)
                .build()

            Result.success(successData)
        } else {
            val failedData = Data.Builder()
                .putString(KEY_DOWNLOAD_FAILED_MESSAGE, failedMsg)
                .build()

            Result.failure(failedData)
        }
    }

    /**
     * Creates a [ForegroundInfo] with a progress notification for the download.
     *
     * @param progress Download progress as a percentage (0-100).
     * @param filename Name of the file being downloaded.
     * @return A [ForegroundInfo] object to update the foreground notification.
     */
    private fun createRunningForegroundInfo(progress: Int, filename: String): ForegroundInfo {
        var title = "Downloading $filename"
        val content = "Downloading in progress: $progress%"

        val notification =
            NotificationCompat.Builder(applicationContext, FOREGROUND_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setProgress(100, progress, false)
                .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}