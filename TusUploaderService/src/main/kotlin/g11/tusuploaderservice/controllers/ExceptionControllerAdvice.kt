package g11.tusuploaderservice.controllers

import g11.tusuploaderservice.exceptions.ChecksumMismatchException
import g11.tusuploaderservice.exceptions.ExpirationException
import g11.tusuploaderservice.exceptions.ForbiddenException
import g11.tusuploaderservice.exceptions.HashAlgorithmNotSupportedException
import g11.tusuploaderservice.exceptions.GenericException
import g11.tusuploaderservice.exceptions.InvalidFormatException
import g11.tusuploaderservice.exceptions.MissingFieldException
import g11.tusuploaderservice.exceptions.ResourceAlreadyExistsException
import g11.tusuploaderservice.exceptions.ResourceNotFoundException
import g11.tusuploaderservice.exceptions.StorageException
import g11.tusuploaderservice.exceptions.TusContentTypeException
import g11.tusuploaderservice.exceptions.TusOffsetException
import g11.tusuploaderservice.exceptions.TusResumableException
import g11.tusuploaderservice.exceptions.UploadLengthException
import g11.tusuploaderservice.exceptions.UploadMetadataException
import g11.tusuploaderservice.exceptions.UploadTooLargeException
import g11.tusuploaderservice.exceptions.ResourceUncompletedException
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ExceptionControllerAdvice(){

    @ExceptionHandler(TusResumableException::class)
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    fun handleTusResumableException(ex: TusResumableException): ResponseEntity<Void> {
        return ResponseEntity
            .status(HttpStatus.PRECONDITION_FAILED)
            .header("Tus-Resumable", ex.supportedVersion)
            .build()
    }

    @ExceptionHandler(TusContentTypeException::class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    fun handleTusContentTypeException(ex: TusContentTypeException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            ex.message ?: "Conflict with Content-Type"
        )
        return problemDetail
    }

    @ExceptionHandler(TusOffsetException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleTusOffsetException(ex: TusOffsetException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.message ?: "Conflict with Offset"
        )
        return problemDetail
    }

    @ExceptionHandler(MissingFieldException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMissingFieldException(ex: MissingFieldException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.message ?: "Missing required field"
        )
        return problemDetail
    }

    @ExceptionHandler(UploadTooLargeException::class)
    @ResponseStatus(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
    fun handleUploadTooLargeException(ex: UploadTooLargeException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.REQUEST_ENTITY_TOO_LARGE,
            ex.message ?: "Upload length exceeds allowed size"
        )
        return problemDetail
    }

    @ExceptionHandler(UploadLengthException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUploadLengthException(ex: UploadLengthException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.message ?: "Invalid Upload-Length or Upload-Defer-Length"
        )
        return problemDetail
    }

    @ExceptionHandler(UploadMetadataException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUploadMetadataException(ex: UploadMetadataException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.message ?: "Invalid Upload-Metadata"
        )
        return problemDetail
    }

    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleConstraintViolation(ex: ConstraintViolationException): ProblemDetail {
        val message = ex.constraintViolations.joinToString("; ") { v ->
            "${v.propertyPath}: ${v.message}"
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message)
    }

    @ExceptionHandler(HashAlgorithmNotSupportedException::class)
    fun handleHashAlgorithmNotSupportedException(ex: HashAlgorithmNotSupportedException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.message ?: "Hash algorithm not supported"
        )
        return problemDetail
    }

    @ExceptionHandler(ChecksumMismatchException::class)
    fun handleChecksumMismatchException(ex: ChecksumMismatchException): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(460),
            ex.message ?: "Checksum mismatch"
        )
        return problemDetail
    }

    @ExceptionHandler(GenericException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGenericException(ex: GenericException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "Internal Server Error")
    }

    @ExceptionHandler(ResourceAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleResourceAlreadyExistsException(ex: ResourceAlreadyExistsException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Resource already exists")
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleResourceNotFoundException(ex: ResourceNotFoundException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")
    }

    @ExceptionHandler(StorageException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleStorageException(ex: StorageException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "Storage error")
    }

    @ExceptionHandler(ExpirationException::class)
    @ResponseStatus(HttpStatus.GONE)
    fun handleExpireException(ex: ExpirationException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.message ?: "Upload has expired")
    }

    @ExceptionHandler(InvalidFormatException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidFormatException(ex: InvalidFormatException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid format")
    }

    @ExceptionHandler(ResourceUncompletedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleResourceUncompletedException(ex: ResourceUncompletedException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ex.message ?: "This file is not fully uploaded"
        )
    }

    @ExceptionHandler(ForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleResourceUncompletedException(ex: ForbiddenException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            ex.message
        )
    }
}
