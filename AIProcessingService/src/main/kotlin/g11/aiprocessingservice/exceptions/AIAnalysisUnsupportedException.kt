package g11.aiprocessingservice.exceptions

/**
 * Exception thrown when AI analysis is not supported for a file
 * (e.g., after multiple failed attempts or unsupported content type)
 */
class AIAnalysisUnsupportedException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class UnsupportedContentTypeException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)