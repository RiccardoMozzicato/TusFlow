package g11.tusuploaderservice.exceptions


class TusResumableException(
    val supportedVersion: String,
    override val message: String = ""
) : RuntimeException()

class TusContentTypeException(
    override val message: String = ""
) : RuntimeException()

class TusOffsetException(
    override val message: String = ""
) : RuntimeException()

class MissingFieldException(
    override val message: String = ""
) : RuntimeException()

class UploadLengthException(
    override val message: String = ""
) : RuntimeException()

class UploadMetadataException(
    override val message: String = "",
    val key: String? = null,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

class ResourceNotFoundException(
    override val message: String = "",
    override val cause: Throwable? = null
) : RuntimeException(message,cause)

class ResourceAlreadyExistsException(
    override val message: String = "",
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

class StorageException(
    override val message: String = "",
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

class GenericException(
    override val message: String = "",
    override val cause: Throwable? = null
) : RuntimeException(message,cause)

class HashAlgorithmNotSupportedException(
    override val message: String = ""
) : RuntimeException(message)

class ChecksumMismatchException(
    override val message: String = ""
) : RuntimeException(message)

class ExpirationException(
    override val message: String = ""
) : RuntimeException(message)

class InvalidFormatException(
    override val message: String = "",
    override val cause: Throwable? = null
) : RuntimeException(message)

class UploadTooLargeException(
    override val message: String = ""
) : RuntimeException(message)

class ResourceUncompletedException(
    override val message: String = ""
): RuntimeException(message)

class ForbiddenException(
    override val message: String = ""
): RuntimeException(message)