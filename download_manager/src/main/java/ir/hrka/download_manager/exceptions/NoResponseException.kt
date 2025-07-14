package ir.hrka.download_manager.exceptions

import java.io.IOException

/**
 * Exception thrown when the server responds with an error or returns an empty response.
 *
 * This exception extends [IOException] and is typically used to indicate
 * that the download request failed due to a missing or invalid server response.
 *
 * @param message The detail message for the exception. Defaults to
 * "Server responded with an error or empty response."
 */
class NoResponseException(
    message: String = "Server responded with an error or empty response."
) : IOException(message)