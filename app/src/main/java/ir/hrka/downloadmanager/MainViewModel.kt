package ir.hrka.downloadmanager

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import ir.hrka.download_manager.DownloadManager
import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.listeners.DownloadWorkerListener
import ir.hrka.download_manager.utilities.FileCreationMode
import ir.hrka.download_manager.utilities.FileLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class MainViewModel : ViewModel() {

    private val _downloadStatus: MutableStateFlow<DownloadStatus> =
        MutableStateFlow(DownloadStatus.None)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus
    private val _downloadProgress: MutableStateFlow<Float> =
        MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress
    private var fileDataModel: FileDataModel? = null
    private lateinit var downloadManagerBuilder: DownloadManager.Builder
    private lateinit var downloadManager: DownloadManager


    fun setDownloadStatus(status: DownloadStatus) {
        _downloadStatus.value = status
    }

    fun startDownload(
        activity: Activity,
        fileDataModel: FileDataModel,
        fileLocation: FileLocation,
        creationMode: FileCreationMode,
        runInService: Boolean
    ) {
        this.fileDataModel = fileDataModel

        downloadManagerBuilder = DownloadManager.Builder(activity)
            .setFileLocation(fileLocation)
            .setFileCreationMode(creationMode)

        if (runInService)
            downloadManagerBuilder.runInService()

        downloadManagerBuilder.setDownloadListener(
            object : DownloadWorkerListener {
                override fun onDownloadEnqueued() {
                    setDownloadStatus(DownloadStatus.StartDownload)
                }

                override fun onDownloadRunning(
                    receivedBytes: Long,
                    downloadRate: Long,
                    remainingTime: Long
                ) {
                    if (_downloadStatus.value != DownloadStatus.Downloading)
                        setDownloadStatus(DownloadStatus.Downloading)
                    _downloadProgress.value =
                        receivedBytes.toFloat() / fileDataModel.totalBytes
                }

                override fun onDownloadSuccess(filePath: String?) {
                    setDownloadStatus(DownloadStatus.DownloadSuccess)
                }

                override fun onDownloadFailed(errorMsg: String?) {
                    setDownloadStatus(DownloadStatus.DownloadFailed)
                }

                override fun onDownloadCancelled() {
                    setDownloadStatus(DownloadStatus.None)
                }

                override fun onDownloadBlocked() {
                    setDownloadStatus(DownloadStatus.None)
                }
            }
        ).build()

        downloadManager = downloadManagerBuilder.build()
        downloadManager.startDownload(fileDataModel)
    }

    fun retryDownload() {}

    fun cancelDownload() {
        downloadManager.stopDownload()
    }

    fun checkDownload(context: Context) {
        val file = File(
            context.filesDir,
            "${fileDataModel?.fileDirName}${File.separator}${fileDataModel?.fileVersion}${File.separator}${fileDataModel?.fileName}.${fileDataModel?.fileExtension}"
        )

        Toast.makeText(context, file.absolutePath, Toast.LENGTH_LONG).show()
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        else
            true
    }
}