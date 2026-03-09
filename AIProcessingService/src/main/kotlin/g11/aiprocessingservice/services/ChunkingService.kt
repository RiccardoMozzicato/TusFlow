package g11.aiprocessingservice.services

import g11.aiprocessingservice.conf.MyTokenTextSplitter
import g11.aiprocessingservice.dto.ChunkData
import org.springframework.ai.document.Document
import org.springframework.stereotype.Service

@Service
class ChunkingService(
    private val tokenTextSplitter: MyTokenTextSplitter
) {

    fun splitIntoChunks(
        text: String,
        fileVersionId: Long,
        baseMetadata: Map<String, String>
    ): List<ChunkData> {

        // TokenTextSplitter richiede un Document come input
        val document = Document(text, baseMetadata)
        val chunks = tokenTextSplitter.splitDocuments(document)
        val totalChunks = chunks.size

        return chunks.mapIndexed { index, chunk ->
            val chunkContent = chunk.text
            val chunkMetadata = chunk.metadata
            ChunkData(
                chunkIndex = index,
                content = chunkContent!!,
                metadata = (chunkMetadata + mapOf(
                    "fileVersionId" to fileVersionId.toString(),
                    "chunkIndex" to index.toString(),
                    "totalChunks" to totalChunks.toString(),
                    "chunkTokens" to estimateTokens(chunkContent).toString()
                )).mapValues { it.value.toString() }
            )
        }
    }

    private fun estimateTokens(text: String): Int {
        // Approssimazione: 1 token ≈ 4 caratteri
        return (text.length / 4.0).toInt()
    }
}
