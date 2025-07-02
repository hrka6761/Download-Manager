package ir.hrka.downloadmanager

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
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

    private val TAG = "hamidreza"
    private val _downloadStatus: MutableStateFlow<DownloadStatus> =
        MutableStateFlow(DownloadStatus.None)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus
    private val _downloadProgress: MutableStateFlow<Float> =
        MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress
    private var fileDataModel: FileDataModel? = null


    fun setDownloadStatus(status: DownloadStatus) {
        _downloadStatus.value = status
    }

    fun startDownload(
        activity: Activity,
        fileDataModel: FileDataModel,
        fileLocation: FileLocation,
        creationMode: FileCreationMode
    ) {
        this.fileDataModel = fileDataModel

        val downloadManager = DownloadManager.Builder(activity)
            .setFileLocation(fileLocation)
            .setFileCreationMode(creationMode)
            .runInService()
            .setDownloadListener(
                object : DownloadWorkerListener {
                    override fun onDownloadEnqueued() {
                        setDownloadStatus(DownloadStatus.Downloading)
                    }

                    override fun onDownloadRunning(
                        receivedBytes: Long,
                        downloadRate: Long,
                        remainingTime: Long
                    ) {
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

                    }

                    override fun onDownloadBlocked() {

                    }
                }
            )
            .build()


        downloadManager.startDownload(fileDataModel)
    }

    fun retryDownload() {}

    fun cancelDownload() {
        
    }

    fun checkDownload(context: Context) {
        val file = File(
            context.filesDir,
            "${fileDataModel?.fileDirName}${File.separator}${fileDataModel?.fileVersion}${File.separator}${fileDataModel?.fileName}.${fileDataModel?.fileSuffix}"
        )

        Toast.makeText(context, file.absolutePath, Toast.LENGTH_LONG).show()
    }
}