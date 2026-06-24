package g11.aiprocessingservice.rag.catalog

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Reads a per-document catalog (filename + AI summary + tags) straight from the `vector_store`
 * metadata that [g11.aiprocessingservice.services.EmbeddingProcessor] stamps on every chunk.
 *
 * This is the "what documents do I have / summarize everything" data source: a catalog/aggregation
 * answer, not a similarity search. Building it locally from `vector_store` avoids N round-trips to
 * TusUploader (whose listing has no summary, and whose metadata endpoint is per-file only).
 *
 * Scope note: it only sees *indexed* documents (text-extractable ones that produced embeddings) —
 * i.e. exactly the documents the chatbot can actually reason about. Images / in-flight uploads that
 * never embedded won't appear.
 */
@Repository
class DocumentCatalogRepository(private val jdbc: NamedParameterJdbcTemplate) {

    /**
     * One row per accessible file. `DISTINCT ON (fileId)` collapses the many chunks of a document to
     * a single catalog entry; the filename/summary/tags are identical across a file's chunks, so any
     * chunk's copy is representative.
     *
     * @param fileIds the files the caller may access (owned + shared); empty short-circuits to empty.
     */
    fun findByFileIds(fileIds: Set<String>): List<DocumentCatalogEntry> {
        if (fileIds.isEmpty()) return emptyList()

        val sql = """
            SELECT DISTINCT ON (metadata->>'fileId')
                   metadata->>'filename' AS filename,
                   metadata->>'summary'  AS summary,
                   metadata->>'tags'     AS tags
            FROM vector_store
            WHERE metadata->>'fileId' IN (:fileIds)
            ORDER BY metadata->>'fileId', metadata->>'filename'
        """.trimIndent()

        val params = MapSqlParameterSource("fileIds", fileIds)
        return jdbc.query(sql, params) { rs, _ ->
            DocumentCatalogEntry(
                filename = rs.getString("filename"),
                summary = rs.getString("summary"),
                tags = rs.getString("tags"),
            )
        }
    }
}
