package ir.hrka.download_manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.listeners.DownloadWorkerListener
import ir.hrka.download_manager.utilities.Constants.DOWNLOAD_MANAGER_TAG
import ir.hrka.download_manager.utilities.Constants.KEY_DOWNLOADED_FILE_PATH
import ir.hrka.download_manager.utilities.Constants.KEY_DOWNLOAD_FAILED_MESSAGE
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_ACCESS_TOKEN
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DIRECTORY_NAME
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DOWNLOAD_RATE
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DOWNLOAD_RECEIVED_BYTES
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_DOWNLOAD_REMAINING_MS
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_IS_ZIP
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_NAME
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_SUFFIX
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_TOTAL_BYTES
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_UNZIPPED_DIRECTORY
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_URL
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_VERSION_NAME
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_CREATION_MODE
import ir.hrka.download_manager.utilities.Constants.KEY_FILE_MIME_TYPE
import ir.hrka.download_manager.utilities.Constants.KEY_RUN_IN_SERVICE
import ir.hrka.download_manager.utilities.FileLocation
import ir.hrka.download_manager.utilities.FileCreationMode

class DownloadManager private constructor(
    private val context: Context,
    private val fileLocation: FileLocation = FileLocation.InternalStorage,
    private val fileCreationMode: FileCreationMode = FileCreationMode.Overwrite,
    private val listener: DownloadWorkerListener? = null,
    private val runInService: Boolean = false
) {

    private val workManager = WorkManager.getInstance(context)


    @Throws(SecurityException::class)
    fun startDownload(
        fileData: FileDataModel
    ) {
        if (
            runInService &&
            !hasForegroundServicePermission() &&
            !hasNotificationPermission()
        )
            throw SecurityException(
                "Missing FOREGROUND_SERVICE or FOREGROUND_SERVICE_DATA_SYNC and POST_NOTIFICATIONS permissions."
            )

        val inputData = Data
            .Builder()
            .putBoolean(KEY_RUN_IN_SERVICE, runInService)
            .putInt(KEY_FILE_CREATION_MODE, fileCreationMode.ordinal)
            .putString(KEY_FILE_URL, fileData.fileUrl)
            .putString(KEY_FILE_NAME, fileData.fileName)
            .putString(KEY_FILE_SUFFIX, fileData.fileSuffix)
            .putString(KEY_FILE_DIRECTORY_NAME, fileData.fileDirName)
            .putString(KEY_FILE_VERSION_NAME, fileData.fileVersion)
            .putString(KEY_FILE_MIME_TYPE, fileData.fileMimeType)
            .putBoolean(KEY_FILE_IS_ZIP, fileData.isZip)
            .putString(KEY_FILE_UNZIPPED_DIRECTORY, fileData.unzippedDirName)
            .putLong(KEY_FILE_TOTAL_BYTES, fileData.totalBytes)
            .putString(KEY_FILE_ACCESS_TOKEN, fileData.accessToken)
            .build()

        val workRequest =
            fileLocation.workRequest
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(inputData)
                .addTag(DOWNLOAD_MANAGER_TAG)
                .build()

        listener?.let { listener ->
            workManager
                .getWorkInfoByIdLiveData(workRequest.id)
                .observeForever { workInfo ->
                    workInfo?.let {
                        when (workInfo.state) {
                            WorkInfo.State.ENQUEUED -> {
                                listener.onDownloadEnqueued()
                            }

                            WorkInfo.State.RUNNING -> {
                                val receivedBytes =
                                    workInfo.progress.getLong(KEY_FILE_DOWNLOAD_RECEIVED_BYTES, 0L)
                                val downloadRate =
                                    workInfo.progress.getLong(KEY_FILE_DOWNLOAD_RATE, 0L)
                                val remainingTime =
                                    workInfo.progress.getLong(KEY_FILE_DOWNLOAD_REMAINING_MS, 0L)

                                listener.onDownloadRunning(
                                    receivedBytes = receivedBytes,
                                    downloadRate = downloadRate,
                                    remainingTime = remainingTime
                                )
                            }

                            WorkInfo.State.SUCCEEDED -> {
                                val downloadedFilePath =
                                    workInfo.outputData.getString(KEY_DOWNLOADED_FILE_PATH)
                                listener.onDownloadSuccess(downloadedFilePath)
                            }

                            WorkInfo.State.BLOCKED -> {
                                listener.onDownloadBlocked()
                            }

                            WorkInfo.State.FAILED -> {
                                val downloadFailedMsg =
                                    workInfo.outputData.getString(KEY_DOWNLOAD_FAILED_MESSAGE)

                                listener.onDownloadFailed(downloadFailedMsg)
                            }

                            WorkInfo.State.CANCELLED -> {
                                listener.onDownloadCancelled()
                            }
                        }
                    }
                }
        }

        workManager.enqueueUniqueWork(fileData.fileName, ExistingWorkPolicy.REPLACE, workRequest)
    }


    private fun hasForegroundServicePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.FOREGROUND_SERVICE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }


    class Builder(private val context: Context) {

        private lateinit var mFileLocation: FileLocation
        private lateinit var mFileCreationMode: FileCreationMode
        private var mListener: DownloadWorkerListener? = null
        private var mRunInService: Boolean = false


        fun build(): DownloadManager =
            DownloadManager(
                context = context,
                fileLocation = mFileLocation,
                fileCreationMode = mFileCreationMode,
                listener = mListener,
                runInService = mRunInService
            )

        fun setFileLocation(fileLocation: FileLocation) =
            apply { mFileLocation = fileLocation }

        fun setFileCreationMode(fileCreationMode: FileCreationMode) =
            apply { mFileCreationMode = fileCreationMode }

        fun setDownloadListener(listener: DownloadWorkerListener) =
            apply { mListener = listener }

        fun runInService() =
            apply { mRunInService = true }
    }
}