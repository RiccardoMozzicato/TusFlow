package g11.aiprocessingservice.dto

import java.util.UUID

// FileAnalyzedEvent.kt
data class FileAnalyzedEvent(
    val jobId: UUID,
    val fileId: UUID,
    val version: Long,
    val fileVersionId: Long,  // ID numerico della versione
    val filename: String,
    val contentType: String,
    val ownerUserId: UUID,

    // Metadata già estratti da AI
    val summary: String?,
    val tags: List<String>?,
    val sensitivity: String?
)

// ChunkBatchEvent.kt
data class ChunkBatchEvent(
    val fileVersionId: Long,  // ID numerico della versione
    val batchNumber: Int,
    val totalBatches: Int,
    val chunks: List<ChunkData>
)

data class ChunkData(
    val chunkIndex: Int,
    val content: String,
    val metadata: Map<String, String>
)
