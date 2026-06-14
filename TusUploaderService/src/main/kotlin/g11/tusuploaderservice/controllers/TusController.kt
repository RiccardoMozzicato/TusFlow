package g11.tusuploaderservice.controllers

import g11.tusuploaderservice.conf.TusProperties
import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.dto.UploadDTO
import g11.tusuploaderservice.exceptions.MissingFieldException
import g11.tusuploaderservice.exceptions.TusContentTypeException
import g11.tusuploaderservice.exceptions.UploadLengthException
import g11.tusuploaderservice.exceptions.InvalidFormatException
import g11.tusuploaderservice.services.FileService
import g11.tusuploaderservice.services.FileServiceInterface
import g11.tusuploaderservice.services.TusServiceInterface
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import g11.tusuploaderservice.validator.TusContentTypeValidation
import g11.tusuploaderservice.validator.TusResumableValidation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.headers.Header
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Base64
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.LoggerFactory
import org.springframework.http.ProblemDetail
import java.util.UUID
import g11.tusuploaderservice.utils.parseMetadataHeader
import g11.tusuploaderservice.utils.getUserIdFromJwt
import org.springframework.web.bind.annotation.RequestParam
import kotlin.text.get


@RestController
@RequestMapping("/api/v1/uploads")
class TusController(val tus: TusProperties, val tusService: TusServiceInterface, private val fileService: FileServiceInterface) {
    private val log = LoggerFactory.getLogger(TusController::class.java)

    // Resolves the authenticated user (owner) id from the JWT for request logging.
    // Never throws so an API log line can't break the request flow.
    private fun currentUserOwnerId(): String =
        runCatching { getUserIdFromJwt().toString() }.getOrDefault("unknown")

    //region Swagger Documentation
    @Operation(
        tags = [ "Tus Server Capabilities" ],
        summary = "Get server TUS capabilities",
        description = "Returns info about the TUS protocol versions and extensions supported by the server.",
        responses = []
    )
    @ApiResponse(
        responseCode = "204", headers = [
            Header(
                name = "Tus-Version",
                description = "Supported TUS protocol version"
            ),
            Header(
                name = "Tus-Extension",
                description = "Supported TUS protocol extensions"
            ),
            Header(
                name = "Tus-Checksum-Algorithm",
                description = "Supported checksum algorithms"
            ),
            Header(
                name = "Tus-Max-Size",
                description = "Maximum allowed upload size"
            ),
            Header(
                name = "vary",
                description = "Indicates which headers can affect the response"
            )
        ]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Internal Server Error",
                      "title": "Server error",
                      "status": 500,
                      "detail": "An unexpected error occurred",
                      "instance": "/uploads"
                    }
                    """
                    )
                ]
            )
        ]
    )
    //endregion
    @RequestMapping("", method = [RequestMethod.OPTIONS])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    open fun optionsUploads(res: HttpServletResponse) {
        log.debug("API OPTIONS /api/v1/uploads userOwnerId={}", currentUserOwnerId())

        res.setHeader("Tus-Version", tus.version)
        res.setHeader("Tus-Extension", tus.extensions?.joinToString(separator = ","))
        res.setHeader("Tus-Checksum-Algorithm", tusService.getSupportedHashAlgorithms().joinToString(", "))
        res.setHeader("Tus-Max-Size", tus.maxSize)

        res.setHeader("vary", tus.vary)
        res.setHeader("Access-Control-Allow-Methods", tus.allowMethods?.joinToString(separator = ","))
        res.setHeader("Access-Control-Allow-Headers", tus.allowHeaders?.joinToString(separator = ","))
        res.setHeader("Access-Control-Expose-Headers", tus.exposeHeaders?.joinToString(separator = ","))

    }
    //region Swagger Do   @ApiResponse(
    //       responseCode = "404",
    //       description = "File not found. Returned when the specified file UUID does not exist.",
    //       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    //   )cumentation
    @Operation(
        tags = [ "File Handling" ],
        summary = "Create a new upload",
        description = "Creates a new file upload resource on the server and returns its location.",
        parameters = [
            Parameter(
                name = "Tus-Resumable",
                description = "Tus-Version",
                schema = Schema(type = "string"),
                required = true,
                `in` = ParameterIn.HEADER
            ),
            Parameter(
                name = "Content-Type",
                schema = Schema(type = "string", example = "application/offset+octet-stream"),
                `in` = ParameterIn.HEADER
            )
        ]
    )
    @ApiResponse(
        responseCode = "201",
        description = "Created",
        headers = [
            Header(
                name = "Location",
                description = "URI of the newly created upload resource",
                schema = Schema(type = "string", example = "http://localhost:8080/uploads/c22c49d6-04fd-444f-87ca-8e9e64f43f50")
            ),
            Header(
                name = "Upload-Expires",
                description = "Expiration time of the upload",
                schema = Schema(type = "string", example = "Fri, 01 Nov 2025 15:30:00 GMT")
            ),
            Header(
                name = "Upload-Offset",
                description = "Current offset after creation (if data was uploaded)",
                schema = Schema(type = "string", example = "0")
            )
        ]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Bad Request",
                      "title": "Missing required header",
                      "status": 400,
                      "detail": "Upload-Length header is required",
                      "instance": "/uploads"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "410",
        description = "Gone",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Gone",
                      "title": "Upload expired",
                      "status": 410,
                      "detail": "Upload has expired and is no longer available",
                      "instance": "/uploads"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "412",
        description = "Precondition Failed",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Precondition Failed",
                      "title": "Unsupported protocol version",
                      "status": 412,
                      "detail": "Tus-Resumable version not supported",
                      "instance": "/uploads"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "413",
        description = "Request Entity Too Large",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Request Entity Too Large",
                      "title": "Upload size exceeds limit",
                      "status": 413,
                      "detail": "Upload-Length must be lower than maximum allowed size",
                      "instance": "/uploads"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "415",
        description = "Unsupported Media Type",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Unsupported Media Type",
                      "title": "Invalid Content-Type",
                      "status": 415,
                      "detail": "Content-Type must be application/offset+octet-stream for creation-with-upload",
                      "instance": "/uploads"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "460",
        description = "Checksum Mismatch",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Checksum Mismatch",
                      "title": "Checksum validation failed",
                      "status": 460,
                      "detail": "Calculated checksum does not match provided checksum",
                      "instance": "/uploads"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Internal Server Error",
                      "title": "Server error",
                      "status": 500,
                      "detail": "An unexpected error occurred",
                      "instance": "/uploads"
                    }
                    """
                    )
                ]
            )
        ]
    )
//endregion
    @RequestMapping("", method = [RequestMethod.POST])
    @ResponseStatus(HttpStatus.CREATED)
    open fun postUploads(
        @RequestParam("fileId") fileId: UUID,
        @RequestParam("sharingScope", required = false) scope: SharingScope?,
        @RequestHeader("Upload-Length", required = false) uploadLength: String?,
        @RequestHeader("Upload-Defer-Length", required = false) deferLength: String?,
        @RequestHeader("Upload-Metadata", required = false) uploadMetadata: String?,
        req: HttpServletRequest,
        @RequestHeader("Content-Length", required = false) contentLength: Long?,
        @RequestHeader("Upload-Checksum", required = false) checksumHeader: String?,
        @RequestHeader("Content-Type", required = false) contentType: String?,
        res: HttpServletResponse
    ) {
        log.debug("API POST /api/v1/uploads userOwnerId={} fileId={}", currentUserOwnerId(), fileId)

        //if no scope is provided, default to PERSONAL
        val sharingScope = scope ?: SharingScope.PERSONAL

        // Creates a Map Key,Value from the upload-metadata header, decoding the base64 values
        val parsedUploadMetadata: Map<String, String>? = parseMetadataHeader(uploadMetadata)

        val filename = parsedUploadMetadata?.get("filename")
        log.debug("Upload metadata parsed fileId={} filename={}", fileId, filename)

        var file = UploadDTO(
            filename = filename,
            fileUuid = fileId,
            uploadLength = uploadLength?.toLong(),
            uploadDeferLength = deferLength?.toInt(),
            uploadMetadata = parsedUploadMetadata,
            checksum = checksumHeader,
            contentType = contentType,
            sharingScope = sharingScope
        )

        log.debug("CONTROLLER - CREATE UPLOAD: \n {}", file)
        var uploadExpires: String? = null

        println("Content-Length durante la prima post:${contentLength}")

        //creation with upload
        if (contentLength != null && contentLength > 0) {
            log.info("Starting upload chunk fileId={} size={}", fileId, contentLength)
            contentType?.let {
                // Extract media type without parameters (e.g., remove ";charset=UTF-8")
                val mediaType = it.split(";").firstOrNull()?.trim()
                if (mediaType != "application/offset+octet-stream") {
                    log.warn("Invalid Content-Type for upload fileId={} contentType={}", fileId, contentType)
                    throw TusContentTypeException("Invalid Content-Type for upload - must be provided for TUS protocol")
                }
            } ?: throw MissingFieldException("Missing Content-Type header for upload")

            val chunk: ByteArray = req.inputStream.readAllBytes()
            if (chunk.size.toLong() != contentLength) {
                log.warn("Upload length mismatch fileId={} expected={} actual={}", fileId, contentLength, chunk.size)
                throw UploadLengthException("Content-Length header does not match chunk size")
            }

            if (checksumHeader != null) {
                log.debug("Checksum validation started fileId={}", fileId)
                tusService.executeChecksum(chunk, checksumHeader)
            }

            file = tusService.createUploadWithChunk(file, chunk)
            log.info("Upload chunk processed fileId={} offset={}", fileId, file.uploadOffset)
            res.setHeader("Upload-Offset", file.uploadOffset.toString())
            uploadExpires = file.uploadExpires
        }
        else {
            log.debug("Initializing empty upload fileId={}", fileId)
            file = tusService.createUpload(file)
            log.info("Upload initialized fileId={}", fileId)
        }

        val location = "http://localhost:8080/api/v1/uploads/${file.id}"
        log.debug("Upload location set fileId={} location={}", fileId, location)
        res.setHeader("Location", location)
        res.setHeader("Upload-Expires", uploadExpires)
    }


    //region Swagger  Documentation
    @Operation(
        tags = [ "File Handling" ],
        summary = "Get upload status",
        description = "Retrieve the current status of an upload including offset, length, metadata, and expiration.",
        parameters = [
            Parameter(
                name = "Tus-Resumable",
                description = "Tus-Version",
                schema = Schema(type = "string"),
                required = true,
                `in` = ParameterIn.HEADER
            ),
        ]
    )
    @ApiResponse(
        responseCode = "204",
        headers = [
            Header(
                name = "Upload-Offset",
                description = "Current offset of the upload in bytes",
                schema = Schema(type = "string", example = "1024")
            ),
            Header(
                name = "Upload-Length",
                description = "Total length of the resource in bytes",
                schema = Schema(type = "string", example = "10485760")
            ),
            Header(
                name = "Upload-Expires",
                description = "Expiration time of the upload",
                schema = Schema(type = "string", example = "Fri, 01 Nov 2025 15:30:00 GMT")
            ),
            Header(
                name = "Upload-Metadata",
                description = "Metadata associated with the upload",
                schema = Schema(type = "string", example = "filename ZXhhbXBsZS50eHQ=,filetype dGV4dC9wbGFpbg==")
            ),
            Header(
                name = "Cache-Control",
                description = "Cache control directives for the response",
                schema = Schema(type = "string", example = "no-store")
            )
        ]
    )

    @ApiResponse(
        responseCode = "404",
        description = "Not Found",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Not Found",
                      "title": "Resource not found",
                      "status": 404,
                      "detail": "Upload resource with the specified ID does not exist",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "410",
        description = "Gone",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Gone",
                      "title": "Upload expired",
                      "status": 410,
                      "detail": "Upload has expired and is no longer available",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "412",
        description = "Precondition Failed",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Precondition Failed",
                      "title": "Unsupported protocol version",
                      "status": 412,
                      "detail": "Tus-Resumable version not supported",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Internal Server Error",
                      "title": "Server error",
                      "status": 500,
                      "detail": "An unexpected error occurred during upload",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
//endregion
    @RequestMapping("/{id}", method = [RequestMethod.HEAD])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    open fun headUploads(@PathVariable id: String, req: HttpServletRequest, res: HttpServletResponse) {
        log.debug("API HEAD /api/v1/uploads/{} userOwnerId={} uploadId={}", id, currentUserOwnerId(), id)
        val result = tusService.getUploadInfo(id)

        log.debug(
            "Upload info resolved id={} offset={} length={} defer={} expiresPresent={} metadataPresent={}",
            id,
            result.uploadOffset,
            result.uploadLength,
            result.uploadDeferLength,
            result.uploadExpires != null,
            !result.uploadMetadata.isNullOrEmpty()
        )

        res.setHeader("Upload-Offset", result.uploadOffset.toString())
        result.uploadLength?.let { res.setHeader("Upload-Length", it.toString()) }
        result.uploadDeferLength?.let { res.setHeader("Upload-Defer-Length", it.toString()) }
        result.uploadExpires?.let { res.setHeader("Upload-Expires", it) }
        result.uploadMetadata
            ?.takeIf { it.isNotEmpty() }
            ?.let { metadata ->
                val headerValue = metadata.entries.joinToString(",") { (k, v) ->
                    "$k ${Base64.getEncoder().encodeToString(v.encodeToByteArray())}"
                }
                res.setHeader("Upload-Metadata", headerValue)
            }
        res.setHeader("Tus-Resumable", tus.version)
        res.setHeader("Cache-Control", "no-store")
    }
//region Swagger Documentation
    @Operation(
        tags = [ "File Handling" ],
        summary = "Upload File Chunk",
        description = "Uploads a chunk of a specified file to the server.",
        parameters = [
            Parameter(
                name = "Tus-Resumable",
                description = "Tus-Version",
                schema = Schema(type = "string"),
                required = true,
                `in` = ParameterIn.HEADER
            ),
            Parameter(
                name = "Upload-Defer-Length",
                schema = Schema(type = "integer"),
                `in` = ParameterIn.HEADER
            ),
            Parameter(
                name = "Upload-Length",
                schema = Schema(type = "integer"),
                `in` = ParameterIn.HEADER
            )
        ]
    )
    @ApiResponse(
        responseCode = "204",
        description = "Chunk uploaded successfully",
        headers = [
            Header(
                name = "Upload-Offset",
                description = "The new offset after the chunk upload",
                schema = Schema(type = "string", example = "2048")
            ),
            Header(
                name = "Tus-Resumable",
                description = "The tus protocol version used",
                schema = Schema(type = "string", example = "1.0.0")
            ),
            Header(
                name = "Upload-Expires",
                description = "Expiration time of the upload",
                schema = Schema(type = "string", example = "Fri, 01 Nov 2025 15:30:00 GMT")
            )
        ]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Bad Request",
                      "title": "Invalid request",
                      "status": 400,
                      "detail": "Upload length exceeds maximum allowed size",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Not Found",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Not Found",
                      "title": "Resource not found",
                      "status": 404,
                      "detail": "Upload resource with the specified ID does not exist",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Conflict",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Conflict",
                      "title": "Offset mismatch",
                      "status": 409,
                      "detail": "Upload-Offset does not match current file offset",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "410",
        description = "Gone",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Gone",
                      "title": "Upload expired",
                      "status": 410,
                      "detail": "Upload has expired and is no longer available",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "412",
        description = "Precondition Failed",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Precondition Failed",
                      "title": "Unsupported protocol version",
                      "status": 412,
                      "detail": "Tus-Resumable version not supported",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "413",
        description = "Request Entity Too Large",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Request Entity Too Large",
                      "title": "Upload size exceeds limit",
                      "status": 413,
                      "detail": "File length exceeds expected length",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "415",
        description = "Unsupported Media Type",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Unsupported Media Type",
                      "title": "Invalid Content-Type",
                      "status": 415,
                      "detail": "Content-Type must be application/offset+octet-stream",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "460",
        description = "Checksum Mismatch",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Checksum Mismatch",
                      "title": "Checksum validation failed",
                      "status": 460,
                      "detail": "Calculated checksum does not match provided checksum",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Internal Server Error",
                      "title": "Server error",
                      "status": 500,
                      "detail": "An unexpected error occurred during upload",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    //endregion
    @RequestMapping("/{id}", method = [RequestMethod.PATCH])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    open fun patchUploads(
        @PathVariable id: String,
        @RequestHeader("Tus-Resumable") @TusResumableValidation version: String,
        @RequestHeader("Content-Type") @TusContentTypeValidation contentType: String,
        @RequestHeader("Upload-Offset") offset: Long,
//        @RequestHeader("Content-Length") contentLength: Long, // not working apigateway removes this header
        req: HttpServletRequest,
        res: HttpServletResponse
    ) {
        log.debug("API PATCH /api/v1/uploads/{} userOwnerId={} uploadId={}", id, currentUserOwnerId(), id)

    req.headerNames.toList().forEach { headerName ->
            log.debug("$headerName: ${req.getHeader(headerName)}")
        }
        val contentLength = req.getHeader("Content-Length")

        val uuid: UUID
        try{
            uuid= UUID.fromString(id)
        } catch (e: IllegalArgumentException){
            log.warn("Invalid UUID format in PATCH upload id={}", id)
            throw InvalidFormatException("Invalid UUID format for upload ID", e)
        }

        val chunk: ByteArray = req.inputStream.readAllBytes()
        val uploadLength = req.getHeader("Upload-Length")?.toLong()

//        // Validate Content-Length if present (not present when using Transfer-Encoding: chunked)
//        if (contentLength != null && chunk.size != contentLength.toInt()) {
//            throw UploadLengthException("Content-Length header does not match chunk size")
//        }

        var uploadDto =
            UploadDTO(uuid, uploadOffset = offset, uploadLength = uploadLength)

        val checksumHeader = req.getHeader("Upload-Checksum")

        if (checksumHeader != null) {
            log.debug("Checksum validation started uploadId={}", id)
            tusService.executeChecksum(chunk, checksumHeader)
        }

        log.debug("Uploading chunk received: uploadId={} offset={} size={}", id, offset, chunk.size)

        uploadDto = tusService.appendChunk(uploadDto, chunk)

        log.info("Chunk appended successfully uploadId={} newOffset={}", id, uploadDto.uploadOffset)
        res.setHeader("Tus-Resumable", tus.version)
        res.setHeader("Upload-Offset", uploadDto.uploadOffset.toString())

        if (uploadDto.uploadExpires != null) {
            log.debug("Upload expiration updated uploadId={} expires={}", id, uploadDto.uploadExpires)
            res.setHeader("Upload-Expires", uploadDto.uploadExpires)
        }
    }
//region Swagger Documentation
    @Operation(
        tags = [ "File Handling" ],
        summary = "Delete upload",
        description = "Terminates an upload and deletes all associated data including the file and metadata.",
        parameters = [
            Parameter(
                name = "Tus-Resumable",
                description = "Tus-Version",
                schema = Schema(type = "string"),
                required = true,
                `in` = ParameterIn.HEADER
            ),
        ]
    )
    @ApiResponse(
        responseCode = "204",
        description = "Upload deleted successfully",
        headers = [
            Header(
                name = "Tus-Resumable",
                description = "The tus protocol version used",
                schema = Schema(type = "string", example = "1.0.0")
            )
        ]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Not Found",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Not Found",
                      "title": "Resource not found",
                      "status": 404,
                      "detail": "Upload resource with the specified ID does not exist",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "410",
        description = "Gone",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Gone",
                      "title": "Upload expired",
                      "status": 410,
                      "detail": "Upload has expired and is no longer available",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "412",
        description = "Precondition Failed",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Precondition Failed",
                      "title": "Unsupported protocol version",
                      "status": 412,
                      "detail": "Tus-Resumable version not supported",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [
            Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                    {
                      "type": "Internal Server Error",
                      "title": "Server error",
                      "status": 500,
                      "detail": "An unexpected error occurred during upload",
                      "instance": "/uploads/{id}"
                    }
                    """
                    )
                ]
            )
        ]
    )
    //endregion
    @RequestMapping("/{id}", method = [RequestMethod.DELETE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    open fun deleteUploads(
        @PathVariable("id") file_id: String,
        req: HttpServletRequest,
        res: HttpServletResponse
    ) {
        log.debug("API DELETE /api/v1/uploads/{} userOwnerId={} uploadId={}", file_id, currentUserOwnerId(), file_id)
        tusService.terminateUpload(file_id)
        res.setHeader("Tus-Resumable", tus.version)
    }
}
