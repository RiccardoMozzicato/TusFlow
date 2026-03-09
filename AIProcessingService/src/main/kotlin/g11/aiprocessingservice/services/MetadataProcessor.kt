package g11.aiprocessingservice.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import g11.aiprocessingservice.dto.FileAnalyzedEvent
import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.MetadataDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import g11.aiprocessingservice.exceptions.AIAnalysisUnsupportedException
import g11.aiprocessingservice.repositories.JobRepository
import org.apache.kafka.common.errors.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
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
    private val jobStatusService: JobStatusServiceInterface,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("classpath:prompts/file-analysis-prompt.st") private val promptTemplate: Resource,
    @Value("\${ai.document.max-content-chars:15000}") private val maxContentChars: Int,
    @Value("\${ai.document.beginning-percentage:70}") private val beginningPercentage: Int
) : KafkaInterface {
    private val log = LoggerFactory.getLogger(g11.aiprocessingservice.services.MetadataProcessor::class.java)
    private val objectMapper = jacksonObjectMapper()

    @KafkaListener(id = "Ai-service-consumer", topics = ["file-outbox"])
    override fun onMessage(message: String, acknowledgment: Acknowledgment){
        log.debug("This is the message from Kafka \n {}" ,message)

        // Prima filtra per event_type per evitare di processare eventi non pertinenti
        try {
            val mapper = jacksonObjectMapper()
            val kafkaEvent = mapper.readTree(message)
            val eventType = kafkaEvent.get("event_type")?.asText()

            if (eventType != "FILE_INSERTED") {
                log.debug("MetadataProcessor: Ignoring event type: {}", eventType)
                acknowledgment.acknowledge()
                return
            }
        } catch (e: Exception) {
            log.error("Failed to parse event_type from Kafka message: ${e.message}", e)
        }

        // Parse JSON message
        val job = try {
            readJson(message)
        } catch (e: Exception) {
            log.error("Failed to parse Kafka message: ${e.message}", e)
            throw e // Cannot proceed without valid message
        }

        log.debug("Converted to json: {}", job.toString())

        // Create or check if job exists
        val jobId = try {
            createJobIfNotExists(job)
        } catch (e: Exception) {
            log.error("Failed to create/check job for file ${job.fileId}: ${e.message}", e)
            throw e // Cannot proceed without job record
        }

        if(jobId == null){
            log.info("Skipping job for file ${job.fileId} - already processed or in progress")
            // Job already exists and is not PENDING - acknowledge the message to commit offset
            acknowledgment.acknowledge()
            return
        }

        // Job created successfully - acknowledge the message to commit the offset
        // This ensures the message is removed from files_outbox_events after job creation
        log.info("Job $jobId created successfully - acknowledging Kafka message")
        acknowledgment.acknowledge()

        // Process the job
        try {
            val enhancedMeta = processJob(job, jobId)

            // Publish event on file-analyzed with FileAnalyzedEvent DTO
            if (enhancedMeta != null) {
                publishFileAnalyzedEvent(job, jobId, enhancedMeta)
            } else {
                log.info("Skipping file-analyzed event for job $jobId - file analysis not supported")
            }

        } catch (e: Exception) {
            log.error("Failed to process job $jobId: ${e.message}", e)

            // Check if this is a retry by looking at the job status
            val jobEntity = jobRepository.findById(jobId)
            val isRetry = jobEntity.isPresent && jobEntity.get().status == jobStatus.FAILED

            if (isRetry) {
                // This is a retry and it failed again - mark as FAILED (will go to DLT)
                log.error("Job $jobId RETRY FAILED - will be sent to DLT")
                jobStatusService.markJobAsFailed(jobId, job, "Retry failed: ${e.message}")
                // Rethrow to send to DLT
                throw e
            } else {
                // First failure - mark as FAILED and trigger retry
                log.warn("Job $jobId FIRST FAILURE - marking as FAILED and will retry after 5 minutes")
                jobStatusService.markJobAsFailed(jobId, job, e.message ?: "Unknown error")
                // Rethrow to trigger Kafka retry mechanism (will retry after 5 minutes)
                throw e
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createJobIfNotExists(job: JobDTO): UUID? {
        // Use the event ID from Kafka as the job ID
        val jobId = job.id

        // Check if job exists
        log.debug("Checking if the job already exists for file: ${job.fileId}, version: ${job.version}")
        val existingJob = jobRepository.findById(jobId)
        log.debug(existingJob.toString())

        if (existingJob.isPresent) {
            log.debug("Job already exists")
            val jobEntity = existingJob.get()
            return if (jobEntity.status == jobStatus.PENDING) {
                log.warn("Resuming stuck job: ${jobEntity.jobid}")
                jobId
            } else {
                log.info("Job ${jobEntity.jobid} already ${jobEntity.status}. Skipping.")
                null
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
        )
        jobRepository.save(newJob)
        return jobId
    }

    fun processJob(job: JobDTO, jobId: UUID): MetadataDTO? {

        val fileToAnalyze = downloadFile(job.fileId, job.version)
        // Call downloadFile expect to receive an InputStream

        // Determine which AI client to use based on content type
        val clientToUse = selectAIClient(job.contentType)
        val clientName = if (clientToUse === pdfAnalysisChatClient) "PDF-specific (Claude)" else "Generic (Groq)"
        log.info("Processing job $jobId with $clientName client for contentType: ${job.contentType}")

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
            log.error("AI returned incomplete metadata for job $jobId: ${e.message}")
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

        return if (baseMimeType.equals("application/pdf", ignoreCase = true)) {
            log.info("Selected PDF-specific client (Groq Llama 3.3 70B) for content type: $contentType")
            pdfAnalysisChatClient
        } else {
            log.info("Selected generic client (Groq Llama 3.1 8B) for content type: $contentType")
            chatClient
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

            log.info("Published file-analyzed event for file ${job.fileId} (job $jobId) to Kafka")
        } catch (e: Exception) {
            log.error("Failed to publish file-analyzed event for job $jobId: ${e.message}", e)
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
        // Extract text based on content type
        val contenutoFile = if (contentType.startsWith("application/pdf", ignoreCase = true)) {
            extractTextFromPdf(inputStream)
        } else {
            inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }

        // Tronca il contenuto se troppo lungo (per evitare limiti di token AI)
        val truncatedContent = truncateContent(contenutoFile, maxContentChars)

        // Carica il template e sostituisci il placeholder
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
            log.info("Successfully extracted ${text.length} characters from PDF ($numPages pages)")
            text
        } catch (e: Exception) {
            log.error("Failed to extract text from PDF: ${e.message}", e)
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
        val truncationMarker = "\n\n[... CONTENT TRUNCATED - ${truncatedBytes} characters omitted ...]\n\n"

        log.info("Content truncated: original ${content.length} chars -> ${maxChars} chars (removed ${truncatedBytes} chars)")

        return beginning + truncationMarker + ending
    }

    /**
     * Attempts AI analysis with retry logic (max 5 attempts)
     * Throws AIAnalysisUnsupportedException if all attempts fail
     *
     * @param inputStream The file content to analyze
     * @param jobId The job identifier for logging
     * @param client The ChatClient to use (either chatClient or pdfAnalysisChatClient)
     * @param contentType The MIME type of the file
     */
    private fun analyzeUploadWithRetry(inputStream: InputStream, jobId: UUID, client: ChatClient, contentType: String): MetadataDTO {
        val maxRetries = 5
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                log.info("AI analysis attempt $attempt/$maxRetries for job $jobId")

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
                log.info("AI analysis succeeded on attempt $attempt for job $jobId")
                return result

            } catch (e: Exception) {
                lastException = e
                log.warn("AI analysis attempt $attempt failed for job $jobId: ${e.message}")

                if (attempt < maxRetries) {
                    // Wait before retry with exponential backoff (1s, 2s, 3s, 4s, 5s max)
                    val delayMs = (1000L * attempt).coerceAtMost(5000L)
                    log.info("Retrying in ${delayMs}ms...")
                    Thread.sleep(delayMs)
                }
            }
        }

        // All 5 attempts failed - throw exception to mark as UNSUPPORTED
        log.error("AI analysis failed after $maxRetries attempts for job $jobId", lastException)
        throw AIAnalysisUnsupportedException(
            "AI analysis not supported for this file after $maxRetries attempts",
            lastException
        )
    }


}

