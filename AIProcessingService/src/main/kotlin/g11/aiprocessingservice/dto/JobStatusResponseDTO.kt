package g11.aiprocessingservice.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "Latest AI job status for a file/version pair.")
data class JobStatusResponseDTO(
    @JsonProperty("jobId")
    @Schema(description = "AI job id.", example = "7de8c224-a9e8-4f40-a882-46e48f08bfc5")
    val jobId: UUID,
    
    @JsonProperty("fileId")
    @Schema(description = "File id requested by the client.", example = "1bbba75b-35bb-4e88-9200-df55387c1570")
    val fileId: UUID,
    
    @JsonProperty("version")
    @Schema(description = "File version requested by the client.", example = "3")
    val version: Long,
    
    @JsonProperty("status")
    @Schema(description = "Current job lifecycle status.")
    val status: jobStatus,
    
    @JsonProperty("payload")
    @Schema(
        description = "Job payload, including AI metadata on success or error details on failure.",
        example = """{"filename":"project-report.pdf","contentType":"application/pdf","summary":"Quarterly project report","tags":"project,quarterly","sensitivity":"INTERNAL","analysisStatus":"COMPLETED"}"""
    )
    val payload: Map<String, Any>? = null
)
