package ir.hrka.download_manager.file

import ir.hrka.download_manager.entities.FileDataModel
import java.io.File

internal interface FileProvider {

    fun provide(fileData: FileDataModel): File
}