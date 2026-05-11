package g11.tusuploaderservice.dto

enum class AiMetadataField(val key: String) {
    SUMMARY("summary"),
    TAGS("tags"),
    SENSITIVITY("sensitivity")
}

enum class SensitivityLevel {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    HIGHLY_CONFIDENTIAL
}

