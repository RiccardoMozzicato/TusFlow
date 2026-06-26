package g11.aiprocessingservice.rag.catalog

/**
 * One document in the user's catalog: the AI-generated summary and tags captured at upload time.
 *
 * This is the model-facing payload of the `listAvailableDocuments` tool, so it is intentionally lean
 * (no ids) — the model uses [filename]/[summary]/[tags] to answer "what do I have / give me an
 * overview" questions without a content search.
 */
data class DocumentCatalogEntry(
    val filename: String?,
    val summary: String?,
    val tags: String?,
)
