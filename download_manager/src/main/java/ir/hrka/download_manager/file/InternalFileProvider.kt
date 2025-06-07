package ir.hrka.download_manager.file

import android.content.Context
import ir.hrka.download_manager.entities.FileDataModel
import ir.hrka.download_manager.utilities.FileCreationMode
import java.io.File

internal class InternalFileProvider(
    private val context: Context,
    private val creationMode: FileCreationMode
) : FileProvider {

    override fun provide(fileData: FileDataModel): File = provideOutputFile(fileData)


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