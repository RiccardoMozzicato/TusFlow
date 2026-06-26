package g11.aiprocessingservice.tracing

import io.micrometer.tracing.Tracer
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Langfuse-oriented tagging for the Kafka-driven document pipeline (upload analysis + embedding
 * generation). These stages run on consumer threads that already carry an ambient span — the
 * manual "kafka receive file-outbox" span in MetadataProcessor, and the Spring Kafka observation
 * spans on the embedding listeners (isObservationEnabled=true, linked via the traceparent header).
 *
 * Each method tags the *current* span so the stage shows up as a meaningful Langfuse trace; all are
 * best-effort and null-safe (tracing must never affect pipeline processing).
 */
@Component
class PipelineTracing(private val tracer: Tracer) {

    /** Tags the active span for an upload-analysis run (one file). */
    fun tagUploadAnalysis(ownerUserId: UUID, fileId: UUID, filename: String, contentType: String, model: String, clientType: String) {
        tracer.currentSpan()?.let { span ->
            span.tag("langfuse.trace.name", "document-analysis")
            span.tag("langfuse.user.id", ownerUserId.toString())
            span.tag("langfuse.observation.input", "$filename ($contentType)")
            span.tag("ai.stage", "upload-analysis")
            span.tag("ai.model", model)
            span.tag("ai.client", clientType)
            span.tag("file.id", fileId.toString())
            span.tag("file.contentType", contentType)
        }
        setLangfuseTraceTags("document-pipeline")
    }

    /** Records how much text was sent to the model (proxy for token cost) on the active span. */
    fun tagAnalysisContent(charCount: Int) {
        tracer.currentSpan()?.tag("ai.content.chars", charCount.toString())
    }

    /** Tags the active span for the embedding-preparation stage (file-analyzed → chunk batches). */
    fun tagEmbeddingPreparation(ownerUserId: UUID, fileId: UUID, contentType: String, chunkCount: Int, batchCount: Int) {
        tracer.currentSpan()?.let { span ->
            // Carry the owner so this observation groups under the user too — baggage does not ride
            // the Kafka hop, so each pipeline stage stamps langfuse.user.id explicitly.
            span.tag("langfuse.user.id", ownerUserId.toString())
            span.tag("ai.stage", "embedding-preparation")
            span.tag("file.id", fileId.toString())
            span.tag("file.contentType", contentType)
            span.tag("embedding.chunkCount", chunkCount.toString())
            span.tag("embedding.batchCount", batchCount.toString())
        }
    }

    /** Tags the active span for an embedding-generation batch on the active span. */
    fun tagEmbeddingBatch(ownerUserId: String?, fileVersionId: Long, batchNumber: Int, totalBatches: Int, chunkCount: Int, model: String, dimensions: String) {
        tracer.currentSpan()?.let { span ->
            // ownerUserId is read from chunk metadata (the batch event has no owner field); stamp it
            // when present so the embedding observation groups under the user. See tagEmbeddingPreparation.
            ownerUserId?.let { span.tag("langfuse.user.id", it) }
            span.tag("ai.stage", "embedding-generation")
            span.tag("embedding.model", model)
            span.tag("embedding.dimensions", dimensions)
            span.tag("embedding.fileVersionId", fileVersionId.toString())
            span.tag("embedding.batch", "$batchNumber/$totalBatches")
            span.tag("embedding.chunkCount", chunkCount.toString())
        }
    }
}
