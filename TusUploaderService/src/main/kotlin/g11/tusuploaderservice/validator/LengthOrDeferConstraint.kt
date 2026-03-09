package g11.tusuploaderservice.validator

import g11.tusuploaderservice.dto.UploadDTO
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@MustBeDocumented
@Constraint(validatedBy = [LengthOrDeferValidator::class])
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LengthOrDeferConstraint(
    val message: String = "uploadLength and uploadDeferLength cannot be set together",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class LengthOrDeferValidator : ConstraintValidator<LengthOrDeferConstraint, UploadDTO> {
    override fun isValid(value: UploadDTO?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val hasLength = value.uploadLength != null
        val isDeferred = value.uploadDeferLength == 1
        if (hasLength && isDeferred) {
            return false
        }
        return true
    }
}

