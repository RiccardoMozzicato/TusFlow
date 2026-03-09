package it.polito.wa2.userdetailservice.controllers

import it.polito.wa2.userdetailservice.exceptions.SelfDeletionNotAllowedException
import it.polito.wa2.userdetailservice.exceptions.UserConflictError
import it.polito.wa2.userdetailservice.exceptions.UserNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.util.Date

data class ErrorDetails(
    val timestamp: Date,
    val error: String,
    val message: String
)


@RestControllerAdvice
class GlobalExceptionHandler: ResponseEntityExceptionHandler() {

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(exception: UserNotFoundException): ProblemDetail {
        val problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        problem.title = "Not Found"
        problem.detail = exception.message
        return problem
    }

    @ExceptionHandler(UserConflictError::class)
    fun handleUserAlreadyExists(exception: UserConflictError): ProblemDetail {
        val problem = ProblemDetail.forStatus(HttpStatus.CONFLICT)
        problem.title = "Conflict Error"
        problem.detail = exception.message
        return problem
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleNotUuid(exception: MethodArgumentTypeMismatchException): ProblemDetail {
        val problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)
        problem.title = "Bad Request"
        problem.detail = "The provided id is not a UUID"
        return problem
    }

    @ExceptionHandler(SelfDeletionNotAllowedException::class)
    fun handleSelfDeletionNotAllowed(exception: SelfDeletionNotAllowedException): ProblemDetail {
        val problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN)
        problem.title = "Forbidden"
        problem.detail = exception.message
        return problem
    }

}