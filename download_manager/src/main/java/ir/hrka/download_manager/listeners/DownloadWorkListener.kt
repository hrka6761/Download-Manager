package ir.hrka.download_manager.listeners

interface DownloadWorkListener {

    fun onDownloadEnqueued()
    fun onDownloadRunning(receivedBytes: Long, downloadRate: Long, remainingTime: Long)
    fun onDownloadSuccess(filePath: String?)
    fun onDownloadFailed(errorMsg: String?)
    fun onDownloadCancelled()
    fun onDownloadBlocked()
}