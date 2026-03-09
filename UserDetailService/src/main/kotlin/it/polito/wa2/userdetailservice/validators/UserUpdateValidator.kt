package it.polito.wa2.userdetailservice.validators

import it.polito.wa2.userdetailservice.dto.UserDTO
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

@Constraint(validatedBy = [UserUpdateValidator::class])
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class UserUpdateValidation(
    val message: String = "User Update Validation Failed",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class UserUpdateValidator: ConstraintValidator<UserUpdateValidation, UserDTO> {
    override fun isValid(value: UserDTO?, context: ConstraintValidatorContext): Boolean {
        if (value == null) {
            return false
        }

        val nullableAllowed = listOf("department", "storageQuota")

        for(property in value::class.memberProperties) {
            if(property.name !in nullableAllowed && property.call(value) == null){
                return false
            }
        }

        return true
    }
}