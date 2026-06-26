package g11.aiprocessingservice.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/** A (fileId, version) pair identifying the AI job to look up in a batch request. */
@Schema(description = "File/version key used to look up the latest AI job status.")
data class JobKeyDTO(
    @Schema(description = "File id.", example = "1bbba75b-35bb-4e88-9200-df55387c1570")
    val fileId: UUID,
    @Schema(description = "File version.", example = "3")
    val version: Long,
)
