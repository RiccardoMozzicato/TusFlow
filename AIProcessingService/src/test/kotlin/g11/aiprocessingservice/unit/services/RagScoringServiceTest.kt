package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.langfuse.LangfuseScoreClient
import g11.aiprocessingservice.rag.dto.DocumentSource
import g11.aiprocessingservice.rag.eval.GroundednessEvaluator
import g11.aiprocessingservice.rag.eval.RagScoringService
import g11.aiprocessingservice.rag.retrieval.RagProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class RagScoringServiceTest {

    private val scoreClient = mockk<LangfuseScoreClient>(relaxed = true)
    private val groundednessEvaluator = mockk<GroundednessEvaluator>()

    private fun service(scoring: RagProperties.Scoring) =
        RagScoringService(scoreClient, groundednessEvaluator, RagProperties(scoring = scoring))

    private fun source(similarity: Double) =
        DocumentSource(content = "c", contentPreview = "c", metadata = emptyMap(), similarity = similarity)

    @Test
    fun `does nothing when scoring is disabled`() {
        service(RagProperties.Scoring(enabled = false)).scoreAsync("t1", "answer", listOf(source(0.9)))
        verify(exactly = 0) { scoreClient.postNumericScore(any(), any(), any(), any()) }
    }

    @Test
    fun `does nothing when traceId is missing`() {
        service(RagProperties.Scoring(enabled = true)).scoreAsync(null, "answer", listOf(source(0.9)))
        verify(exactly = 0) { scoreClient.postNumericScore(any(), any(), any(), any()) }
    }

    @Test
    fun `posts relevance as the mean source similarity`() {
        service(RagProperties.Scoring(enabled = true, relevance = true, groundedness = false))
            .scoreAsync("t1", "answer", listOf(source(0.8), source(0.6)))
        verify { scoreClient.postNumericScore("t1", "relevance", 0.7, any()) }
        verify(exactly = 0) { scoreClient.postNumericScore(any(), "groundedness", any(), any()) }
    }

    @Test
    fun `posts groundedness from the evaluator when enabled`() {
        every { groundednessEvaluator.evaluate(any(), any()) } returns 0.5
        service(RagProperties.Scoring(enabled = true, relevance = false, groundedness = true))
            .scoreAsync("t1", "answer", listOf(source(0.9)))
        verify { scoreClient.postNumericScore("t1", "groundedness", 0.5, any()) }
    }
}
