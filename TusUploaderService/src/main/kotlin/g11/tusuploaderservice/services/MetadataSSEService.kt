package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.MetadataAIDTO
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Service
class MetadataSSEService(
    private val fileService: FileServiceInterface,
    @Value("\${ai.metadata.sse.timeout-ms:60000}") private val timeoutMs: Long
) {
    private val log = LoggerFactory.getLogger(MetadataSSEService::class.java)
    private val emitters = ConcurrentHashMap<StreamKey, MutableSet<SseEmitter>>()
    private val timeoutTasks = ConcurrentHashMap<SseEmitter, ScheduledFuture<*>>()
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "metadata-sse-timeout").apply { isDaemon = true }
    }

    private data class StreamKey(val fileId: UUID, val version: Long?)

    /**
     * Stream metadata processing status via Server-Sent Events (SSE).
     * The stream is notified by Kafka job-outbox events instead of polling AIProcessingService.
     *
     * @param fileId The UUID of the file
     * @param version The version of the file (optional, defaults to current version)
     * @return SseEmitter that emits status events
     */
    fun streamMetadataProcessingStatus(fileId: UUID, version: Long?): SseEmitter {
        log.info("Creating SSE stream for file {} with version {}", fileId, version)
        val existingMetadata = fileService.getFileGeneratedMetadata(fileId, version)
        val emitter = SseEmitter(timeoutMs + EMITTER_TIMEOUT_GRACE_MS)
        val key = StreamKey(fileId, version)

        emitter.onCompletion { 
            log.debug("SSE emitter completed for file {} version {}", fileId, version)
            removeEmitter(key, emitter) 
        }
        emitter.onError { throwable ->
            log.warn("SSE emitter error for file {} version {}: {}", fileId, version, throwable.message)
            removeEmitter(key, emitter) 
        }
        emitter.onTimeout {
            log.debug("SSE emitter timed out for file {} version {}", fileId, version)
            removeEmitter(key, emitter)
        }

        when {
            isUnsupported(existingMetadata) -> {
                log.debug("Metadata already marked unsupported for file {} version {}", fileId, version)
                sendAndComplete(key, emitter, "unsupported", mapOf("error" to "File type not supported for AI analysis"))
            }
            isFailed(existingMetadata) -> {
                log.debug("Metadata already marked failed for file {} version {}", fileId, version)
                sendAndComplete(key, emitter, "failed", mapOf("error" to "AI analysis failed"))
            }
            isCompleted(existingMetadata) -> {
                log.debug("Metadata already available for file {} version {}", fileId, version)
                sendAndComplete(key, emitter, "completed", existingMetadata)
            }
            else -> {
                log.debug("Starting to wait for AI analysis for file {} version {}", fileId, version)
                registerEmitter(key, emitter)
                if (!sendEvent(emitter, "waiting", mapOf("message" to "Waiting for AI analysis to complete"))) {
                    log.warn("Failed to send waiting event for file {} version {}", fileId, version)
                    removeEmitter(key, emitter)
                }
            }
        }

        return emitter
    }

    @PreDestroy
    fun shutdown() {
        log.info("Shutting down MetadataSSEService")
        timeoutScheduler.shutdownNow()
    }

    fun notifyAnalysisCompleted(fileId: UUID, version: Long, metadata: MetadataAIDTO) {
        log.info("Analysis completed for file {} version {}", fileId, version)
        notifyMatchingEmitters(fileId, version, "completed", metadata)
    }

    fun notifyAnalysisUnsupported(fileId: UUID, version: Long, reason: String?) {
        log.warn("Analysis unsupported for file {} version {}: {}", fileId, version, reason)
        notifyMatchingEmitters(
            fileId,
            version,
            "unsupported",
            mapOf("error" to (reason ?: "File type not supported for AI analysis"))
        )
    }

    fun notifyAnalysisFailed(fileId: UUID, version: Long, error: String?) {
        log.error("Analysis failed for file {} version {}: {}", fileId, version, error)
        notifyMatchingEmitters(
            fileId,
            version,
            "failed",
            mapOf("error" to (error ?: "AI analysis failed"))
        )
    }

    private fun registerEmitter(key: StreamKey, emitter: SseEmitter) {
        val keyEmitters = emitters.computeIfAbsent(key) {
            ConcurrentHashMap.newKeySet()
        }
        keyEmitters.add(emitter)
        scheduleTimeout(key, emitter)
        log.debug("Registered SSE emitter for file {} version {} (total emitters: {})", key.fileId, key.version, keyEmitters.size)
    }

    private fun removeEmitter(key: StreamKey, emitter: SseEmitter) {
        log.debug("Removing SSE emitter for file {} version {}", key.fileId, key.version)
        timeoutTasks.remove(emitter)?.cancel(false)
        emitters[key]?.let { keyEmitters ->
            keyEmitters.remove(emitter)
            if (keyEmitters.isEmpty()) {
                log.debug("No more emitters for key file {} version {}, removing key", key.fileId, key.version)
                emitters.remove(key)
            }
        }
    }

    private fun scheduleTimeout(key: StreamKey, emitter: SseEmitter) {
        log.debug("Scheduling timeout ({} ms) for file {} version {}", timeoutMs, key.fileId, key.version)
        val timeoutTask = timeoutScheduler.schedule(
            {
                if (emitters[key]?.contains(emitter) == true) {
                    log.warn("Timeout reached for file {} version {}", key.fileId, key.version)
                    sendAndComplete(
                        key,
                        emitter,
                        "timeout",
                        mapOf("error" to "AI analysis is taking longer than expected. The file may be too large or complex. Please try again later.")
                    )
                }
            },
            timeoutMs,
            TimeUnit.MILLISECONDS
        )
        timeoutTasks[emitter] = timeoutTask
    }

    private fun notifyMatchingEmitters(fileId: UUID, version: Long, eventType: String, data: Any) {
        log.debug("Notifying emitters for file {} version {} with event type: {}", fileId, version, eventType)
        val keys = listOf(StreamKey(fileId, version), StreamKey(fileId, null))
        keys.forEach { key ->
            val keyEmitters = emitters[key]?.toList().orEmpty()
            keyEmitters.forEach { emitter ->
                sendAndComplete(key, emitter, eventType, data)
            }
        }
    }

    private fun sendAndComplete(key: StreamKey, emitter: SseEmitter, eventType: String, data: Any) {
        try {
            if (sendEvent(emitter, eventType, data)) {
                log.debug("Completing SSE stream with event type: {} for file {} version {}", eventType, key.fileId, key.version)
                emitter.complete()
            }
        } catch (e: Exception) {
            log.error("Error completing SSE stream for file {} version {}: {}", key.fileId, key.version, e.message, e)
        } finally {
            removeEmitter(key, emitter)
        }
    }

    private fun sendEvent(emitter: SseEmitter, eventType: String, data: Any): Boolean {
        return try {
            emitter.send(
                SseEmitter.event()
                    .name(eventType)
                    .data(data)
            )
            log.debug("Sent SSE event: {}", eventType)
            true
        } catch (e: Exception) {
            log.debug("SSE stream closed while sending event '{}': {}", eventType, e.javaClass.simpleName)
            false
        }
    }

    private fun isCompleted(metadata: MetadataAIDTO): Boolean {
        if (metadata.analysisStatus == "COMPLETED") {
            return true
        }

        // Backward compatibility for files analyzed before analysisStatus was persisted.
        return metadata.analysisStatus.isNullOrBlank() &&
                !metadata.summary.isNullOrBlank() &&
                !metadata.tags.isNullOrBlank() &&
                !metadata.sensitivity.isNullOrBlank() &&
                !isFailed(metadata) &&
                !isUnsupported(metadata)
    }

    private fun isFailed(metadata: MetadataAIDTO): Boolean {
        return metadata.summary == "AI analysis failed"
    }

    private fun isUnsupported(metadata: MetadataAIDTO): Boolean {
        return metadata.summary == "AI analysis not supported"
    }

    private companion object {
        const val EMITTER_TIMEOUT_GRACE_MS = 5000L
    }
}
