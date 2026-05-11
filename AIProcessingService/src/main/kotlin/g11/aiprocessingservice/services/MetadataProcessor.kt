package g11.aiprocessingservice.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import g11.aiprocessingservice.dto.FileAnalyzedEvent
import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.MetadataDTO
import g11.aiprocessingservice.dto.jobStatus
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
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

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
    @Value("classpath:prompts/file-analysis-prompt.st") private val promptTemplate: Resource,
    @Value("\${ai.document.max-content-chars:15000}") private val maxContentChars: Int,
    @Value("\${ai.document.beginning-percentage:70}") private val beginningPercentage: Int
) : KafkaInterface {
    private val log = LoggerFactory.getLogger(MetadataProcessor::class.java)
    private val objectMapper = jacksonObjectMapper()

    @KafkaListener(id = "Ai-service-consumer", topics = ["file-outbox"])
    override fun onMessage(message: String, acknowledgment: Acknowledgment){
        log.debug("This is the message from Kafka \n {}" ,message)

        // Filter by event_type first to avoid processing irrelevant events
        try {
            val kafkaEvent = objectMapper.readTree(message)
            val eventType = kafkaEvent.get("event_type")?.asText()
            if (eventType != "FILE_INSERTED") {
                log.debug("MetadataProcessor: Ignoring event type: {}", eventType)
                acknowledgment.acknowledge()
                return
            }
        } catch (e: Exception) {
            log.error("Failed to parse event_type from Kafka message: {}", e.message, e)
        }

        val job = try {
            readJson(message)
        } catch (e: Exception) {
            log.error("Failed to parse Kafka message: {}", e.message, e)
            throw e
        }

        log.debug("Converted to json: {}", job.toString())

        val jobId = try {
            jobStatusService.createJobIfNotExists(job)
        } catch (e: Exception) {
            log.error("Failed to create/check job for file {}: {}", job.fileId, e.message, e)
            throw e
        }

        if (jobId == null) {
            log.info("Skipping job for file {} - already processed or in progress", job.fileId)
            acknowledgment.acknowledge()
            return
        }

        log.info("Job {} created successfully - acknowledging Kafka message", jobId)
        acknowledgment.acknowledge()

        try {
            val enhancedMeta = processJob(job, jobId)
            if (enhancedMeta != null) {
                publishFileAnalyzedEvent(job, jobId, enhancedMeta)
            } else {
                log.info("Skipping file-analyzed event for job {} - file analysis not supported", jobId)
            }
        } catch (e: Exception) {
            log.error("Failed to process job {}: {}", jobId, e.message, e)
            val isRetry = jobRepository.findById(jobId).orElse(null)?.status == jobStatus.FAILED
            if (isRetry) {
                log.error("Job {} RETRY FAILED - will be sent to DLT", jobId)
                jobStatusService.markJobAsFailed(jobId, job, "Retry failed: ${e.message}")
            } else {
                log.warn("Job {} FIRST FAILURE - will retry after 5 minutes", jobId)
                jobStatusService.markJobAsFailed(jobId, job, e.message ?: "Unknown error")
            }
            throw e
        }
    }

    fun processJob(job: JobDTO, jobId: UUID): MetadataDTO? {

        val fileToAnalyze = downloadFile(job.fileId, job.version)
        // Call downloadFile expect to receive an InputStream

        // Determine which AI client to use based on content type
        val clientToUse = selectAIClient(job.contentType)
        val clientName = when (clientToUse) {
            pdfAnalysisChatClient -> "PDF-specific (Llama 3.3 70B)"
            imageChatClient       -> "Image/Video-specific (Llama 4 Scout vision)"
            else                  -> "Generic (Llama 3.1 8B)"
        }
        log.info("Processing job {} with {} client for contentType: {}", jobId, clientName, job.contentType)

        try {
            // Attempt AI analysis with retry logic using the appropriate client
            val enhancedMeta = analyzeUploadWithRetry(fileToAnalyze!!, jobId, clientToUse, job.contentType)

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
                log.info("Selected PDF client (Llama 3.3 70B) for content type: {}", contentType)
                pdfAnalysisChatClient
            }
            baseMimeType.startsWith("image/", ignoreCase = true) ||
            baseMimeType.startsWith("video/", ignoreCase = true) -> {
                log.info("Selected image/video client (Llama 4 Scout vision) for content type: {}", contentType)
                imageChatClient
            }
            else -> {
                log.info("Selected generic client (Llama 3.1 8B) for content type: {}", contentType)
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
            kafkaTemplate.send("file-analyzed", job.fileId.toString(), eventJson)

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

    fun readJson(json: String): JobDTO {
        val kafkaEvent = objectMapper.readTree(json)
        val eventId = kafkaEvent.get("id").asText()
        val payloadString = kafkaEvent.get("payload").asText()

        @Suppress("UNCHECKED_CAST")
        val payloadData = objectMapper.readValue(payloadString, Map::class.java) as MutableMap<String, Any>
        payloadData["id"] = eventId

        return objectMapper.convertValue(payloadData, JobDTO::class.java)
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
            log.info(
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

        // Videos: Analysis not supported
        if (baseMimeType.startsWith("video/", ignoreCase = true)) {
            throw AIAnalysisUnsupportedException("Video analysis is not supported: $contentType")
        }

        // PDFs and text files: extract text then send to the LLM
        val contenutoFile = if (baseMimeType.equals("application/pdf", ignoreCase = true)) {
            extractTextFromPdf(inputStream)
        } else {
            inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }

        val truncatedContent = truncateContent(contenutoFile, maxContentChars)
        val prompt = promptTemplate.inputStream.bufferedReader().use { it.readText() }
            .replace("{document}", truncatedContent)

        return client.prompt()
            .user(prompt)
            .call()
            .entity(MetadataDTO::class.java)
            ?: throw IllegalStateException("AI returned null response")
    }

    /**
     * Extracts text content from a PDF file using Apache PDFBox.
     *
     * @param inputStream The PDF file input stream
     * @return Extracted text content from the PDF
     * @throws IllegalStateException if PDF parsing fails
     */
    private fun extractTextFromPdf(inputStream: InputStream): String {
        var document: org.apache.pdfbox.pdmodel.PDDocument? = null
        return try {
            document = org.apache.pdfbox.pdmodel.PDDocument.load(inputStream)
            val stripper = org.apache.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(document)
            val numPages = document.numberOfPages
            log.info("Successfully extracted {} characters from PDF ({} pages)", text.length, numPages)
            text
        } catch (e: Exception) {
            log.error("Failed to extract text from PDF: {}", e.message, e)
            throw IllegalStateException("Cannot extract text from PDF: ${e.message}", e)
        } finally {
            document?.close()
        }
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
        if (content.length <= maxChars) {
            return content
        }

        // Calculate chars from beginning and end based on configured percentage
        val beginChars = (maxChars * beginningPercentage / 100.0).toInt()
        val endChars = maxChars - beginChars

        val beginning = content.take(beginChars)
        val ending = content.takeLast(endChars)

        val truncatedBytes = content.length - maxChars
        val truncationMarker = "\n\n[... CONTENT TRUNCATED - $truncatedBytes characters omitted ...]\n\n"

        log.info("Content truncated: original {} chars -> {} chars (removed {} chars)", content.length, maxChars, truncatedBytes)

        return beginning + truncationMarker + ending
    }

    /**
     * Attempts AI analysis with retry logic (max 5 attempts)
     * Throws AIAnalysisUnsupportedException if all attempts fail
     *
     * @param inputStream The file content to analyze
     * @param jobId The job identifier for logging
     * @param client The ChatClient to use (chatClient, pdfAnalysisChatClient, or imageChatClient)
     * @param contentType The MIME type of the file
     */
    private fun analyzeUploadWithRetry(inputStream: InputStream, jobId: UUID, client: ChatClient, contentType: String): MetadataDTO {
        val maxRetries = 5
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                log.info("AI analysis attempt {}/{} for job {}", attempt, maxRetries, jobId)

                // Reset stream if possible (for retries after the first attempt)
                if (attempt > 1) {
                    if (inputStream.markSupported()) {
                        inputStream.reset()
                    } else {
                        log.warn("InputStream does not support reset, retry may fail")
                    }
                }

                val result = analyzeUpload(inputStream, client, contentType)

                // Success
                log.info("AI analysis succeeded on attempt {} for job {}", attempt, jobId)
                return result

            } catch (e: AIAnalysisUnsupportedException) {
                // Do not retry for permanently unsupported content types (e.g. video)
                throw e
            } catch (e: NonTransientAiException) {
                // 4xx status response, do not retry
                throw AIAnalysisUnsupportedException(e.message?: "Bad Request")
            } catch (e: Exception) {
                lastException = e
                log.warn("AI analysis attempt {} failed for job {}: {}", attempt, jobId, e.message)

                if (attempt < maxRetries) {
                    // Wait before retry with exponential backoff (1s, 2s, 3s, 4s, 5s max)
                    val delayMs = (1000L * attempt).coerceAtMost(5000L)
                    log.info("Retrying in {}ms...", delayMs)
                    Thread.sleep(delayMs)
                }
            }
        }

        // All 5 attempts failed - rethrow the original exception so the job is marked as FAILED,
        // not UNSUPPORTED. UNSUPPORTED is reserved for truly unsupported content types (e.g. video).
        // LLM unavailability or an expired API key are transient/infrastructure failures.
        log.error("AI analysis failed after {} attempts for job {} - marking as FAILED", maxRetries, jobId, lastException)
        throw lastException!!
    }


}

