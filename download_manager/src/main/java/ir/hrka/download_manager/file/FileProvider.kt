package ir.hrka.download_manager.file

import ir.hrka.download_manager.entities.FileDataModel
import java.io.File

/**
 * Interface responsible for providing a [File] instance based on the given [FileDataModel].
 *
 * Implementations of this interface should determine the correct file path and handle
 * any file creation or directory setup as necessary.
 */
internal interface FileProvider {

    /**
     * Provides a [File] object where the specified file data should be downloaded or stored.
     *
     * @param fileData The metadata describing the file to be stored.
     * @return A [File] instance representing the local destination for the download.
     */
    fun provide(fileData: FileDataModel): File
}
