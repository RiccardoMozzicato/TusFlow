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
        val existingMetadata = fileService.getFileGeneratedMetadata(fileId, version)
        val emitter = SseEmitter(timeoutMs + EMITTER_TIMEOUT_GRACE_MS)
        val key = StreamKey(fileId, version)

        emitter.onCompletion { removeEmitter(key, emitter) }
        emitter.onError { removeEmitter(key, emitter) }
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
                registerEmitter(key, emitter)
                if (!sendEvent(emitter, "waiting", mapOf("message" to "Waiting for AI analysis to complete"))) {
                    removeEmitter(key, emitter)
                }
            }
        }

        return emitter
    }

    @PreDestroy
    fun shutdown() {
        timeoutScheduler.shutdownNow()
    }

    fun notifyAnalysisCompleted(fileId: UUID, version: Long, metadata: MetadataAIDTO) {
        notifyMatchingEmitters(fileId, version, "completed", metadata)
    }

    fun notifyAnalysisUnsupported(fileId: UUID, version: Long, reason: String?) {
        notifyMatchingEmitters(
            fileId,
            version,
            "unsupported",
            mapOf("error" to (reason ?: "File type not supported for AI analysis"))
        )
    }

    fun notifyAnalysisFailed(fileId: UUID, version: Long, error: String?) {
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
        log.debug("Registered SSE emitter for file {} version {}", key.fileId, key.version)
    }

    private fun removeEmitter(key: StreamKey, emitter: SseEmitter) {
        timeoutTasks.remove(emitter)?.cancel(false)
        emitters[key]?.let { keyEmitters ->
            keyEmitters.remove(emitter)
            if (keyEmitters.isEmpty()) {
                emitters.remove(key)
            }
        }
    }

    private fun scheduleTimeout(key: StreamKey, emitter: SseEmitter) {
        val timeoutTask = timeoutScheduler.schedule(
            {
                if (emitters[key]?.contains(emitter) == true) {
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
                emitter.complete()
            }
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
