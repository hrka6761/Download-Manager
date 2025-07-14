package ir.hrka.download_manager.utilities

/**
 * Enum representing the mode of creating a file when a file with the same name already exists.
 */
enum class FileCreationMode {

    /**
     * Overwrite the existing file by deleting it before creating a new one.
     */
    Overwrite,

    /**
     * Use the existing file and append data to it without deleting.
     */
    Append,

    /**
     * Create a new file with a unique name, typically by appending a timestamp or other identifier,
     * to avoid overwriting the existing file.
     */
    CreateNew
}

