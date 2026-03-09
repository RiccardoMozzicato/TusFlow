package g11.tusuploaderservice.dto

import java.time.LocalDateTime

data class ErrorDTO(
    val dateTime: LocalDateTime,
    val message: String,
    val details: String
)