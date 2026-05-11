package g11.tusuploaderservice.controllers

import g11.tusuploaderservice.dto.DownloadDTO
import g11.tusuploaderservice.dto.FileDTO
import g11.tusuploaderservice.dto.FileShareDTO
import g11.tusuploaderservice.dto.FileVersionDTO
import g11.tusuploaderservice.dto.FiltersDTO
import g11.tusuploaderservice.dto.MetadataAIDTO
import g11.tusuploaderservice.dto.RagQueryRequest
import g11.tusuploaderservice.dto.RagQueryResponse
import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.entities.PERMISSION_LEVEL
import g11.tusuploaderservice.entities.FileStatus
import g11.tusuploaderservice.exceptions.ResourceNotFoundException
import g11.tusuploaderservice.exceptions.ResourceUncompletedException
import g11.tusuploaderservice.services.FileService
import g11.tusuploaderservice.services.FileServiceInterface
import g11.tusuploaderservice.services.MetadataSSEService
import g11.tusuploaderservice.services.StorageService
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
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.InputStreamResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
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
    private val metadataSSEService: MetadataSSEService,
    @Qualifier("aiServiceClient") private val aiServiceClient: RestClient
) {
    private val log = LoggerFactory.getLogger(FileController::class.java)

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Get all files",
        description = "Retrieves all files present in the repository without pagination or filtering.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved all files",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = FileDTO::class)
        )]
    )
    @ApiResponse(
        responseCode = "404",
        description = "No files found in the repository",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    )
    //endregion
    @GetMapping("/all")
    fun getAllFiles(): ResponseEntity<List<FileDTO>> {
        log.debug("Getting all files from repository")
        val files = fileService.getAllFiles()
        return ResponseEntity.ok(files)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Get list of files metadata",
        description = "Retrieves a paginated list of files with optional filtering by status, content type, and date range.",
        parameters = [
            Parameter(name = "contentType", description = "Filter by content type", schema = Schema(type = "string", example = "application/pdf")),
            Parameter(name = "dateFrom", description = "Filter files created from this date (ISO format)", schema = Schema(type = "string", example = "2024-01-01T00:00:00Z")),
            Parameter(name = "dateTo", description = "Filter files created until this date (ISO format)", schema = Schema(type = "string", example = "2024-12-31T23:59:59Z")),
            Parameter(name = "sort", description = "Sort field and direction (e.g., 'createdAt,desc')", schema = Schema(type = "string", example = "createdAt,desc")),
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
        responseCode = "404",
        description = "No files found matching the specified criteria",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class),
            examples = [ExampleObject(
                name = "FilesNotFound",
                summary = "No results for given filters",
                value = """
                {
                  "type": "Not Found",
                  "title": "No files found",
                  "status": 404,
                  "detail": "No files match the provided filters",
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
    fun getFilesMetadata(
        @RequestParam(required = false) contentType: String?,
        @RequestParam(required = false) dateFrom: String?,
        @RequestParam(required = false) dateTo: String?,
        @RequestParam(defaultValue = "createdAt,desc") sort: String,
        @RequestParam(defaultValue = "0") page: UInt,
        @RequestParam(defaultValue = "10") size: UInt,
        @RequestParam(defaultValue = "PERSONAL") sharingScope: SharingScope,
    ): ResponseEntity<Page<FileDTO>> {

        log.debug("getFilesMetadata")
        // Parse sort parameter
        val sortParts = sort.split(",")
        val sortField = sortParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "createdAt"
        val sortDirection = if (sortParts.getOrNull(1)?.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC

        val filtersDTO = FiltersDTO(contentType, dateFrom, dateTo, sortField, sortDirection, page, size, sharingScope)

        log.debug(filtersDTO.sharingScope.toString())
        val files = if(filtersDTO.sharingScope == SharingScope.PERSONAL) {
            log.debug("Getting PERSONAL files")
            fileService.getPersonalFiles(filtersDTO)
        } else {
            log.debug("Getting organization or department files")
            fileService.getOrganizationDepartmentFiles(filtersDTO)
        }

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
        val fileDTO = fileService.getFileMetadata(id)
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
        log.debug("Fetching AI-generated metadata for file: {} version: {}", id, version)
        val metadata = fileService.getFileGeneratedMetadata(id, version)
        return ResponseEntity.ok(metadata)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["File Management"],
        summary = "Stream AI metadata processing status via Server-Sent Events",
        description = "Opens a Server-Sent Events connection that streams the status of AI metadata processing. Returns metadata when processing is complete.",
        parameters = [
            Parameter(name = "id", description = "File UUID", required = true, `in` = ParameterIn.PATH, schema = Schema(type = "string", format = "uuid")),
            Parameter(name = "version", description = "File version number (optional)", required = false, `in` = ParameterIn.QUERY, schema = Schema(type = "integer", format = "int64"))
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "SSE stream opened successfully",
        content = [Content(mediaType = "text/event-stream")]
    )
    @ApiResponse(
        responseCode = "404",
        description = "File not found",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    )
    //endregion
    @GetMapping("/generatedmetadata/stream/{id}")
    fun streamMetadataProcessingStatus(
        @PathVariable id: UUID,
        @RequestParam(required = false) version: Long?
    ): ResponseEntity<SseEmitter> {
        log.debug("Opening SSE stream for AI metadata processing - fileId: {} version: {}", id, version)

        val emitter = metadataSSEService.streamMetadataProcessingStatus(id, version)

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .header("Cache-Control", "no-cache")
            .header("X-Accel-Buffering", "no")
            .body(emitter)
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

        if (metadata.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Metadata cannot be empty"
            )
        }

        metadata.keys.forEach { key ->
            if (!key.matches(Regex("\\A\\p{ASCII}+\\z"))) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Metadata key '$key' must be ASCII encoded"
                )
            }
        }

        fileService.updateGeneratedMetadataByUser(id, version, metadata)
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

        // Check if metadata is empty
        if (metadata.isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Metadata cannot be empty"
            )
        }

        // Validate metadata keys are ASCII
        metadata.keys.forEach { key ->
            if (!key.matches(Regex("\\A\\p{ASCII}+\\z"))) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Metadata key '$key' must be ASCII encoded"
                )
            }
        }
        fileService.updateFileMetadataByUser(id,version, metadata)
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
        fileService.deleteFile(id)
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
        val download: DownloadDTO = fileService.downloadFile(id, version)
        res.setHeader("Content-Disposition", "attachment; filename=\"${download.filename}\"")
        res.setHeader("Content-Type", download.contentType)
        res.outputStream.use { output -> output.write(download.file) }
    }

    @GetMapping("/{id}/preview")
    fun previewFile(
        @PathVariable id: UUID,
        @RequestParam("version") version: Long? = null,
        res: HttpServletResponse
    ) {
        val preview: DownloadDTO = fileService.downloadFile(id, version)
        res.setHeader("Content-Disposition", "inline; filename=\"${preview.filename}\"")
        res.setHeader("Content-Type", preview.contentType)
        res.outputStream.use { output -> output.write(preview.file) }
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
       val shares = fileService.getFileShares(id, PageRequest.of(page.toInt(), size.toInt()))
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
           Parameter(name = "size", description = "Page size", schema = Schema(type = "integer", example = "10"))
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
       @RequestParam(defaultValue = "0") page: UInt,
       @RequestParam(defaultValue = "10") size: UInt
   ): ResponseEntity<Page<FileDTO>> {
       val sharedFiles = fileService.getFilesSharedWithCurrentUser(PageRequest.of(page.toInt(), size.toInt()))
       return ResponseEntity.ok(sharedFiles)
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
       fileService.deleteFileShare(id, shareId)
       return ResponseEntity.ok(HttpStatus.OK)
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
        fileService.existsFileOrThrow(fileId) // Throws 404 if file does not exist
        res.status = HttpServletResponse.SC_TEMPORARY_REDIRECT // 307
        res.setHeader("Location", "/uploads?fileId=${fileId}")
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
        log.debug("Getting all versions for file: $fileId")
        val versions = fileService.getFileVersions(fileId)
        return ResponseEntity.ok(versions)
    }

    //region Swagger Documentation
    @Operation(
        tags = ["AI RAG"],
        summary = "Query files with RAG",
        description = "Performs semantic search on file contents using RAG (Retrieval-Augmented Generation) powered by AI."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Successfully retrieved RAG response",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = RagQueryResponse::class))]
    )
    @ApiResponse(
        responseCode = "500",
        description = "AI Service Error",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
    )
    //endregion

    @PostMapping("/query")
    fun queryFiles(@RequestBody request: RagQueryRequest, principal: Authentication): ResponseEntity<RagQueryResponse> {
        // Estrai userId dal JWT token
        val userId = when (principal) {
            is JwtAuthenticationToken -> principal.token.subject
            else -> throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication")
        }

        log.info("Forwarding RAG query to AI Service: {} for user: {}", request.query, userId)

        try {
            // Aggiungi userId ai filtri
            val filters = request.filters.toMutableMap()
            filters["ownerId"] = userId

            val requestWithFilters = request.copy(filters = filters)

            val response = aiServiceClient.post()
                .uri("/api/rag/query")
                .body(requestWithFilters)
                .retrieve()
                .body(RagQueryResponse::class.java)

            return ResponseEntity.ok(response)

        } catch (e: Exception) {
            log.error("Failed to query AI Service: ${e.message}", e)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to contact AI Service: ${e.message}"
            )
        }
    }

}

