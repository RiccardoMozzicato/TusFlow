package g11.aiprocessingservice.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Spring Data page wrapper returned by the failed-jobs endpoint.")
data class FailedJobsPageDTO(
    @Schema(description = "Failed jobs on the requested page.")
    val content: List<FailedJobDTO>,
    @Schema(description = "Spring Data pageable metadata.")
    val pageable: Map<String, Any>? = null,
    @Schema(description = "Spring Data sort metadata.")
    val sort: Map<String, Any>? = null,
    @Schema(description = "Total number of failed jobs matching the caller scope.", example = "12")
    val totalElements: Long,
    @Schema(description = "Total number of pages.", example = "2")
    val totalPages: Int,
    @Schema(description = "Current page index, zero-based.", example = "0")
    val number: Int,
    @Schema(description = "Requested page size.", example = "100")
    val size: Int,
    @Schema(description = "Number of elements in this page.", example = "100")
    val numberOfElements: Int,
    @Schema(description = "Whether this is the first page.", example = "true")
    val first: Boolean,
    @Schema(description = "Whether this is the last page.", example = "false")
    val last: Boolean,
    @Schema(description = "Whether this page contains no jobs.", example = "false")
    val empty: Boolean,
)
