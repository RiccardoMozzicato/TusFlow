package g11.aiprocessingservice.rag.eval

import g11.aiprocessingservice.langfuse.LangfuseScoreClient
import g11.aiprocessingservice.rag.dto.DocumentSource
import g11.aiprocessingservice.rag.retrieval.RagProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Attaches Langfuse evaluation scores to a completed RAG query trace. Runs async and is fully gated
 * by `app.rag.scoring.*`, so it never affects the user response. Scores link to the trace by the OTel
 * trace id captured while the query span was active.
 */
@Service
class RagScoringService(
    private val scoreClient: LangfuseScoreClient,
    private val groundednessEvaluator: GroundednessEvaluator,
    private val props: RagProperties,
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @Async
    fun scoreAsync(traceId: String?, answer: String, sources: List<DocumentSource>) {
        if (!props.scoring.enabled || traceId.isNullOrBlank()) return

        // Relevance: cheap, deterministic — the mean similarity of the retrieved chunks.
        if (props.scoring.relevance && sources.isNotEmpty()) {
            val relevance = sources.map { it.similarity }.average()
            scoreClient.postNumericScore(traceId, "relevance", relevance, "mean source similarity")
        }

        // Groundedness: LLM-judged, opt-in (extra LLM call).
        if (props.scoring.groundedness && sources.isNotEmpty()) {
            val context = sources.joinToString("\n\n---\n\n") { it.content }
            groundednessEvaluator.evaluate(context, answer)?.let { score ->
                scoreClient.postNumericScore(traceId, "groundedness", score, "LLM-as-judge")
            }
        }

        log.debug("Scored RAG trace {}", traceId)
    }
}
