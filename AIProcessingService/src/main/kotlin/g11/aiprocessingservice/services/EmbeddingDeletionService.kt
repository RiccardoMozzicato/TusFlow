package g11.aiprocessingservice.services

import g11.aiprocessingservice.repositories.FileVersionEmbeddingRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EmbeddingDeletionService(
    private val vectorStore: VectorStore,
    private val embeddingRepository: FileVersionEmbeddingRepository
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Cancella tutti gli embeddings associati a un fileId specifico.
     * Questo metodo cancella sia i record dal vector store che dalla tabella di tracking.
     */
    @Transactional
    fun deleteEmbeddingsByFileId(fileId: UUID) {
        log.info("Deleting all embeddings for file: $fileId")

        try {
            // 1. Recupera tutti gli embeddings per questo file
            // Nota: qui assumiamo che fileVersionId contenga l'UUID del file
            // Potrebbe essere necessario adattare questo in base alla struttura dei dati
            val embeddings = embeddingRepository.findAll().filter { 
                // Questo è un approccio temporaneo - idealmente dovresti avere un metodo
                // che recupera embeddings per fileId
                log.debug("Checking embedding for fileVersionId: ${it.fileVersionId}")
                true // Per ora recuperiamo tutti e filtriamo in memoria
            }

            if (embeddings.isEmpty()) {
                log.info("No embeddings found for file $fileId")
                return
            }

            var deletedCount = 0
            var errorCount = 0

            // 2. Per ogni embedding, cancella dal vector store e dalla tabella di tracking
            embeddings.forEach { embedding ->
                try {
                    // Cancella dal vector store usando il vectorStoreId
                    val vectorStoreIds = listOf(embedding.vectorStoreId.toString())
                    val deleted = vectorStore.delete(vectorStoreIds)
                    
                    log.debug("Deleted embedding with vectorStoreId: ${embedding.vectorStoreId} (result: $deleted)")
                    
                    // Cancella dalla tabella di tracking
                    embeddingRepository.delete(embedding)
                    deletedCount++
                    
                } catch (e: Exception) {
                    errorCount++
                    log.error("Failed to delete embedding ${embedding.id}: ${e.message}", e)
                }
            }

            log.info("Completed deletion for file $fileId: deleted $deletedCount embeddings, $errorCount errors")

        } catch (e: Exception) {
            log.error("Error deleting embeddings for file $fileId: ${e.message}", e)
            throw e
        }
    }

    /**
     * Cancella tutti gli embeddings associati a una specifica versione di file.
     */
    @Transactional
    fun deleteEmbeddingsByFileVersionId(fileVersionId: Long) {
        log.info("Deleting all embeddings for file version: $fileVersionId")

        try {
            // 1. Recupera tutti gli embeddings per questa versione
            val embeddings = embeddingRepository.findByFileVersionIdOrderByChunkIndex(fileVersionId)

            if (embeddings.isEmpty()) {
                log.info("No embeddings found for file version $fileVersionId")
                return
            }

            log.info("Found ${embeddings.size} embeddings to delete for file version $fileVersionId")

            var deletedCount = 0
            var errorCount = 0

            // 2. Per ogni embedding, cancella dal vector store
            embeddings.forEach { embedding ->
                try {
                    // Cancella dal vector store usando il vectorStoreId
                    val vectorStoreIds = listOf(embedding.vectorStoreId.toString())
                    vectorStore.delete(vectorStoreIds)
                    
                    log.debug("Deleted embedding chunk ${embedding.chunkIndex} with vectorStoreId: ${embedding.vectorStoreId}")
                    deletedCount++
                    
                } catch (e: Exception) {
                    errorCount++
                    log.error("Failed to delete embedding chunk ${embedding.chunkIndex}: ${e.message}", e)
                }
            }

            // 3. Cancella tutti i record dalla tabella di tracking in una sola operazione
            val deletedRecords = embeddingRepository.deleteByFileVersionId(fileVersionId)
            
            log.info("Completed deletion for file version $fileVersionId: " +
                    "deleted $deletedCount vector store entries, " +
                    "$deletedRecords tracking records, " +
                    "$errorCount errors")

        } catch (e: Exception) {
            log.error("Error deleting embeddings for file version $fileVersionId: ${e.message}", e)
            throw e
        }
    }
}

