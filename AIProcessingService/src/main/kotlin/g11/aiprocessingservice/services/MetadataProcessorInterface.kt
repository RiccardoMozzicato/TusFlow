package g11.aiprocessingservice.services

import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Callable entry points into the AI metadata pipeline. The Kafka-driven consumption is an
 * internal concern of the implementation; this interface exposes only the operations other
 * beans invoke directly, so callers depend on the abstraction rather than the concrete bean.
 */
interface MetadataProcessorInterface {

    /**
     * Re-runs the AI analysis for a previously failed job, off the request thread.
     * Resets the job to PENDING and reprocesses it.
     */
    fun retryFailedJobAsync(jobId: UUID): CompletableFuture<Void>
}