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
import ir.hrka.download_manager.core.OkHttpDownloader
import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.file.FileProvider
import ir.hrka.download_manager.file.PrivateInternalFileProvider
import ir.hrka.download_manager.utilities.Constants.FOREGROUND_NOTIFICATION_CHANNEL_ID
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_ACCESS_TOKEN
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DIRECTORY_NAME
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_NAME
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_SUFFIX
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_TOTAL_BYTES
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_URL
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_VERSION_NAME
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DOWNLOAD_RATE
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DOWNLOAD_RECEIVED_BYTES
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DOWNLOAD_REMAINING_MS
import ir.hrka.download_manager.listeners.DownloadListener
import ir.hrka.download_manager.utilities.Constants.KEY_DOWNLOADED_FILE_PATH
import ir.hrka.download_manager.utilities.Constants.KEY_DOWNLOAD_FAILED_MESSAGE
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_CREATION_MODE
import ir.hrka.download_manager.utilities.Constants.KEY_RUN_IN_SERVICE
import ir.hrka.download_manager.utilities.FileCreationMode
import java.io.File

/**
 * A [CoroutineWorker] responsible for downloading a file in the background using a coroutine.
 *
 * This worker manages the download process via [OkHttpDownloader], handles progress reporting,
 * and updates a foreground notification if configured to run in a service.
 *
 * @property context The application context.
 * @property params The parameters used to configure this worker, including input data.
 *
 * The worker supports these input parameters via [WorkerParameters.inputData]:
 * - [KEY_FILE_URL]: The URL of the file to download.
 * - [KEY_FILE_NAME]: The name of the file to save.
 * - [KEY_FILE_SUFFIX]: The file extension.
 * - [KEY_FILE_DIRECTORY_NAME]: Optional directory name to store the file.
 * - [KEY_FILE_VERSION_NAME]: Optional version string for the file.
 * - [KEY_FILE_TOTAL_BYTES]: Total size of the file in bytes.
 * - [KEY_FILE_ACCESS_TOKEN]: Optional access token for authorization.
 * - [KEY_FILE_CREATION_MODE]: The mode of file creation (overwrite, append, create new).
 * - [KEY_RUN_IN_SERVICE]: Whether to run the download with a foreground notification service.
 *
 * The worker posts download progress updates via [setProgress], and uses foreground notifications
 * to display download status if [KEY_RUN_IN_SERVICE] is true.
 *
 * The result of the worker is:
 * - [Result.success] with the downloaded file path on success.
 * - [Result.failure] with an error message on failure.
 */
internal class InternalDownloadWorker(
    private val context: Context,
    private val params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "DM_InternalDownloadWorker"
    private var channelCreated = false
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationId: Int = params.id.hashCode() // Unique notification id
    private val runInService = inputData.getBoolean(KEY_RUN_IN_SERVICE, false)
    private val writeType = inputData.getInt(KEY_FILE_CREATION_MODE, 0)
    private val privateInternalFileProvider: FileProvider =
        PrivateInternalFileProvider(context, FileCreationMode.entries[writeType])
    private val downloader = OkHttpDownloader(privateInternalFileProvider)
    private val fileData = FileDataModel(
        fileUrl = inputData.getString(KEY_FILE_URL) ?: "",
        fileName = inputData.getString(KEY_FILE_NAME) ?: "",
        fileExtension = inputData.getString(KEY_FILE_SUFFIX) ?: "",
        fileDirName = inputData.getString(KEY_FILE_DIRECTORY_NAME) ?: "",
        fileVersion = inputData.getString(KEY_FILE_VERSION_NAME),
        totalBytes = inputData.getLong(KEY_FILE_TOTAL_BYTES, 0L),
        accessToken = inputData.getString(KEY_FILE_ACCESS_TOKEN)
    )


    /**
     * Initializes the notification channel if the worker is configured to run as a foreground service.
     *
     * This setup is required to display notifications on Android 8.0 (API level 26) and above.
     * The channel is created once and used for download progress and status notifications.
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
     * Executes the download task in a coroutine worker context.
     *
     * This method starts the file download using the [downloader] with the provided [fileData].
     * It listens for download events to update progress, handle completion, and failures.
     * If configured to run in a foreground service, it updates the notification accordingly.
     *
     * @return [Result.success] with output data containing the downloaded file path if the download succeeds,
     * or [Result.failure] with an error message if it fails.
     */
    override suspend fun doWork(): Result {
        var downloadedFile: File? = null
        var failedMsg: String? = null
        var isWorkSuccess = false

        downloader.startDownload(
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
                    if (runInService)
                        setForeground(createSuccessForegroundInfo(filename = file.name))

                    downloadedFile = file
                    isWorkSuccess = true
                }

                override suspend fun onDownloadFailed(file: File?, e: Exception) {
                    if (runInService)
                        setForeground(createFailedForegroundInfo(filename = file?.name ?: ""))

                    failedMsg = e.message
                }
            }
        )

        return if (isWorkSuccess) {
            val successData = Data.Builder()
                .putString(KEY_DOWNLOADED_FILE_PATH, downloadedFile?.absolutePath)
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
     * Creates a [ForegroundInfo] representing the ongoing download notification.
     *
     * Displays the download progress with the current filename and percentage completed.
     *
     * @param progress The current progress percentage (0-100).
     * @param filename The name of the file being downloaded.
     * @return A [ForegroundInfo] with the notification configured for an active download.
     */
    private fun createRunningForegroundInfo(progress: Int, filename: String): ForegroundInfo {
        var title = "Downloading $filename"
        val content = "Downloading in progress: $progress%\n" +
                ""

        val notification =
            NotificationCompat.Builder(applicationContext, FOREGROUND_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .setProgress(100, progress, false)
                .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    /**
     * Creates a [ForegroundInfo] representing the successful completion notification.
     *
     * Indicates that the download of the specified file has completed successfully.
     *
     * @param filename The name of the file downloaded.
     * @return A [ForegroundInfo] with the notification configured for a successful download.
     */
    private fun createSuccessForegroundInfo(filename: String): ForegroundInfo {
        var title = "Successful Download"
        val content = "Download $filename successfully done."

        val notification =
            NotificationCompat.Builder(applicationContext, FOREGROUND_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    /**
     * Creates a [ForegroundInfo] representing the failed download notification.
     *
     * Indicates that the download of the specified file has failed.
     *
     * @param filename The name of the file whose download failed.
     * @return A [ForegroundInfo] with the notification configured for a failed download.
     */
    private fun createFailedForegroundInfo(filename: String): ForegroundInfo {
        var title = "Failed Download"
        val content = "Download of $filename failed."

        val notification =
            NotificationCompat.Builder(applicationContext, FOREGROUND_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}