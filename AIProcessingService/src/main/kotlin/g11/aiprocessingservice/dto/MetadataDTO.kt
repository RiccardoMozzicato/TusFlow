package g11.aiprocessingservice.dto

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

enum class sensitivityLevel {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    HIGHLY_CONFIDENTIAL
}
data class MetadataDTO(
    @JsonSetter(nulls = Nulls.SKIP)
    val summary: String? = null,
    @JsonSetter(nulls = Nulls.SKIP)
    val tags: String? = null,
    @JsonSetter(nulls = Nulls.SKIP)
    val sensitivity: sensitivityLevel? = sensitivityLevel.PUBLIC

)
