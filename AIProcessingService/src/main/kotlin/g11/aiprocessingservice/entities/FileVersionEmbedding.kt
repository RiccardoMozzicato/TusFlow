package g11.aiprocessingservice.entities

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "file_version_embeddings",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_file_version_chunk", columnNames = ["file_version_id", "chunk_index"])
    ]
)
data class FileVersionEmbedding(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "file_version_id", nullable = false)
    val fileVersionId: Long,  // ID numerico della versione

    @Column(name = "chunk_index", nullable = false)
    val chunkIndex: Int,

    @Column(name = "vector_store_id")
    val vectorStoreId: UUID? = null,

    @Column(name = "content_preview", columnDefinition = "TEXT")
    val contentPreview: String? = null,

    @Column(name = "chunk_tokens")
    val chunkTokens: Int? = null,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)

