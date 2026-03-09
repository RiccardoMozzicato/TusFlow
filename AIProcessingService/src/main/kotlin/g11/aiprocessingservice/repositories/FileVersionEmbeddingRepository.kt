package g11.aiprocessingservice.repositories

import g11.aiprocessingservice.entities.FileVersionEmbedding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FileVersionEmbeddingRepository : JpaRepository<FileVersionEmbedding, UUID> {

    fun findByFileVersionIdAndChunkIndex(fileVersionId: Long, chunkIndex: Int): FileVersionEmbedding?

    fun findByFileVersionIdOrderByChunkIndex(fileVersionId: Long): List<FileVersionEmbedding>

    fun deleteByFileVersionId(fileVersionId: Long): Int

    fun countByFileVersionId(fileVersionId: Long): Int

    fun existsByFileVersionId(fileVersionId: Long): Boolean
}

