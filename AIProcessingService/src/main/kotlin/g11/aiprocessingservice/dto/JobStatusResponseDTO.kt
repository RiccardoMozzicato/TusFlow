package g11.aiprocessingservice.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class JobStatusResponseDTO(
    @JsonProperty("jobId")
    val jobId: UUID,
    
    @JsonProperty("fileId")
    val fileId: UUID,
    
    @JsonProperty("version")
    val version: Long,
    
    @JsonProperty("status")
    val status: jobStatus,
    
    @JsonProperty("payload")
    val payload: Map<String, Any>? = null
)
