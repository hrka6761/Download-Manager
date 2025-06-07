package ir.hrka.download_manager.utilities

import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import ir.hrka.download_manager.workers.InternalDownloadWorker

enum class FileLocation(val workRequest: OneTimeWorkRequest.Builder) {

    InternalStorage(OneTimeWorkRequestBuilder<InternalDownloadWorker>())
}