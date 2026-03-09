package g11.tusuploaderservice.validator

import g11.tusuploaderservice.exceptions.TusContentTypeException
import g11.tusuploaderservice.exceptions.TusResumableException
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.springframework.http.HttpStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.server.ResponseStatusException
import kotlin.reflect.KClass

@Constraint(validatedBy = [TusResumableValidator::class])
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class TusResumableValidation(
    val message: String = "Precondition Failed",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class TusResumableValidator(
    @Value("\${tus.version}") private val supportedVersion: String
) : ConstraintValidator<TusResumableValidation, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) {
            throw TusResumableException(
                supportedVersion,
                "Precondition Failed - Missing Tus-Resumable header"
            )
        }

        if (value != supportedVersion) {
            throw TusResumableException(
                supportedVersion,
                "Precondition Failed - Unsupported Tus-Resumable version: $value"
            )
        }

        return true
    }
}

@Constraint(validatedBy = [TusContentTypeValidator::class])
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class TusContentTypeValidation(
    val message: String = "Precondition Failed",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)


class TusContentTypeValidator() : ConstraintValidator<TusContentTypeValidation, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        // Extract the media type without parameters (e.g., remove ";charset=UTF-8")
        val mediaType = value?.split(";")?.firstOrNull()?.trim()
        
        if (mediaType != "application/offset+octet-stream") {
            throw TusContentTypeException(
                "Unsupported Media Type - Unsupported Content-Type: $value"
            )
        }

        return true
    }
}
