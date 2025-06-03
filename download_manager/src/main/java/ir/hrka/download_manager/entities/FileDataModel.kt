package ir.hrka.download_manager.entities

data class FileDataModel(
    val fileUrl: String,
    val fileName: String,
    val fileSuffix: String,
    val fileDirName: String,
    val fileVersion: String?,
    val isZip: Boolean,
    val unzippedDirName: String?,
    val totalBytes: Long,
    val accessToken: String?,
) {

    fun getFullName(append: String = ""): String =
        if (append.isNotEmpty()) "${fileName}_$append.$fileSuffix" else "$fileName.$fileSuffix"
}

