package g11.aiprocessingservice.exceptions

class JobNotFoundException(
    message: String = "Job not found",
    cause: Throwable? = null
) : Exception(message, cause)

class JobNotFailedException(
    message: String = "Cannot retry a not failed job",
    cause: Throwable? = null
) : Exception(message, cause)