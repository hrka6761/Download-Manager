package ir.hrka.download_manager.downloadCore

import android.content.Context
import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.listeners.DownloadListener

internal interface Downloader {

    suspend fun download(
        context: Context,
        fileDataModel: FileDataModel,
        listener: DownloadListener
    )
}