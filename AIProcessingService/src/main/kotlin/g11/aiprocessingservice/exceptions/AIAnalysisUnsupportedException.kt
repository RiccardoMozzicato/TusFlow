package g11.aiprocessingservice.exceptions

/**
 * Exception thrown when AI analysis is not supported for a file due to its content type
 * (e.g., video files or images exceeding the size limit).
 * Do NOT use this for transient failures such as LLM unavailability or expired API keys —
 * those should propagate as regular exceptions so the job is marked FAILED.
 */
class AIAnalysisUnsupportedException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class UnsupportedContentTypeException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)