package ir.hrka.download_manager.core

import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.listeners.DownloadListener

internal interface Downloader {

    suspend fun download(fileData: FileDataModel, listener: DownloadListener)
}