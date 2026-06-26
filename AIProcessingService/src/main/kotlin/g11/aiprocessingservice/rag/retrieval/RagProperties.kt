package g11.aiprocessingservice.rag.retrieval

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * RAG configuration bound from `app.rag` in application.yml.
 */
@ConfigurationProperties(prefix = "app.rag")
data class RagProperties(
    val retrieval: Retrieval = Retrieval(),
    val memory: Memory = Memory(),
    val streaming: Streaming = Streaming(),
    val scoring: Scoring = Scoring(),
) {
    /**
     * @property topK default number of chunks the content-search tool retrieves when the request
     *   omits topK.
     * @property similarityThreshold minimum cosine similarity in [0,1]; weaker matches are dropped.
     *   Defaults to 0.0 (off) so retrieval does not regress before the score distribution of the
     *   active embedding model is known — raise it (e.g. 0.5) once Langfuse traces show typical scores.
     *
     * Query reformulation (rewrite/compression/expansion) is no longer configured here: in the
     * agentic flow the model itself phrases the search query when it calls the content-search tool.
     */
    data class Retrieval(
        val topK: Int = 5,
        val similarityThreshold: Double = 0.0,
    )

    /**
     * @property maxMessages conversation window kept in chat memory (older turns are dropped).
     */
    data class Memory(
        val maxMessages: Int = 20,
    )

    /**
     * @property enabled whether the streaming endpoint (POST /api/rag/query/stream) is served.
     */
    data class Streaming(
        val enabled: Boolean = true,
    )

    /**
     * Langfuse evaluation scores attached to RAG query traces (off by default; all best-effort).
     *
     * @property enabled master switch for posting any score.
     * @property relevance post a cheap, deterministic relevance score (avg source similarity).
     * @property groundedness post an LLM-judged groundedness score (adds one LLM call per query).
     * @property langfuseBaseUrl Langfuse host for the scores REST API (POST {base}/api/public/scores).
     * @property langfuseAuthHeader the same "Basic base64(public:secret)" used for OTLP ingestion.
     */
    data class Scoring(
        val enabled: Boolean = false,
        val relevance: Boolean = true,
        val groundedness: Boolean = false,
        val langfuseBaseUrl: String = "https://cloud.langfuse.com",
        val langfuseAuthHeader: String = "",
    )
}
