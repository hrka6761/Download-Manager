package ir.hrka.download_manager.file

import android.content.Context
import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.utilities.FileCreationMode
import java.io.File

/**
 * [FileProvider] implementation that provides files stored in the app's internal storage.
 *
 * This class creates files inside a specified directory under the app's external files directory,
 * optionally considering file versioning and creation mode (overwrite or create new).
 *
 * @property context The Android [Context] used to access internal storage directories.
 * @property creationMode Determines the behavior when a file with the same name already exists.
 */
internal class InternalFileProvider(
    private val context: Context,
    private val creationMode: FileCreationMode
) : FileProvider {

    /**
     * Provides a [File] instance in the app's internal storage for the given [FileDataModel].
     *
     * The file will be created or handled based on the specified [creationMode].
     *
     * @param fileData Metadata describing the file to be stored.
     * @return The [File] object pointing to the appropriate location.
     */
    override fun provide(fileData: FileDataModel): File = provideOutputFile(fileData)

    /**
     * Creates or determines the output file based on the file data and creation mode.
     *
     * If a file with the same name exists:
     * - In [FileCreationMode.Overwrite], the existing file will be deleted.
     * - In [FileCreationMode.CreateNew], a new file will be created with a timestamp appended to its name.
     *
     * @param fileData The metadata describing the file.
     * @return The [File] representing the output file.
     */
    private fun provideOutputFile(fileData: FileDataModel): File {
        var outputFile =
            createFileInInternalStorage(
                context = context,
                directoryName = fileData.fileDirName,
                version = fileData.fileVersion,
                fileFullName = fileData.getFullName()
            )

        if (outputFile.exists())
            when (creationMode) {
                FileCreationMode.Overwrite -> outputFile.delete()
                FileCreationMode.CreateNew -> {
                    val newOutputFile =
                        File(
                            outputFile.absolutePath.replace(
                                fileData.getFullName(),
                                fileData.getFullName("${System.currentTimeMillis()}")
                            )
                        )
                    outputFile = newOutputFile
                }
            }

        return outputFile
    }

    /**
     * Creates a [File] instance inside internal storage under a directory structure based on
     * [directoryName] and optional [version].
     *
     * Directories are created if they do not already exist.
     *
     * @param context The Android context for accessing storage.
     * @param directoryName The base directory name.
     * @param version Optional subdirectory for file versioning.
     * @param fileFullName The full filename (including suffix).
     * @return A [File] pointing to the intended storage location.
     */
    private fun createFileInInternalStorage(
        context: Context,
        directoryName: String,
        version: String?,
        fileFullName: String
    ): File {
        val directories = mutableListOf(directoryName)
        version?.let { directories.add(it) }

        val outputDir = File(
            context.getExternalFilesDir(null),
            directories.joinToString(separator = File.separator)
        )

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        return File(listOf(outputDir, fileFullName).joinToString(separator = File.separator))
    }
}