package it.polito.wa2.userdetailservice.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import it.polito.wa2.userdetailservice.dto.FiltersDTO
import it.polito.wa2.userdetailservice.dto.UserCreationDTO
import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.exceptions.UserConflictError
import it.polito.wa2.userdetailservice.services.UserServiceInterface
import it.polito.wa2.userdetailservice.validators.UserUpdateValidation
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken


@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserServiceInterface
) {

    private val log = LoggerFactory.getLogger(UserController::class.java)

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Get the list of users",
        description = "Retrieve a paginated list of users.",
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
        responseCode = "500",
        description = "Internal Server Error",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Internal Server Error",
                            "message": "An unexpected error occurred"
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
        @RequestParam(defaultValue = "10") size: UInt
    ): Page<UserDTO> {
        return userService.getUsers(FiltersDTO(page.toInt(), size.toInt()))
    }

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Get user details",
        description = "Retrieve details of a specific user by their UUID.",
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
                            "role": "VIEWER",
                            "storageQuota": 1073741824
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
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Bad Request",
                            "message": "The provided id is not a UUID"
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
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Not Found",
                            "message": "The user with id 123e4567-e89b-12d3-a456-426614174000 not found"
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
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Internal Server Error",
                            "message": "An unexpected error occurred"
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
        log.debug("Retrieving user with UUID {}", id)
        return userService.getUser(id)
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_serviceaccount-fileservice')")
    fun getUsersBatch(@RequestBody ids: List<UUID>): Map<UUID, UserDTO> {
        log.debug("Retrieving batch of {} users", ids.size)
        return userService.getUsersBatch(ids)
    }

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Create a new user",
        description = "Create a new user with the provided details.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User details",
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
                                "role": "VIEWER",
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
        responseCode = "409",
        description = "Conflict",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Conflict Error",
                            "message": "This user already exists"
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
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Internal Server Error",
                            "message": "An unexpected error occurred"
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
        log.debug("Creating user {}", userDTO)
        userService.createUser(userDTO)
    }

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Update an existing user",
        description = "Update the details of an existing user identified by their UUID.",
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
                                "role": "VIEWER",
                                "storageQuota": 1073741824
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
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Bad Request",
                            "message": "The provided id is not a UUID"
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
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Not Found",
                            "message": "User with id 123e4567-e89b-12d3-a456-426614174000 not found"
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
                mediaType = "application/json",
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Conflict Error",
                            "message": "Another user with that email already exists"
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
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Internal Server Error",
                            "message": "An unexpected error occurred"
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
        if(id != userDTO.id) throw UserConflictError("ID mismatch")
        userService.updateUser(userDTO)
    }

    //region Swagger Documentation
    @Operation(
        tags = [ "User Handling" ],
        summary = "Delete a user",
        description = "Delete an existing user identified by their UUID.",
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
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Bad Request",
                            "message": "The provided id is not a UUID"
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
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Not Found",
                            "message": "User with id 123e4567-e89b-12d3-a456-426614174000 not found"
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
                schema = Schema(implementation = ErrorDetails::class),
                examples = [
                    ExampleObject(
                        value = """
                        {
                            "timestamp": "2024-01-01T12:00:00Z",
                            "error": "Internal Server Error",
                            "message": "An unexpected error occurred"
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
        userService.deleteUser(id)
    }

    //region Swagger Documentation
    //endregion
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    fun getCurrentUser(principal: Principal): UserDTO {
        val auth = principal as JwtAuthenticationToken
        val jwt = auth.token

        return userService.getUser(UUID.fromString(jwt.subject))
    }


}