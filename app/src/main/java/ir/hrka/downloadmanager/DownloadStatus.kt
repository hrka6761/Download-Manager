package ir.hrka.downloadmanager

sealed class DownloadStatus {

    object None: DownloadStatus()
    object Downloading: DownloadStatus()
    object DownloadSuccess: DownloadStatus()
    object DownloadFailed: DownloadStatus()
}