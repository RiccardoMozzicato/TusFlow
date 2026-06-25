package g11.tusuploaderservice.controllers

import g11.tusuploaderservice.dto.DownloadDTO
import g11.tusuploaderservice.dto.FileDTO
import g11.tusuploaderservice.dto.FileShareDTO
import g11.tusuploaderservice.dto.FileVersionDTO
import g11.tusuploaderservice.dto.FileVersionFavoriteDTO
import g11.tusuploaderservice.dto.FiltersDTO
import g11.tusuploaderservice.dto.MetadataAIDTO
import g11.tusuploaderservice.dto.Ownership
import g11.tusuploaderservice.dto.PendingUploadDTO
import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.entities.PERMISSION_LEVEL
import g11.tusuploaderservice.entities.FileStatus
import g11.tusuploaderservice.exceptions.ResourceNotFoundException
import g11.tusuploaderservice.exceptions.ResourceUncompletedException
import g11.tusuploaderservice.services.FileService
import g11.tusuploaderservice.services.FileServiceInterface
import g11.tusuploaderservice.services.StorageService
import g11.tusuploaderservice.utils.getUserIdFromJwt
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.headers.Header
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Date
import java.util.UUID

/*
@PreAuthorize
@PostAuthorize
@PreFilter
@PostFilter
@Secured
*/

@RestController
@RequestMapping("/api/v1/files")
class FileController(
    private val fileService: FileServiceInterface,
) {
    private val log = LoggerFactory.getLogger(FileController::class.java)

    // Resolves the authenticated user (owner) id from the JWT for request logging.
    // Never throws so an API log line can't break the request flow.
    private fun currentUserOwnerId(): String =
        runCatching { getUserIdFromJwt().toString() }.getOrDefault("unknown")

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Get list of files metadata",
        description = "Retrieves a paginated list of files visible to the current user in the requested sharing scopes (combined with OR). Files whose current version is AVAILABLE or MERGE_PENDING (still processing) are returned; DELETED versions are excluded. Returns an empty page when no files match.",
        parameters = [
            Parameter(name = "sharingScope", description = "Sharing scopes to include, comma-separated (PERSONAL, DEPARTMENT, ORGANIZATION)", schema = Schema(type = "string", example = "PERSONAL,DEPARTMENT")),
            Parameter(name = "contentType", description = "Filter by content type", schema = Schema(type = "string", example = "application/pdf")),
            Parameter(name = "excludeContentTypes", description = "Keep only files whose content type matches none of these tokens (repeatable); used for the 'Other' file-type bucket", schema = Schema(type = "string", example = "application/pdf")),
            Parameter(name = "dateFrom", description = "Filter files created from this date (ISO format)", schema = Schema(type = "string", example = "2024-01-01T00:00:00Z")),
            Parameter(name = "dateTo", description = "Filter files created until this date (ISO format)", schema = Schema(type = "string", example = "2024-12-31T23:59:59Z")),
            Parameter(name = "sort", description = "Sort field and direction (e.g., 'createdAt,desc')", schema = Schema(type = "string", example = "createdAt,desc")),
            Parameter(name = "search", description = "Free-text query matched case-insensitively against the file name (substring)", schema = Schema(type = "string", example = "report")),
            Parameter(name = "page", description = "Page number (0-indexed)", schema = Schema(type = "integer", example = "0")),
            Parameter(name = "size", description = "Page size", schema = Schema(type = "integer", example = "10"))
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved list of files. Returns a paginated list of file metadata matching the filter criteria.",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = Page::class),
            examples = [ExampleObject(value = """
                {
                  "content": [
                    {
                      "id": "5a7e3b77-8d5a-45ab-9b1f-6e1b1e3cb92a",
                      "fileName": "report.pdf",
                      "createdAt": "2025-01-14T10:12:32Z",
                      "completedAt": "2025-01-14T10:20:00Z",
                      "version": 3
                    }
                  ],
                  "pageable": { "pageNumber": 0, "pageSize": 10 },
                  "totalElements": 1,
                  "totalPages": 1,
                  "first": true,
                  "last": true,
                  "size": 10,
                  "number": 0
                }
            """)]
        )]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request - Invalid parameters",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "InvalidParameters",
                summary = "One or more query parameters are invalid",
                value = """
                {
                  "type": "Bad Request",
                  "title": "Invalid request parameters",
                  "status": 400,
                  "detail": "Sort format must be 'field,asc|desc' and dates must be ISO-8601",
                  "instance": "/api/v1/files"
                }
                """
            )]
        )]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "InternalServerError",
                summary = "Unexpected server error",
                value = """
                {
                  "type": "Internal Server Error",
                  "title": "Server error",
                  "status": 500,
                  "detail": "An unexpected error occurred",
                  "instance": "/api/v1/files"
                }
                """
            )]
        )]
    )
    //endregion
    @GetMapping
    fun getFiles(
        @RequestParam(required = false) contentType: String?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(defaultValue = "createdAt,desc") sort: String,
        @RequestParam(defaultValue = "0") page: UInt,
        @RequestParam(defaultValue = "10") size: UInt,
        @RequestParam(defaultValue = "PERSONAL") sharingScope: Set<SharingScope>,
        @RequestParam(required = false) ownership: Ownership?,
        @RequestParam(required = false) excludeContentTypes: List<String>?,
        @RequestParam(required = false) search: String?,
    ): ResponseEntity<Page<FileDTO>> {

        log.debug("API GET /api/v1/files userOwnerId={}", currentUserOwnerId())
        log.debug("Requested file metadata: contentType={}, excludeContentTypes={}, dateFrom={}, dateTo={}, sort={}, page={}, size={}, sharingScope={}, ownership={}, search={}", contentType, excludeContentTypes, dateFrom, dateTo, sort, page, size, sharingScope, ownership, search)
        // Parse sort parameter
        val sortParts = sort.split(",")
        val sortField = sortParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "createdAt"
        val sortDirection = if (sortParts.getOrNull(1)?.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC

        val filtersDTO = FiltersDTO(contentType, dateFrom, dateTo, sortField, sortDirection, page, size, sharingScope, ownership, excludeContentTypes, search)

        val files = fileService.getFiles(filtersDTO)
        log.debug("Retrieved {} files with applied filters", files.totalElements)

        return ResponseEntity.ok(files)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Get file metadata by ID",
        description = "Retrieves detailed metadata for a specific file by its UUID.",
        parameters = [
            Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid"))
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved file metadata",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = FileDTO::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Resource does not exist, no metadata found for specified ID",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(value = """{"type":"Not Found","title":"File not found","status":404,"detail":"File with specified ID does not exist","instance":"/api/v1/files/{id}"}""")])]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "InternalServerError",
                summary = "Unexpected server error",
                value = """
                {
                  "type": "Internal Server Error",
                  "title": "Server error",
                  "status": 500,
                  "detail": "An unexpected error occurred",
                  "instance": "/api/v1/files/{id}"
                }
                """
            )]
        )]
    )
    //endregion
    @GetMapping("/{id}")
    fun getFileMetadata(@PathVariable id: UUID): ResponseEntity<FileDTO> {
        log.debug("API GET /api/v1/files/{} userOwnerId={} fileId={}", id, currentUserOwnerId(), id)
        val fileDTO = fileService.getFileMetadata(id)
        log.debug("Successfully retrieved file metadata for id: {}", id)
        return ResponseEntity.ok(fileDTO)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Get AI-generated metadata for a file",
        description = "Retrieves AI-generated metadata for a specific file version, including tags, summary, sensitivity, and other AI-analyzed fields.",
        parameters = [
            Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
            Parameter(name = "version", description = "File version (optional, defaults to current version)", required = false, `in` = ParameterIn.QUERY, schema = Schema(type = "integer", format = "int64"))
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved AI-generated metadata",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = MetadataAIDTO::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "File or file version not found",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(value = """{"type":"Not Found","title":"File not found","status":404,"detail":"File with specified ID does not exist","instance":"/api/v1/files/generatedmetadata/{id}"}""")])]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "InternalServerError",
                summary = "Unexpected server error",
                value = """
                {
                  "type": "Internal Server Error",
                  "title": "Server error",
                  "status": 500,
                  "detail": "An unexpected error occurred",
                  "instance": "/api/v1/files/generatedmetadata/{id}"
                }
                """
            )]
        )]
    )
    //endregion
    @GetMapping("/generatedmetadata/{id}")
    fun getFileGeneratedMetadata(
        @PathVariable id: UUID,
        @RequestParam(required = false) version: Long?
    ): ResponseEntity<MetadataAIDTO> {
        log.debug("API GET /api/v1/files/generatedmetadata/{} userOwnerId={} fileId={}, version={}", id, currentUserOwnerId(), id, version)
        val metadata = fileService.getFileGeneratedMetadata(id, version)
        log.debug("Successfully retrieved AI-generated metadata for file: {}", id)
        return ResponseEntity.ok(metadata)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Update AI-generated metadata for a file",
        description = "Updates AI-generated metadata (summary, tags, sensitivity) for a specific file version.",
        parameters = [
            Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
            Parameter(name = "version", description = "File version (optional, defaults to current version)", required = false, `in` = ParameterIn.QUERY, schema = Schema(type = "integer", format = "int64"))
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "AI metadata successfully updated"
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request - Invalid metadata or empty payload",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "File or file version not found",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    )
    //endregion
    @PatchMapping("/generatedmetadata/{id}")
    fun updateFileGeneratedMetadata(
        @PathVariable id: UUID,
        @RequestParam(required = false) version: Long?,
        @RequestBody metadata: Map<String, String>
    ): ResponseEntity<HttpStatus> {
        log.debug("API PATCH /api/v1/files/generatedmetadata/{} userOwnerId={} fileId={}, version={}", id, currentUserOwnerId(), id, version)

        if (metadata.isEmpty()) {
            log.warn("Rejected generated-metadata update: empty payload fileId={}", id)
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Metadata cannot be empty"
            )
        }

        metadata.keys.forEach { key ->
            if (!key.matches(Regex("\\A\\p{ASCII}+\\z"))) {
                log.warn("Rejected generated-metadata update: non-ASCII key fileId={} key={}", id, key)
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Metadata key '$key' must be ASCII encoded"
                )
            }
        }

        fileService.updateGeneratedMetadataByUser(id, version, metadata)
        log.info("Generated metadata updated successfully fileId={} version={}", id, version)
        return ResponseEntity.ok(HttpStatus.OK)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Update file metadata",
        description = "Updates metadata for a specific file version. All metadata keys must be ASCII encoded.",
        parameters = [
            Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
            Parameter(name = "version", description = "File version number", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "integer", format = "int64"))
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "Metadata successfully updated"
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request - Invalid metadata or empty payload",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(value = """{"type":"Bad Request","title":"Invalid metadata","status":400,"detail":"Metadata key must be ASCII encoded","instance":"/api/v1/files/{id}/versions/{version}"}""")])]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Resource does not exist, no metadata found for specified ID",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "FileOrVersionNotFound",
                summary = "The file or the specific version does not exist",
                value = """
                {
                  "type": "Not Found",
                  "title": "File or version not found",
                  "status": 404,
                  "detail": "No metadata found for the given file/version",
                  "instance": "/api/v1/files/{id}/versions/{version}"
                }
                """
            )]
        )]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "InternalServerError",
                summary = "Unexpected server error",
                value = """
                {
                  "type": "Internal Server Error",
                  "title": "Server error",
                  "status": 500,
                  "detail": "An unexpected error occurred",
                  "instance": "/api/v1/files/{id}/versions/{version}"
                }
                """
            )]
        )]
    )
    //endregion
    @PatchMapping("/{id}/versions/{version}")
    fun updateFileMetadata(
        @PathVariable id: UUID,
        @PathVariable version: Long,
        @RequestBody metadata: Map<String, String>
    ): ResponseEntity<HttpStatus> {

        log.debug("API PATCH /api/v1/files/{}/versions/{} userOwnerId={} fileId={}, version={}", id, version, currentUserOwnerId(), id, version)
        // Check if metadata is empty
        if (metadata.isEmpty()) {
            log.warn("Rejected metadata update: empty payload fileId={} version={}", id, version)
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Metadata cannot be empty"
            )
        }

        // Validate metadata keys are ASCII
        metadata.keys.forEach { key ->
            if (!key.matches(Regex("\\A\\p{ASCII}+\\z"))) {
                log.warn("Rejected metadata update: non-ASCII key fileId={} version={} key={}", id, version, key)
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Metadata key '$key' must be ASCII encoded"
                )
            }
        }
        log.debug("Requested update for file metadata: fileId={}, version={}", id, version)
        fileService.updateFileMetadataByUser(id,version, metadata)
        log.info("File metadata updated successfully fileId={} version={}", id, version)
        return ResponseEntity.ok(HttpStatus.OK)

    }

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Delete file",
        description = "Deletes a file and all its associated metadata and versions.",
        parameters = [
            Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid"))
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "File successfully deleted"
    )
    @ApiResponse(
        responseCode = "404",
        description = "File not found",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "FileNotFound",
                summary = "File to delete does not exist",
                value = """
                {
                  "type": "Not Found",
                  "title": "File not found",
                  "status": 404,
                  "detail": "The requested file does not exist",
                  "instance": "/api/v1/files/{id}"
                }
                """
            )]
        )]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "InternalServerError",
                summary = "Unexpected server error",
                value = """
                {
                  "type": "Internal Server Error",
                  "title": "Server error",
                  "status": 500,
                  "detail": "An unexpected error occurred",
                  "instance": "/api/v1/files/{id}"
                }
                """
            )]
        )]
    )
    //endregion
    @DeleteMapping("/{id}")
    fun deleteFileMetadata(@PathVariable id: UUID): ResponseEntity<HttpStatus> {
        log.debug("API DELETE /api/v1/files/{} userOwnerId={} fileId={}", id, currentUserOwnerId(), id)

        fileService.deleteFile(id)
        log.info("File metadata deleted successfully for id: {}", id)
        return ResponseEntity.ok(HttpStatus.OK)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Download file",
        description = "Downloads the complete file content. File must be fully uploaded and merged.",
        parameters = [
            Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid"))
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "File successfully downloaded. The complete file content is returned with appropriate Content-Disposition and Content-Type headers.",
        headers = [
            Header(name = "Content-Disposition", description = "Attachment disposition with filename"),
            Header(name = "Content-Type", description = "File content type")
        ],
        content = [Content(mediaType = "application/octet-stream")]
    )
    @ApiResponse(
        responseCode = "404",
        description = "File not found",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "FileNotFound",
                summary = "File does not exist",
                value = """
               {
                 "type": "Not Found",
                 "title": "File not found",
                 "status": 404,
                 "detail": "The specified file does not exist",
                 "instance": "/api/v1/files/{id}/download"
               }
               """
            )]
        )]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Conflict - File upload not completed",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(value = """{"type":"Conflict","title":"File incomplete","status":409,"detail":"File upload is not complete","instance":"/api/v1/files/{id}/download"}""")])]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "InternalServerError",
                summary = "Unexpected server error",
                value = """
                {
                  "type": "Internal Server Error",
                  "title": "Server error",
                  "status": 500,
                  "detail": "An unexpected error occurred",
                  "instance": "/api/v1/files/{id}/download"
                }
                """
            )]
        )]
    )
    //endregion
    @GetMapping("/{id}/download")
    fun downloadFile(
        @PathVariable id: UUID,
        @RequestParam("version") version: Long? = null,
        res: HttpServletResponse
    ) {
        log.debug("API GET /api/v1/files/{}/download userOwnerId={} fileId={}, version={}", id, currentUserOwnerId(), id, version)

        val download: DownloadDTO = fileService.downloadFile(id, version)
        log.info("File download started: {} version: {}", id, version)
        res.setHeader("Content-Disposition", "attachment; filename=\"${download.filename}\"")
        res.setHeader("Content-Type", download.contentType)
        res.outputStream.use { output -> output.write(download.file) }
        log.debug("File download completed: {}", id)
    }

    @GetMapping("/{id}/preview")
    fun previewFile(
        @PathVariable id: UUID,
        @RequestParam("version") version: Long? = null,
        res: HttpServletResponse
    ) {
        log.debug("API GET /api/v1/files/{}/preview userOwnerId={} fileId={}, version={}", id, currentUserOwnerId(), id, version)

        val preview: DownloadDTO = fileService.downloadFile(id, version)
        log.info("File preview started: {} version: {}", id, version)
        res.setHeader("Content-Disposition", "inline; filename=\"${preview.filename}\"")
        res.setHeader("Content-Type", preview.contentType)
        res.outputStream.use { output -> output.write(preview.file) }
        log.debug("File preview completed: {}", id)
    }


   //region Swagger Documentation
   @Operation(
       tags = ["File Sharing"],
       summary = "Get file shares",
       description = "Retrieves a paginated list of users with whom the file is shared. Returns 404 if the file does not exist.",
       parameters = [
           Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
           Parameter(name = "page", description = "Page number (0-indexed)", schema = Schema(type = "integer", example = "0")),
           Parameter(name = "size", description = "Page size", schema = Schema(type = "integer", example = "10"))
       ]
   )
   @ApiResponse(
       responseCode = "200",
       description = "Successfully retrieved file shares. Returns a paginated list of share records for the given file.",
       content = [Content(
           mediaType = "application/json",
           schema = Schema(implementation = Page::class),
           examples = [ExampleObject(value = """
               {
                 "content": [
                   {
                     "id": 12,
                     "fileID": "5a7e3b77-8d5a-45ab-9b1f-6e1b1e3cb92a",
                     "userID": "c5d5b9a0-1f6f-4f11-bf9d-cb49b34a6c11",
                     "permission": "READ",
                     "timestamp": "2025-01-14T10:25:00Z"
                   }
                 ],
                 "pageable": { "pageNumber": 0, "pageSize": 10 },
                 "totalElements": 1,
                 "totalPages": 1
               }
           """)]
       )]
   )
   @ApiResponse(
       responseCode = "404",
       description = "File not found. Returned when the specified file UUID does not exist.",
       content = [Content(
           mediaType = "application/problem+json",
           schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "FileNotFound",
               summary = "File does not exist",
               value = """
               {
                 "type": "Not Found",
                 "title": "File not found",
                 "status": 404,
                 "detail": "The specified file does not exist",
                 "instance": "/api/v1/files/{id}/shares"
               }
               """
           )]
       )]
   )
   @ApiResponse(
       responseCode = "500",
       description = "Internal Server Error",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "InternalServerError",
               summary = "Unexpected server error",
               value = """
               {
                 "type": "Internal Server Error",
                 "title": "Server error",
                 "status": 500,
                 "detail": "An unexpected error occurred",
                 "instance": "/api/v1/files/{id}/shares"
               }
               """
           )]
       )]
   )
   //endregion
   @GetMapping("/{id}/shares")
   fun getFileShares(
       @PathVariable id: UUID,
       @RequestParam(defaultValue = "0") page: UInt,
       @RequestParam(defaultValue = "10") size: UInt
   ): ResponseEntity<Page<FileShareDTO>> {
       log.debug("API GET /api/v1/files/{}/shares userOwnerId={} fileId={}", id, currentUserOwnerId(), id)

       val shares = fileService.getFileShares(id, PageRequest.of(page.toInt(), size.toInt()))
       log.debug("Retrieved {} shares for file: {}", shares.totalElements, id)
       return ResponseEntity.ok(shares)
   }

   //region Swagger Documentation
   @Operation(
       tags = ["File Sharing"],
       summary = "Create file share",
       description = "Shares a file with a specific user, granting them the specified permission level. Returns 404 if the file or user does not exist.",
       parameters = [
           Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
           Parameter(name = "userId", description = "User UUID to share the file with", required = true, schema = Schema(type = "string", format = "uuid")),
           Parameter(name = "permission", description = "Permission level (READ or WRITE)", required = true, schema = Schema(type = "string", example = "READ"))
       ]
   )
   @ApiResponse(
       responseCode = "201",
       description = "File share successfully created. Returns the created share record.",
       content = [Content(mediaType = "application/json", schema = Schema(implementation = FileShareDTO::class))]
   )
   @ApiResponse(
       responseCode = "400",
       description = "Bad Request - Invalid parameters",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "InvalidParameters",
               summary = "Invalid permission or missing parameter",
               value = """
               {
                 "type": "Bad Request",
                 "title": "Invalid parameters",
                 "status": 400,
                 "detail": "permission must be one of [READ, WRITE]",
                 "instance": "/api/v1/files/{id}/shares"
               }
               """
           )]
       )]
   )
   @ApiResponse(
       responseCode = "404",
       description = "File or user not found. Returned when the file UUID does not exist or the user service reports the user is missing.",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "FileOrUserNotFound",
               summary = "File does not exist or user missing",
               value = """
               {
                 "type": "Not Found",
                 "title": "Resource not found",
                 "status": 404,
                 "detail": "File not found or user does not exist",
                 "instance": "/api/v1/files/{id}/shares"
               }
               """
           )]
       )]
   )
   @ApiResponse(
       responseCode = "502",
       description = "Bad Gateway - User service error. Returned when the user service responds with an error.",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "UserServiceError",
               summary = "User service downstream error",
               value = """
               {
                 "type": "Bad Gateway",
                 "title": "User service error",
                 "status": 502,
                 "detail": "Failed to contact user service",
                 "instance": "/api/v1/files/{id}/shares"
               }
               """
           )]
       )]
   )
   @ApiResponse(
       responseCode = "500",
       description = "Internal Server Error",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "InternalServerError",
               summary = "Unexpected server error",
               value = """
               {
                 "type": "Internal Server Error",
                 "title": "Server error",
                 "status": 500,
                 "detail": "An unexpected error occurred",
                 "instance": "/api/v1/files/{id}/shares"
               }
               """
           )]
       )]
   )
   //endregion
   @PostMapping("/{id}/shares")
   fun createFileShare(
       @PathVariable id: UUID,
       @RequestParam userId: UUID,
       @RequestParam permission: PERMISSION_LEVEL
   ): ResponseEntity<FileShareDTO> {
       log.debug("API POST /api/v1/files/{}/shares userOwnerId={} fileId={} targetUserId={}, permission={}", id, currentUserOwnerId(), id, userId, permission)

       val fileShareDTO = FileShareDTO(null, id, userId, permission, Date.from(Instant.now()).toString())
       val share = fileService.createFileShare(fileShareDTO)
       return ResponseEntity.status(HttpStatus.CREATED).body(share)
   }

   //region Swagger Documentation
   @Operation(
       tags = ["File Sharing"],
       summary = "Get files shared with user",
       description = "Retrieves a paginated list of files that have been shared with the specified user. Returns 404 if the user does not exist.",
       parameters = [
           Parameter(name = "userId", description = "User UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
           Parameter(name = "page", description = "Page number (0-indexed)", schema = Schema(type = "integer", example = "0")),
           Parameter(name = "size", description = "Page size", schema = Schema(type = "integer", example = "10")),
           Parameter(name = "search", description = "Free-text query matched case-insensitively against the file name (substring)", schema = Schema(type = "string", example = "report"))
       ]
   )
   @ApiResponse(
       responseCode = "200",
       description = "Successfully retrieved files shared with user. Returns a paginated list of files accessible to the user.",
       content = [Content(
           mediaType = "application/json",
           schema = Schema(implementation = Page::class),
           examples = [ExampleObject(value = """
               {
                 "content": [
                   {
                     "id": "5a7e3b77-8d5a-45ab-9b1f-6e1b1e3cb92a",
                     "fileName": "report.pdf",
                     "createdAt": "2025-01-14T10:12:32Z",
                     "completedAt": "2025-01-14T10:20:00Z",
                     "version": 3
                   }
                 ],
                 "pageable": { "pageNumber": 0, "pageSize": 10 },
                 "totalElements": 1,
                 "totalPages": 1
               }
           """)]
       )]
   )
   @ApiResponse(
       responseCode = "404",
       description = "User not found. Returned when the user UUID does not exist in the user service.",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "UserNotFound",
               summary = "User does not exist",
               value = """
               {
                 "type": "Not Found",
                 "title": "User not found",
                 "status": 404,
                 "detail": "The specified user does not exist",
                 "instance": "/api/v1/files/shared-with/{userId}"
               }
               """
           )]
       )]
   )
   @ApiResponse(
       responseCode = "502",
       description = "Bad Gateway - User service error. Returned when the user service responds with an error.",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "UserServiceError",
               summary = "User service downstream error",
               value = """
               {
                 "type": "Bad Gateway",
                 "title": "User service error",
                 "status": 502,
                 "detail": "Failed to contact user service",
                 "instance": "/api/v1/files/shared-with/{userId}"
               }
               """
           )]
       )]
   )
   @ApiResponse(
       responseCode = "500",
       description = "Internal Server Error",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "InternalServerError",
               summary = "Unexpected server error",
               value = """
               {
                 "type": "Internal Server Error",
                 "title": "Server error",
                 "status": 500,
                 "detail": "An unexpected error occurred",
                 "instance": "/api/v1/files/shared-with/{userId}"
               }
               """
           )]
       )]
   )
   //endregion
   @GetMapping("/my-shared")
   fun getFilesSharedWithUser(
       @RequestParam(required = false) contentType: String?,
       @RequestParam(required = false) dateFrom: String?,
       @RequestParam(required = false) dateTo: String?,
       @RequestParam(defaultValue = "createdAt,desc") sort: String,
       @RequestParam(defaultValue = "0") page: UInt,
       @RequestParam(defaultValue = "10") size: UInt,
       @RequestParam(required = false) permission: PERMISSION_LEVEL?,
       @RequestParam(required = false) ownerId: UUID?,
       @RequestParam(required = false) excludeContentTypes: List<String>?,
       @RequestParam(required = false) search: String?,
   ): ResponseEntity<Page<FileDTO>> {
       log.debug(
           "API GET /api/v1/files/my-shared userOwnerId={} contentType={} excludeContentTypes={} dateFrom={} dateTo={} sort={} permission={} ownerId={} search={}",
           currentUserOwnerId(), contentType, excludeContentTypes, dateFrom, dateTo, sort, permission, ownerId, search,
       )

       // Parse sort; the shared listing is rooted at FileShare, so the sortable
       // fields live on the joined file (e.g. createdAt -> file.createdAt).
       val sortParts = sort.split(",")
       val sortField = sortParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "createdAt"
       val mappedField = if (sortField.contains(".")) sortField else "file.$sortField"
       val sortDirection = if (sortParts.getOrNull(1)?.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC
       val pageable = PageRequest.of(page.toInt(), size.toInt(), Sort.by(sortDirection, mappedField))

       val sharedFiles = fileService.getFilesSharedWithCurrentUser(
           ownerId = ownerId,
           permission = permission,
           contentType = contentType,
           dateFrom = dateFrom,
           dateTo = dateTo,
           excludeContentTypes = excludeContentTypes,
           search = search,
           pageable = pageable,
       )
       log.debug("Retrieved {} shared files for current user", sharedFiles.totalElements)
       return ResponseEntity.ok(sharedFiles)
   }

   //region Swagger Documentation
   @Operation(
       tags = ["File Management"],
       summary = "List the current user's resumable uploads",
       description = "Returns the in-flight uploads owned by the current user that can still be " +
           "resumed (status PENDING or UPLOADING, not expired). The client uses this to decide " +
           "which locally-persisted uploads to resume after a reload. No offset is returned: the " +
           "tus HEAD on /api/v1/uploads/{fileId} remains the authority on the current offset.",
   )
   @ApiResponse(responseCode = "200", description = "The caller's resumable uploads")
   //endregion
   @GetMapping("/my-uploads")
   fun getMyPendingUploads(): ResponseEntity<List<PendingUploadDTO>> {
       log.debug("API GET /api/v1/files/my-uploads userOwnerId={}", currentUserOwnerId())
       val uploads = fileService.getMyPendingUploads()
       log.debug("Retrieved {} resumable uploads for current user", uploads.size)
       return ResponseEntity.ok(uploads)
   }

   //region Swagger Documentation
   @Operation(
       tags = ["File Sharing"],
       summary = "Filter file ids to those the user can write",
       description = "Given a set of candidate file ids, returns the subset the current user has " +
           "WRITE access to: owned files, files shared with READ_WRITE, and (by role) department " +
           "files for managers and department/organization files for admins. Used by other " +
           "services to scope actions to files the user may modify, keeping the request bounded to " +
           "the caller's candidate set."
   )
   @ApiResponse(
       responseCode = "200",
       description = "Writable subset of the candidate ids",
       content = [Content(mediaType = "application/json", schema = Schema(implementation = UUID::class))]
   )
   //endregion
   @PostMapping("/writable-ids")
   fun getWritableFileIds(@RequestBody candidateIds: List<UUID>): ResponseEntity<List<UUID>> {
       if (candidateIds.isEmpty()) return ResponseEntity.ok(emptyList())
       if (candidateIds.size > MAX_WRITABLE_CANDIDATES) {
           return ResponseEntity.badRequest().build()
       }
       return ResponseEntity.ok(fileService.getWritableFileIds(candidateIds.distinct()))
   }

   //region Swagger Documentation
   @Operation(
       tags = ["File Sharing"],
       summary = "Delete file share",
       description = "Removes a file share, revoking the user's access to the file. Returns 404 if the file or share does not exist.",
       parameters = [
           Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
           Parameter(name = "shareId", description = "Share ID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "integer", format = "int64"))
       ]
   )
   @ApiResponse(
       responseCode = "200",
       description = "File share successfully deleted. Returns 200 OK to indicate the share was removed."
   )
   @ApiResponse(
       responseCode = "404",
       description = "File or share not found. Returned when either the file UUID does not exist or the share does not belong to the file.",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "ShareNotFoundOrMismatch",
               summary = "Share does not belong to file or missing",
               value = """
               {
                 "type": "Not Found",
                 "title": "File or share not found",
                 "status": 404,
                 "detail": "File not found or share does not belong to file",
                 "instance": "/api/v1/files/{id}/shares/{shareId}"
               }
               """
           )]
       )]
   )
   @ApiResponse(
       responseCode = "500",
       description = "Internal Server Error",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "InternalServerError",
               summary = "Unexpected server error",
               value = """
               {
                 "type": "Internal Server Error",
                 "title": "Server error",
                 "status": 500,
                 "detail": "An unexpected error occurred",
                 "instance": "/api/v1/files/{id}/shares/{shareId}"
               }
               """
           )]
       )]
   )
   //endregion
   @DeleteMapping("/{id}/shares/{shareId}")
   fun deleteFileShare(
       @PathVariable id: UUID,
       @PathVariable shareId: Long
   ): ResponseEntity<HttpStatus> {
       log.debug("API DELETE /api/v1/files/{}/shares/{} userOwnerId={} fileId={} shareId={}", id, shareId, currentUserOwnerId(), id, shareId)
       fileService.deleteFileShare(id, shareId)
       log.info("File share deleted successfully: fileId={}, shareId={}", id, shareId)
       return ResponseEntity.ok(HttpStatus.OK)
   }

   //region Swagger Documentation
   @Operation(
       tags = ["File Sharing"],
       summary = "Update file share permission",
       description = "Updates the permission level (READ_ONLY or READ_WRITE) of an existing share. Only the file owner may change share permissions.",
       parameters = [
           Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
           Parameter(name = "shareId", description = "Share record id", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "integer", format = "int64")),
           Parameter(name = "permission", description = "New permission level", required = true, schema = Schema(type = "string", example = "READ_WRITE"))
       ]
   )
   @ApiResponse(
       responseCode = "200",
       description = "Permission updated. Returns the updated share record.",
       content = [Content(mediaType = "application/json", schema = Schema(implementation = FileShareDTO::class))]
   )
   @ApiResponse(
       responseCode = "403",
       description = "Forbidden - only the file owner can update shares.",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "NotOwner",
               summary = "Caller is not the file owner",
               value = """
               {
                 "type": "Forbidden",
                 "title": "Access denied",
                 "status": 403,
                 "detail": "Access denied: only file owner can update share for file {id}",
                 "instance": "/api/v1/files/{id}/shares/{shareId}"
               }
               """
           )]
       )]
   )
   @ApiResponse(
       responseCode = "404",
       description = "File or share not found, or the share does not belong to the file.",
       content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
           examples = [ExampleObject(
               name = "ShareNotFound",
               summary = "File or share missing",
               value = """
               {
                 "type": "Not Found",
                 "title": "Resource not found",
                 "status": 404,
                 "detail": "File share {shareId} does not belong to file {id}",
                 "instance": "/api/v1/files/{id}/shares/{shareId}"
               }
               """
           )]
       )]
   )
   //endregion
   @PatchMapping("/{id}/shares/{shareId}")
   fun updateFileShare(
       @PathVariable id: UUID,
       @PathVariable shareId: Long,
       @RequestParam permission: PERMISSION_LEVEL
   ): ResponseEntity<FileShareDTO> {
       val share = fileService.updateFileSharePermission(id, shareId, permission)
       return ResponseEntity.ok(share)
   }

    //region Swagger Documentation
    @Operation(
        tags = ["File Versioning"],
        summary = "Create file version",
        description = "Creates a new version of an existing file by redirecting to the TUS upload endpoint.",
        parameters = [
            Parameter(name = "fileId", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid"))
        ]
    )
    @ApiResponse(
        responseCode = "302",
        description = "Redirect to upload endpoint"
    )
    @ApiResponse(
        responseCode = "404",
        description = "File not found",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "FileNotFound",
                summary = "File to version does not exist",
                value = """
                {
                  "type": "Not Found",
                  "title": "File not found",
                  "status": 404,
                  "detail": "The requested file does not exist",
                  "instance": "/api/v1/files/{fileId}/versions"
                }
                """
            )]
        )]
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "InternalServerError",
                summary = "Unexpected server error",
                value = """
                {
                  "type": "Internal Server Error",
                  "title": "Server error",
                  "status": 500,
                  "detail": "An unexpected error occurred",
                  "instance": "/api/v1/files/{fileId}/versions"
                }
                """
            )]
        )]
    )
    //endregion
    @PostMapping("/{fileId}/versions")
    fun createFileVersion(
        @PathVariable fileId: UUID,
        req: HttpServletRequest,
        res: HttpServletResponse
    ) {
        log.debug("API POST /api/v1/files/{}/versions userOwnerId={} fileId={}", fileId, currentUserOwnerId(), fileId)

        fileService.existsFileOrThrow(fileId) // Throws 404 if file does not exist
        log.debug("File exists, redirecting to upload endpoint for file: {}", fileId)
        // 307 preserves the method, body and (same-origin) headers, so the tus client's
        // creation POST — with Upload-Length / Tus-Resumable / Upload-Metadata — is replayed
        // against the real upload endpoint. The path must be absolute-from-root and include
        // /api/v1 so it routes through the gateway to this service's TusController.
        res.status = HttpServletResponse.SC_TEMPORARY_REDIRECT // 307
        res.setHeader("Location", "/api/v1/uploads?fileId=${fileId}")
    }

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Get all versions of a file",
        description = "Retrieves all versions of a specific file with their metadata.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved file versions",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = FileVersionDTO::class)
        )]
    )
    @ApiResponse(
        responseCode = "404",
        description = "File not found",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    )
    //endregion
    @GetMapping("/{fileId}/versions")
    fun getFileVersions(@PathVariable fileId: UUID): ResponseEntity<List<FileVersionDTO>> {
        log.debug("API GET /api/v1/files/{}/versions userOwnerId={} fileId={}", fileId, currentUserOwnerId(), fileId)

        val versions = fileService.getFileVersions(fileId)
        log.debug("Retrieved {} versions for file: {}", versions.size, fileId)
        return ResponseEntity.ok(versions)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["Version Favorites"],
        summary = "Favorite a file version",
        description = "Marks a specific file version as favorite for the current user. Idempotent: favoriting an already-favorited version returns the existing record. Requires at least read access to the file.",
        parameters = [
            Parameter(name = "fileId", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
            Parameter(name = "version", description = "Version number", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "integer", format = "int64"))
        ]
    )
    @ApiResponse(
        responseCode = "201",
        description = "Version successfully favorited (or already favorited). Returns the favorite record.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = FileVersionFavoriteDTO::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "File or version not found",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    )
    //endregion
    @PostMapping("/{fileId}/versions/{version}/favorite")
    fun addVersionFavorite(
        @PathVariable fileId: UUID,
        @PathVariable version: Long
    ): ResponseEntity<FileVersionFavoriteDTO> {
        val favorite = fileService.addFavorite(fileId, version)
        return ResponseEntity.status(HttpStatus.CREATED).body(favorite)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["Version Favorites"],
        summary = "Unfavorite a file version",
        description = "Removes the current user's favorite on a specific file version. Idempotent: removing a non-favorited version succeeds.",
        parameters = [
            Parameter(name = "fileId", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
            Parameter(name = "version", description = "Version number", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "integer", format = "int64"))
        ]
    )
    @ApiResponse(responseCode = "204", description = "Favorite removed (or was not present)")
    @ApiResponse(
        responseCode = "404",
        description = "File or version not found",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    )
    //endregion
    @DeleteMapping("/{fileId}/versions/{version}/favorite")
    fun removeVersionFavorite(
        @PathVariable fileId: UUID,
        @PathVariable version: Long
    ): ResponseEntity<Void> {
        fileService.removeFavorite(fileId, version)
        return ResponseEntity.noContent().build()
    }

    //region Swagger Documentation
    @Operation(
        tags = ["Version Favorites"],
        summary = "List my favorite versions",
        description = "Retrieves the current user's favorited file versions, paginated. Soft-deleted versions and files the user can no longer access are hidden.",
        parameters = [
            Parameter(name = "page", description = "Page number (0-indexed)", schema = Schema(type = "integer", example = "0")),
            Parameter(name = "size", description = "Page size", schema = Schema(type = "integer", example = "10"))
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved favorites",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = Page::class))]
    )
    //endregion
    @GetMapping("/favorites")
    fun getMyFavorites(
        @RequestParam(defaultValue = "0") page: UInt,
        @RequestParam(defaultValue = "10") size: UInt
    ): ResponseEntity<Page<FileVersionFavoriteDTO>> {
        val favorites = fileService.getMyFavorites(PageRequest.of(page.toInt(), size.toInt()))
        return ResponseEntity.ok(favorites)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["Version Favorites"],
        summary = "Get favorited version numbers for a file",
        description = "Returns the version numbers of the given file favorited by the current user. Used to render favorite indicators in the version history.",
        parameters = [
            Parameter(name = "fileId", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid"))
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved favorited version numbers",
        content = [Content(mediaType = "application/json", schema = Schema(type = "array"))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "File not found",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    )
    //endregion
    @GetMapping("/{fileId}/favorites")
    fun getFavoritedVersionsForFile(@PathVariable fileId: UUID): ResponseEntity<List<Long>> {
        val versions = fileService.getFavoritedVersionsForFile(fileId)
        return ResponseEntity.ok(versions)
    }

    private companion object {
        /** Upper bound on a writable-ids filter request, to keep the candidate IN query bounded. */
        const val MAX_WRITABLE_CANDIDATES = 1000
    }

}

