package g11.aiprocessingservice.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import g11.aiprocessingservice.dto.FileAnalyzedEvent
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.MetadataDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import g11.aiprocessingservice.exceptions.AIAnalysisUnsupportedException
import g11.aiprocessingservice.repositories.JobRepository
import org.apache.kafka.common.errors.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.retry.NonTransientAiException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.util.MimeType
import org.springframework.http.HttpStatus
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.io.InputStream
import java.util.UUID
import org.springframework.scheduling.annotation.Async
import java.util.concurrent.CompletableFuture

@Service
class MetadataProcessor(
    private val jobRepository : JobRepository,
    private val authService: AuthServiceInterface,
    @Qualifier("TusUploaderService") private val fileServiceClient: RestClient,
    @Qualifier("chatClient") private val chatClient: ChatClient,
    @Qualifier("pdfAnalysisChatClient") private val pdfAnalysisChatClient: ChatClient,
    @Qualifier("imageChatClient") private val imageChatClient: ChatClient,
    private val jobStatusService: JobStatusServiceInterface,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val tracer: Tracer,
    private val propagator: Propagator,
    @Value("classpath:prompts/file-analysis-prompt.st") private val promptTemplate: Resource,
    @Value("\${ai.chat.model}") private val chatModelName: String,
    @Value("\${ai.pdf.model}") private val pdfModelName: String,
    @Value("\${ai.image.model}") private val imageModelName: String,
    @Value("\${ai.document.max-content-chars:15000}") private val maxContentChars: Int,
    @Value("\${ai.document.beginning-percentage:70}") private val beginningPercentage: Int,
    @Value("\${ai.retry.max-automatic-retries:5}") private val maxAutomaticRetries: Int,
    @Value("\${ai.retry.analysis-backoff-base-ms:1000}") private val analysisBackoffBaseMs: Long,
    @Value("\${ai.retry.analysis-backoff-cap-ms:5000}") private val analysisBackoffCapMs: Long,
    @Value("\${ai.retry.max-user-retries:4}") private val maxUserRetries: Int,
    @Value("\${ai.retry.simulate-llm-offline:false}") private val simulateLlmOffline: Boolean,
    private val retryJobService: RetryJobService,
    private val textExtractionService: TextExtractionService,
    private val supportedContentTypes: SupportedContentTypes,
    private val pipelineTracing: g11.aiprocessingservice.tracing.PipelineTracing,
) : KafkaInterface {
    private val log = LoggerFactory.getLogger(g11.aiprocessingservice.services.MetadataProcessor::class.java)
    private val objectMapper = jacksonObjectMapper()

    /**
     * Non-blocking retry: on failure the record is forwarded to a retry topic with a delay and the
     * main consumer keeps polling, instead of the old DefaultErrorHandler that slept (blocked) the
     * single consumer thread for 5 minutes and froze all other file processing. After the configured
     * attempts are exhausted the record lands in `file-outbox-dlt`.
     *
     * `concurrency` (bounded by the file-outbox partition count) lets several files be analyzed in
     * parallel rather than strictly one at a time.
     */
    @RetryableTopic(
        attempts = "\${ai.retry.kafka-attempts:2}",
        backoff = Backoff(delayExpression = "\${ai.retry.kafka-delay-ms:300000}"),
        kafkaTemplate = "kafkaTemplate",
        listenerContainerFactory = "debeziumKafkaListenerContainerFactory",
    )
    @KafkaListener(
        id = "Ai-service-consumer",
        topics = ["file-outbox"],
        containerFactory = "debeziumKafkaListenerContainerFactory",
        concurrency = "\${kafka.file-outbox.concurrency:3}",
    )
    override fun onMessage(message: String){
        log.debug("Received message from kafka \n {}" ,message)

        // Resume the upstream trace carried in the Debezium outbox payload body.
        // The RecordInterceptor approach fires too late (after Spring Kafka creates the
        // observation from the original record headers), so we do it manually here.
        val traceparent = extractTraceparentFromPayload(message)
        val span = if (traceparent != null) {
            propagator.extract(mapOf("traceparent" to traceparent)) { map, key -> map[key] }
                .name("kafka receive file-outbox")
                .start()
        } else {
            tracer.nextSpan().name("kafka receive file-outbox").start()
        }
        val scope = tracer.withSpan(span)
        try {
            // Prima filtra per event_type per evitare di processare eventi non pertinenti
            try {
                val mapper = jacksonObjectMapper()
                val kafkaEvent = mapper.readTree(message)
                val eventType = kafkaEvent.get("event_type")?.asText()

                if (eventType != "FILE_INSERTED") {
                    log.debug("MetadataProcessor: Ignoring event type: {}", eventType)
                    return
                }
            } catch (e: Exception) {
                log.error("Failed to parse event_type from Kafka message: {}", e.message, e)
            }

            // Parse JSON message
            val job = try {
                readJson(message)
            } catch (e: Exception) {
                log.error("Failed to parse Kafka message: {}", e.message, e)
                throw e // Cannot proceed without valid message
            }

            log.debug("Converted to json: {}", job.toString())

            // Create or check if job exists
            val jobId = try {
                createJobIfNotExists(job)
            } catch (e: Exception) {
                log.error("Failed to create/check job for file {}: {}", job.fileId, e.message, e)
                throw e // Cannot proceed without job record
            }

            if(jobId == null){
                log.info("Skipping job for file {} - already processed or in progress", job.fileId)
                // Job already exists and is not resumable - return so the container commits the offset
                return
            }

            // Process the job. The offset is committed by the container (AckMode.RECORD) only when
            // this method returns normally; if processing throws, @RetryableTopic forwards the record
            // to the retry topic without blocking this consumer thread.
            log.info("Processing job {}", jobId)
            getMetadata(job)

        } finally {
            scope.close()
            span.end()
        }
    }

    /**
     * Fires when the automatic file-outbox retries are exhausted. This is NOT terminal for the
     * file-analysis pipeline: the job is left FAILED and the user still has manual retries. The
     * dead_letter_events audit row is written only once those are also spent (see
     * JobStatusService.markJobAsDeadLettered), so here we only log — no audit row.
     */
    @DltHandler
    fun handleDlt(
        message: String,
        @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) exceptionType: ByteArray?,
        @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) exceptionMessage: ByteArray?,
    ) {
        val type = exceptionType?.toString(Charsets.UTF_8)
        val msg = exceptionMessage?.toString(Charsets.UTF_8)
        log.warn("file-outbox automatic retries exhausted [{}]: {} — job left FAILED for user retry", type, msg)
    }

    fun getMetadata(job: JobDTO) {
        log.info("Calling AI for $job")

        val jobId = job.id

        try {
            val enhancedMeta = processJob(job, jobId)

            // Publish event on file-analyzed with FileAnalyzedEvent DTO
            if (enhancedMeta != null) {
                publishFileAnalyzedEvent(job, jobId, enhancedMeta)
            } else {
                log.info("Skipping file-analyzed event for job {} - file analysis not supported", jobId)
            }

        } catch (e: Exception) {
            log.error("Failed to process job {}: {}", jobId, e.message, e)

            // Terminal check. `attempts` counts user retries only (incremented in
            // RetryJobService.prepareRetry). Once the user has spent all of them, dead-letter the
            // job (audit row + terminal DEAD_LETTERED status); otherwise mark it FAILED so it stays
            // in the retryable failed-jobs list. The automatic Kafka path never increments attempts,
            // so it always takes the FAILED branch — its retry routing is owned by @RetryableTopic.
            val attempts = jobRepository.findById(jobId).orElse(null)?.attempts ?: 0u
            if (attempts >= maxUserRetries.toUInt()) {
                jobStatusService.markJobAsDeadLettered(jobId, job, e.message ?: "Unknown error", e::class.qualifiedName)
            } else {
                jobStatusService.markJobAsFailed(jobId, job, e.message ?: "Unknown error")
            }
            throw e
        }
    }


    @Async
    fun retryFailedJobAsync(jobId: UUID): CompletableFuture<Void> {
        val jobDto = retryJobService.prepareRetry(jobId)
        log.info("Calling getMetadata() for $jobDto")
        getMetadata(jobDto)
        return CompletableFuture.completedFuture(null)
    }


    /**
     * Extracts the W3C traceparent from the Debezium outbox message body.
     * Debezium wraps the DB payload as an escaped JSON string in the "payload" field.
     */
    private fun extractTraceparentFromPayload(message: String): String? = try {
        val root = objectMapper.readTree(message)
        root["payload"]?.asText()?.let { payloadStr ->
            objectMapper.readTree(payloadStr)["traceparent"]?.asText()?.takeIf { it.isNotBlank() }
        } ?: root["traceparent"]?.asText()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createJobIfNotExists(job: JobDTO): UUID? {
        // Use the event ID from Kafka as the job ID
        val jobId = job.id

        // Check if job exists
        log.debug("Checking if the job already exists for file: {}, version: {}", job.fileId, job.version)
        val existingJob = jobRepository.findById(jobId)
        log.debug(existingJob.toString())

        if (existingJob.isPresent) {
            log.debug("Job already exists")
            val jobEntity = existingJob.get()
            // PENDING = first delivery still in flight; FAILED = a @RetryableTopic redelivery of a
            // job we already marked failed. Both must reprocess. COMPLETED/UNSUPPORTED are terminal
            // and skipped. (FAILED was previously skipped here, which silently no-op'd every retry.)
            return when (jobEntity.status) {
                jobStatus.PENDING -> {
                    log.warn("Resuming stuck job: {}", jobEntity.jobid)
                    jobId
                }
                jobStatus.FAILED -> {
                    log.info("Retrying previously FAILED job: {}", jobEntity.jobid)
                    jobEntity.status = jobStatus.PENDING
                    jobRepository.save(jobEntity)
                    jobId
                }
                else -> {
                    log.info("Job {} already existing and {}. Skipping creation.", jobEntity.jobid, jobEntity.status)
                    null
                }
            }
        }

        log.debug("Creating a new job")
        // Create payload map from JobDTO fields
        val payloadMap: MutableMap<String, Any> = mutableMapOf(
            "filename" to job.filename,
            "contentType" to job.contentType,
            "ownerUserId" to job.ownerUserId.toString(),
            "fileVersionId" to job.fileVersionId
        )

        // Add metadata if present
        job.metadata?.let {
            payloadMap["metadata"] = it as Any
        }

        val newJob = Job(
            jobid = jobId,
            fileid = job.fileId,
            version = job.version,
            type = "FILE_ANALYSIS",  // Default job type for AI processing
            payload = payloadMap,
            status = jobStatus.PENDING,
            attempts = 0u
        )
        jobRepository.save(newJob)
        return jobId
    }

    fun processJob(job: JobDTO, jobId: UUID): MetadataDTO? {

        // Gate by content type before downloading: types we can't analyze (e.g. binaries like
        // .dmg, archives) are marked UNSUPPORTED without pulling their bytes — a .dmg can be huge.
        if (!supportedContentTypes.isAnalyzable(job.contentType)) {
            log.info("Skipping AI analysis for job {} - unsupported content type: {}", jobId, job.contentType)
            jobStatusService.markJobAsUnsupported(
                jobId,
                job,
                "Content type not supported for AI analysis: ${job.contentType}",
            )
            return null
        }

        val fileToAnalyze = downloadFile(job.fileId, job.version)
        // Call downloadFile expect to receive an InputStream

        // Determine which AI client to use based on content type
        val clientToUse = selectAIClient(job.contentType)
        val modelName = when (clientToUse) {
            pdfAnalysisChatClient -> pdfModelName
            imageChatClient       -> imageModelName
            else                  -> chatModelName
        }
        val clientName = when (clientToUse) {
            pdfAnalysisChatClient -> "pdf"
            imageChatClient       -> "image"
            else                  -> "generic"
        }
        log.info("Processing job {} with {} client ({}) for contentType: {}", jobId, clientName, modelName, job.contentType)
        pipelineTracing.tagUploadAnalysis(job.ownerUserId, job.fileId, job.filename, job.contentType, modelName, clientName)

        try {
            log.debug("First attempt / Kafka attempt / User attempt")
            // Attempt AI analysis with retry logic using the appropriate client
            val enhancedMeta = analyzeUploadWithRetry(fileToAnalyze!!, job, clientToUse, job.contentType)

            log.debug(enhancedMeta.toString())

            // For testing FAILED state (simulates incomplete metadata from AI)
            //throw IllegalStateException("Testing FAILED state")


            // Validate that AI returned all required fields
            if (enhancedMeta.summary == null || enhancedMeta.tags == null || enhancedMeta.sensitivity == null) {
                throw IllegalStateException("AI returned incomplete metadata: summary=${enhancedMeta.summary}, tags=${enhancedMeta.tags}, sensitivity=${enhancedMeta.sensitivity}")
            }

            // Mark job as completed (uses separate transaction)
            jobStatusService.markJobAsCompleted(jobId, job, enhancedMeta)

            return enhancedMeta

        } catch (e: AIAnalysisUnsupportedException) {
            // AI analysis not supported for this file - mark as UNSUPPORTED with default metadata
            jobStatusService.markJobAsUnsupported(jobId, job, e.message)
            return null

        } catch (e: IllegalStateException) {
            // AI returned incomplete metadata - propagate to let markJobAsFailed handle it
            log.error("AI returned incomplete metadata for job {}: {}", jobId, e.message)
            throw e
        }
    }

    /**
     * Selects the appropriate AI client based on file content type.
     *
     * @param contentType The MIME type of the file (may include charset, e.g., "application/pdf;charset=UTF-8")
     * @return The appropriate ChatClient (pdfAnalysisChatClient for PDFs, chatClient for others)
     */
    private fun selectAIClient(contentType: String): ChatClient {
        // Extract the base MIME type (remove charset and other parameters)
        val baseMimeType = contentType.split(";").firstOrNull()?.trim() ?: contentType

        return when {
            baseMimeType.equals("application/pdf", ignoreCase = true) -> {
                log.info("Selected PDF client ({}) for content type: {}", pdfModelName, contentType)
                pdfAnalysisChatClient
            }
            baseMimeType.startsWith("image/", ignoreCase = true) ||
            baseMimeType.startsWith("video/", ignoreCase = true) -> {
                log.info("Selected image/video client ({}) for content type: {}", imageModelName, contentType)
                imageChatClient
            }
            else -> {
                log.info("Selected generic client ({}) for content type: {}", chatModelName, contentType)
                chatClient
            }
        }
    }

    /**
     * Publishes a file-analyzed event to Kafka topic for downstream processing (e.g., embedding generation).
     *
     * @param job The original job DTO
     * @param jobId The job identifier
     * @param metadata The enhanced metadata extracted from AI analysis
     */
    private fun publishFileAnalyzedEvent(job: JobDTO, jobId: UUID, metadata: MetadataDTO) {
        try {
            val event = FileAnalyzedEvent(
                jobId = jobId,
                fileId = job.fileId,
                version = job.version,
                fileVersionId = job.fileVersionId,  // Già un Long, nessuna conversione necessaria
                filename = job.filename,
                contentType = job.contentType,
                ownerUserId = job.ownerUserId,
                summary = metadata.summary,
                tags = metadata.tags?.split(",")?.map { it.trim() },
                sensitivity = metadata.sensitivity?.name
            )

            val eventJson = objectMapper.writeValueAsString(event)
            val headers = RecordHeaders()
            tracer.currentSpan()?.context()?.let {
                headers.add("traceparent", "00-${it.traceId()}-${it.spanId()}-01".toByteArray(Charsets.UTF_8))
            }
            kafkaTemplate.send(ProducerRecord("file-analyzed", null, job.fileId.toString(), eventJson, headers))

            log.info("Published file-analyzed event for file {} (job {}) to Kafka", job.fileId, jobId)
        } catch (e: Exception) {
            log.error("Failed to publish file-analyzed event for job {}: {}", jobId, e.message, e)
            // Don't throw - job is already marked as completed
        }
    }

    fun downloadFile(fileid: UUID, version: Long?) : InputStream? {
        val token = authService.getAccessToken()

        try {
            val resource = fileServiceClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/v1/files/${fileid}/download")
                        .queryParam("version", version)
                        .build()
                }
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .body(Resource::class.java)

            return resource?.inputStream
        } catch (ex: RestClientResponseException) {
            log.error("Error calling file service for download {}: {}", fileid, ex.message)
            if (ex.statusCode.value() == 404) {
                throw ResourceNotFoundException("Problem with file: $fileid")
            }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "File service error: ${ex.statusText}")
        }
    }

    fun readJson (json: String) : JobDTO{
        // Parse the Kafka event wrapper
        val mapper = jacksonObjectMapper()
        val kafkaEvent = mapper.readTree(json)

        // Extract the id from the Kafka event
        val eventId = kafkaEvent.get("id").asText()

        // Extract the payload field (which contains the actual job data as a JSON string)
        val payloadString = kafkaEvent.get("payload").asText()

        // Parse the payload into a mutable map
        val payloadData = mapper.readValue(payloadString, Map::class.java) as MutableMap<String, Any>

        // Add the event ID to the payload data
        payloadData["id"] = eventId

        // Convert back to JSON and parse as JobDTO
        return mapper.convertValue(payloadData, JobDTO::class.java)
    }

    fun analyzeUpload(inputStream: InputStream, client: ChatClient, contentType: String): MetadataDTO {
        val baseMimeType = contentType.split(";").firstOrNull()?.trim() ?: contentType

        // Images: base64-encode, validate size, then send to the vision model via Media
        if (baseMimeType.startsWith("image/", ignoreCase = true)) {
            val imageBytes = inputStream.readBytes()

            // Base64 encodes every 3 raw bytes into 4 ASCII characters (with padding).
            // Exact encoded size without actually encoding: ceil(n / 3) * 4 == ((n + 2) / 3) * 4
            val base64SizeBytes = ((imageBytes.size.toLong() + 2) / 3) * 4

            val maxBase64Bytes = 4L * 1024 * 1024 // 4 MB
            if (base64SizeBytes > maxBase64Bytes) {
                log.info(
                    "Image rejected: base64 size is {} MB, exceeds the 4 MB limit",
                    "%.2f".format(base64SizeBytes / 1_048_576.0)
                )
                throw AIAnalysisUnsupportedException(
                    "Image base64-encoded size (${"%.2f".format(base64SizeBytes / 1_048_576.0)} MB) exceeds the 4 MB limit"
                )
            }
            log.debug(
                "Image accepted: base64 size {} KB (limit 4 MB)",
                base64SizeBytes / 1024
            )

            // Spring AI sends ByteArrayResource as a base64 data-URI to the OpenAI-compatible API
            val imageResource = ByteArrayResource(imageBytes)
            return client.prompt()
                .user { u ->
                    u.text("Analyze this image carefully and provide the metadata as specified in your instructions. Return ONLY valid JSON, no markdown.")
                    u.media(MimeType.valueOf(baseMimeType), imageResource)
                }
                .call()
                .entity(MetadataDTO::class.java)
                ?: throw IllegalStateException("AI returned null response")
        }

        // Documents (PDF, DOCX, text, text-like): extract text via the shared service, then send
        // it to the LLM. The content type was already gated in processJob and images are handled
        // above, so only text-extractable types reach here.
        val fileContent = textExtractionService.extract(inputStream, contentType).content

        val truncatedContent = truncateContent(fileContent, maxContentChars)
        pipelineTracing.tagAnalysisContent(truncatedContent.length)
        val prompt = promptTemplate.inputStream.bufferedReader().use { it.readText() }
            .replace("{document}", truncatedContent)

        return client.prompt()
            .user(prompt)
            .call()
            .entity(MetadataDTO::class.java)
            ?: throw IllegalStateException("AI returned null response")
    }

    /**
     * Truncates content intelligently to fit within AI token limits.
     *
     * Strategy:
     * 1. If content is short enough, use it all
     * 2. If too long, take beginning + end (context from both)
     * 3. Add truncation marker to inform AI
     *
     * Configuration via application.yml:
     * - ai.document.max-content-chars: Maximum characters to send
     * - ai.document.beginning-percentage: Percentage from beginning (rest from end)
     *
     * @param content The original document content
     * @param maxChars Maximum characters to send (from config)
     * @return Truncated content with metadata
     */
    private fun truncateContent(content: String, maxChars: Int): String {
        // maxChars <= 0 disables truncation entirely — for large-context models
        // (e.g. Qwen via OpenWebUI) the whole document fits, so set
        // ai.document.max-content-chars to 0 to send it untruncated.
        if (maxChars <= 0 || content.length <= maxChars) {
            return content
        }

        // Calculate chars from beginning and end based on configured percentage
        val beginChars = (maxChars * beginningPercentage / 100.0).toInt()
        val endChars = maxChars - beginChars

        val beginning = content.take(beginChars)
        val ending = content.takeLast(endChars)

        val truncatedBytes = content.length - maxChars
        val truncationMarker = "\n\n[... CONTENT TRUNCATED - ${truncatedBytes} characters omitted ...]\n\n"

        log.info("Content truncated: original {} chars -> {} chars (removed {} chars)", content.length, maxChars, truncatedBytes)

        return beginning + truncationMarker + ending
    }

    /**
     * Runs AI analysis with retry logic. The exponential-backoff loop (up to [maxAutomaticRetries])
     * applies only to the FIRST analysis of a fresh upload. A job that has already failed once —
     * reaching here via a Kafka retry-topic redelivery or a manual user retry, detectable by the
     * "error" key [JobStatusService.markJobAsFailed] leaves in the payload — gets a single immediate
     * attempt with no backoff. Throws AIAnalysisUnsupportedException for permanently unsupported content.
     *
     * @param inputStream The file content to analyze
     * @param job The job being processed (used for logging + first-attempt detection)
     * @param client The ChatClient to use (chatClient, pdfAnalysisChatClient, or imageChatClient)
     * @param contentType The MIME type of the file
     */
    private fun analyzeUploadWithRetry(inputStream: InputStream, job: JobDTO, client: ChatClient, contentType: String): MetadataDTO {
        var lastException: Exception? = null
        var attempt = 1
        val jobEntity: Job = jobRepository.findById(job.id).orElseThrow()

        // Backoff retries apply only to a fresh upload; a retry (Kafka redelivery / user retry) — a
        // job that has already failed, so its payload carries "error" — gets a single immediate try.
        val hasFailedBefore = jobEntity.payload?.containsKey("error") == true
        log.debug("Job {} hasFailedBefore: {}", job.id, hasFailedBefore)
        val maxAttempts = if (hasFailedBefore) 1 else maxAutomaticRetries

        while (attempt <= maxAttempts) {
            try {
                log.debug("AI analysis attempt {}/{} for job {}", attempt, maxAttempts, job.id)

                // Reset stream if possible (for retries after the first attempt)
                if (attempt > 1) {
                    if (inputStream.markSupported()) {
                        inputStream.reset()
                    } else {
                        log.warn("InputStream does not support reset, retry may fail")
                    }
                }

                if (simulateLlmOffline) throw RuntimeException("LLM offline simulation enabled")

                val result = analyzeUpload(inputStream, client, contentType)

                // Success
                log.info("AI analysis succeeded on attempt {} for job {}", attempt, job.id)
                return result
            } catch (e: AIAnalysisUnsupportedException) {
                // Do not retry for permanently unsupported content types (e.g. video)
                throw e
            } catch (e: NonTransientAiException) {
                // 4xx status response, do not retry
                log.error("Non transient exception occurred for job {} : {}", job.id, e.message)
                throw NonTransientAiException(e.message ?: "Bad Request")
            } catch (e: Exception) {
                lastException = e
                log.warn("AI analysis backoff attempt {} failed for job {}: {}", attempt, job.id, e.message)

                if (attempt < maxAttempts) {
                    // Exponential backoff from the configured base, capped (default 1s, 2s, 4s, …
                    // capped at 5s). Only reached on a fresh upload (maxAttempts > 1); retries run
                    // once and never sleep. A base of 0 (tests) disables the sleep entirely.
                    val delayMs = (analysisBackoffBaseMs * (1L shl (attempt - 1))).coerceAtMost(analysisBackoffCapMs)
                    if (delayMs > 0) {
                        log.info("Retrying in {}ms...", delayMs)
                        Thread.sleep(delayMs)
                    }
                }

                attempt++
            }
        }

        // Attempts exhausted - rethrow the original exception so the job is marked FAILED, not
        // UNSUPPORTED. UNSUPPORTED is reserved for truly unsupported content types (e.g. video);
        // LLM unavailability or an expired API key are transient/infrastructure failures.
        log.error("AI analysis failed after {} attempt(s) for job {} - marking as {FAILED}", maxAttempts, job.id, lastException)
        throw lastException!!
    }


}

