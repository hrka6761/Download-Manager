package ir.hrka.download_manager.utilities

import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import ir.hrka.download_manager.workers.InternalDownloadWorker

/**
 * Enum representing the file storage location, each associated with a specific
 * [OneTimeWorkRequest.Builder] for performing background download work.
 *
 * @property workRequest The builder for the background work request related to this location.
 */
enum class FileLocation(val workRequest: OneTimeWorkRequest.Builder) {

    /**
     * Represents internal storage location with its corresponding
     * [InternalDownloadWorker] background task builder.
     */
    InternalStorage(OneTimeWorkRequestBuilder<InternalDownloadWorker>())
}
