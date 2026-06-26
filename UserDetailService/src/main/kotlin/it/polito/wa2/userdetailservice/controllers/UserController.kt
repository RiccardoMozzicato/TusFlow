package it.polito.wa2.userdetailservice.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import it.polito.wa2.userdetailservice.dto.AvatarDTO
import it.polito.wa2.userdetailservice.dto.FiltersDTO
import it.polito.wa2.userdetailservice.dto.UserCreationDTO
import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.exceptions.UserConflictError
import it.polito.wa2.userdetailservice.services.UserServiceInterface
import it.polito.wa2.userdetailservice.validators.UserUpdateValidation
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.multipart.MultipartFile


@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserServiceInterface
) {

    private val log = LoggerFactory.getLogger(UserController::class.java)

    // Resolves the authenticated user (owner) id from the JWT for request logging.
    // Never throws so an API log line can't break the request flow.
    private fun currentUserOwnerId(): String =
        runCatching { (SecurityContextHolder.getContext().authentication.principal as Jwt).subject }
            .getOrDefault("unknown")

    // Spring requires a strong ETag wrapped in double quotes; MinIO returns the
    // raw hash (sometimes already quoted), so normalize to a single quoted form.
    private fun normalizeETag(raw: String): String {
        val unquoted = raw.trim().removeSurrounding("\"")
        return "\"$unquoted\""
    }

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Get the list of users",
        description = "Retrieve a paginated list of users visible to the authenticated principal. Regular users, managers and admins are scoped to users in their own organization; the file-service account can list users across organizations. The result can be filtered by name/email and sorted by supported user fields.",
        parameters = [
            Parameter(
                name = "page",
                description = "Page number",
                required = false,
                example = "0",
                schema = Schema(type = "integer", defaultValue = "0", format = "int32")
            ),
            Parameter(
                name = "size",
                description = "Page size",
                required = false,
                example = "10",
                schema = Schema(type = "integer", defaultValue = "10", format = "int32")
            ),
            Parameter(
                name = "search",
                description = "Free-text query matched case-insensitively against the user's name and email",
                required = false,
                example = "rossi",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "sort",
                description = "Sort spec as 'field,direction'. Supported fields: fullName, email, department, role, storageUsed, createdAt.",
                required = false,
                example = "fullName,asc",
                schema = Schema(type = "string", defaultValue = "fullName,asc")
            )
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "OK",
        content = [
            Content(mediaType = "application/json", schema = Schema(implementation = Page::class))
        ]
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized"
    )
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden"
    )
    @ApiResponse(
        responseCode = "404",
        description = "Authenticated user not found in the local user store",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Not Found",
                            "status": 404,
                            "detail": "The user with id 123e4567-e89b-12d3-a456-426614174000 not found"
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
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Internal Server Error",
                            "status": 500,
                            "detail": "An unexpected error occurred"
                        }
                        """
                    )
                ]
            )
        ]
    )
    //endregion
    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    fun getUsers(
        @RequestParam(defaultValue = "0") page: UInt,
        @RequestParam(defaultValue = "10") size: UInt,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "fullName,asc") sort: String
    ): Page<UserDTO> {
        log.debug("API GET /api/v1/users userOwnerId={} page={} size={} search={} sort={}", currentUserOwnerId(), page, size, search, sort)
        // Parse sort parameter as "field,direction"; blank field falls back in the service.
        val sortParts = sort.split(",")
        val sortField = sortParts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val sortDirection = if (sortParts.getOrNull(1)?.lowercase() == "desc") Sort.Direction.DESC else Sort.Direction.ASC
        return userService.getUsers(FiltersDTO(page.toInt(), size.toInt(), search, sortField, sortDirection))
    }

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Get user details",
        description = "Retrieve details of a specific user by their UUID. This endpoint is restricted to admins and the file-service account.",
        parameters = [
            Parameter(
                name = "id",
                description = "UUID of the user",
                required = true,
                example = "123e4567-e89b-12d3-a456-426614174000",
                `in` = ParameterIn.PATH,
                schema = Schema(type = "string", format = "uuid")
            )
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "OK",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserDTO::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "id": "123e4567-e89b-12d3-a456-426614174000",
                            "fullName": "John Doe",
                            "email": "john.doe@gmail.com",
                            "organization": "Polytechnic University of Turin",
                            "department": "Engineering",
                            "role": "ROLE_staff",
                            "storageQuota": 1073741824,
                            "storageUsed": 0
                        }
                        """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Bad Request",
                            "status": 400,
                            "detail": "The provided id is not a UUID"
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
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Not Found",
                            "status": 404,
                            "detail": "The user with id 123e4567-e89b-12d3-a456-426614174000 not found"
                        }
                        """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized"
    )
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden"
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Internal Server Error",
                            "status": 500,
                            "detail": "An unexpected error occurred"
                        }
                        """
                    )
                ]
            )
        ]
    )
    //endregion
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_serviceaccount-fileservice')")
    fun getUser(@PathVariable id: UUID): UserDTO {
        log.debug("API GET /api/v1/users/{} userOwnerId={} userId={}", id, currentUserOwnerId(), id)
        return userService.getUser(id)
    }

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Get users by id batch",
        description = "Retrieve multiple users by UUID. This endpoint is restricted to admins and the file-service account and is intended for service-to-service lookups such as resolving file owners and sharing recipients.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of user UUIDs to retrieve",
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(type = "string", format = "uuid")),
                    examples = [
                        ExampleObject(
                            value = """
                            [
                              "123e4567-e89b-12d3-a456-426614174000",
                              "123e4567-e89b-12d3-a456-426614174001"
                            ]
                            """
                        )
                    ]
                )
            ]
        )
    )
    @ApiResponse(
        responseCode = "200",
        description = "OK",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = Map::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                          "123e4567-e89b-12d3-a456-426614174000": {
                            "id": "123e4567-e89b-12d3-a456-426614174000",
                            "fullName": "John Doe",
                            "email": "john.doe@gmail.com",
                            "organization": "Polytechnic University of Turin",
                            "department": "Engineering",
                            "role": "ROLE_staff",
                            "storageQuota": 1073741824,
                            "storageUsed": 0
                          }
                        }
                        """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request"
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized"
    )
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden"
    )
    //endregion
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_serviceaccount-fileservice')")
    fun getUsersBatch(@RequestBody ids: List<UUID>): Map<UUID, UserDTO> {
        log.debug("API POST /api/v1/users/batch userOwnerId={} userCount={} userIds={}", currentUserOwnerId(), ids.size, ids)
        return userService.getUsersBatch(ids)
    }

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Create a new user",
        description = "Create a new user with the provided details. This endpoint is restricted to admins.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "New user details. The user is created in Keycloak and then persisted in the UserDetailService database.",
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UserCreationDTO::class),
                    examples = [
                        ExampleObject(
                            value = """
                            {
                                "username": "John Doe",
                                "email": "john.doe@gmail.com",
                                "organization": "Polytechnic University of Turin",
                                "department": "Engineering",
                                "role": "ROLE_staff",
                                "storageQuota": 1073741824
                            }"""
                        )
                    ]
                )
            ]
        )
    )
    @ApiResponse(
        responseCode = "201",
        description = "Created"
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Bad Request",
                            "status": 400,
                            "detail": "Failed to read request",
                            "instance": "/api/v1/users"
                        }
                        """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized"
    )
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden"
    )
    @ApiResponse(
        responseCode = "500",
        description = "Internal Server Error, including failures returned by Keycloak during user creation",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Internal Server Error",
                            "status": 500,
                            "detail": "An unexpected error occurred"
                        }
                        """
                    )
                ]
            )
        ]
    )
    //endregion
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ROLE_admin')")
    fun postUser(@Valid @RequestBody userDTO: UserCreationDTO) {
        log.debug("API POST /api/v1/users userOwnerId={} user={}", currentUserOwnerId(), userDTO)
        userService.createUser(userDTO)
    }

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Update an existing user",
        description = "Update the details of an existing user identified by their UUID. This endpoint is restricted to admins.",
        parameters = [
            Parameter(
                name = "id",
                description = "UUID of the user to be updated",
                required = true,
                example = "123e4567-e89b-12d3-a456-426614174000",
                `in` = ParameterIn.PATH,
                schema = Schema(type = "string", format = "uuid")
            )
        ],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Updated user details",
            required = true,
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UserDTO::class),
                    examples = [
                        ExampleObject(
                            value = """
                            {
                                "id": "123e4567-e89b-12d3-a456-426614174000",
                                "fullName": "John Doe",
                                "email": "john.doe@gmail.com",
                                "organization": "Polytechnic University of Turin",
                                "department": "Engineering",
                                "role": "ROLE_staff",
                                "storageQuota": 1073741824,
                                "storageUsed": 0
                            }"""
                        )
                    ]
                )
            ]
        )
    )
    @ApiResponse(
        responseCode = "204",
        description = "No Content"
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Bad Request",
                            "status": 400,
                            "detail": "The provided id is not a UUID"
                        }
                        """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized"
    )
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden"
    )
    @ApiResponse(
        responseCode = "404",
        description = "Not Found",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Not Found",
                            "status": 404,
                            "detail": "User with id 123e4567-e89b-12d3-a456-426614174000 not found"
                        }
                        """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Conflict caused by an ID mismatch, duplicate email, or storage quota lower than the user's current usage",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Conflict Error",
                            "status": 409,
                            "detail": "Another user with that email already exists"
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
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Internal Server Error",
                            "status": 500,
                            "detail": "An unexpected error occurred"
                        }
                        """
                    )
                ]
            )
        ]
    )
    //endregion
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ROLE_admin')")
    fun updateUser(
        @PathVariable id: UUID,
        @RequestBody @UserUpdateValidation userDTO: UserDTO
    ) {
        log.debug("API PUT /api/v1/users/{} userOwnerId={} userId={}", id, currentUserOwnerId(), id)
        if(id != userDTO.id) {
            log.warn("User update rejected due to ID mismatch pathId={} bodyId={}", id, userDTO.id)
            throw UserConflictError("ID mismatch")
        }
        userService.updateUser(userDTO)
    }

    @PostMapping("/me/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    fun updateUserAvatar(@RequestParam file: MultipartFile, response: HttpServletResponse) {
        log.debug("API POST /api/v1/users/me/avatar userId={}", currentUserOwnerId())
        val dto = AvatarDTO(file.inputStream, file.size, file.contentType)
        userService.updateAvatar(currentUserOwnerId(), dto)
    }

    // Streams the avatar bytes from MinIO through the gateway so MinIO is never
    // reachable from the browser. Returns 404 (no body) when the user has no
    // avatar, letting the frontend fall back to initials.
    @GetMapping("/{id}/avatar")
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    fun getUserAvatar(@PathVariable id: UUID): ResponseEntity<InputStreamResource> {
        log.debug("API GET /api/v1/users/{}/avatar userId={}", id, currentUserOwnerId())
        val avatar = userService.getAvatarContent(id) ?: return ResponseEntity.notFound().build()

        val headers = HttpHeaders().apply {
            contentType = MediaType.parseMediaType(avatar.contentType)
            cacheControl = CacheControl.noCache().headerValue
            avatar.etag?.let { eTag = normalizeETag(it) }
        }
        return ResponseEntity.ok().headers(headers).body(InputStreamResource(avatar.stream))
    }

    @DeleteMapping("/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    fun deleteUserAvatar() {
        log.debug("API DELETE /api/v1/users/me/avatar userId={}", currentUserOwnerId())
        userService.deleteAvatar(currentUserOwnerId())
    }


    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Delete a user",
        description = "Delete an existing user identified by their UUID. This endpoint is restricted to admins and rejects attempts to delete the authenticated user's own account.",
        parameters = [
            Parameter(
                name = "id",
                description = "UUID of the user to be deleted",
                required = true,
                example = "123e4567-e89b-12d3-a456-426614174000",
                `in` = ParameterIn.PATH,
                schema = Schema(type = "string", format = "uuid")
            )
        ]
    )
    @ApiResponse(
        responseCode = "204",
        description = "No Content"
    )
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Bad Request",
                            "status": 400,
                            "detail": "The provided id is not a UUID"
                        }
                        """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized"
    )
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Forbidden",
                            "status": 403,
                            "detail": "Cannot delete your own user account"
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
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Not Found",
                            "status": 404,
                            "detail": "User with id 123e4567-e89b-12d3-a456-426614174000 not found"
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
                mediaType = "application/json",
                schema = Schema(implementation = ProblemDetail::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "type": "about:blank",
                            "title": "Internal Server Error",
                            "status": 500,
                            "detail": "An unexpected error occurred"
                        }
                        """
                    )
                ]
            )
        ]
    )
    //endregion
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ROLE_admin')")
    fun deleteUser(@PathVariable id: UUID) {
        log.debug("API DELETE /api/v1/users/{} userOwnerId={} userId={}", id, currentUserOwnerId(), id)
        userService.deleteUser(id)
    }

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Get current user",
        description = "Retrieve the profile of the authenticated user. The user id is read from the JWT subject."
    )
    @ApiResponse(
        responseCode = "200",
        description = "OK",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserDTO::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "id": "123e4567-e89b-12d3-a456-426614174000",
                            "fullName": "John Doe",
                            "email": "john.doe@gmail.com",
                            "organization": "Polytechnic University of Turin",
                            "department": "Engineering",
                            "role": "ROLE_staff",
                            "storageQuota": 1073741824,
                            "storageUsed": 0
                        }
                        """
                    )
                ]
            )
        ]
    )
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized"
    )
    @ApiResponse(
        responseCode = "404",
        description = "Not Found"
    )
    //endregion
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getCurrentUser(principal: Principal): UserDTO {
        log.debug("API GET /api/v1/users/me userOwnerId={}", currentUserOwnerId())

        val auth = principal as JwtAuthenticationToken
        val jwt = auth.token

        return userService.getUser(UUID.fromString(jwt.subject))
    }
}
