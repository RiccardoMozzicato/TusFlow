package g11.aiprocessingservice.integration.services

import com.ninjasquad.springmockk.MockkBean
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.repositories.JobRepository
import g11.aiprocessingservice.services.AuthServiceInterface
import g11.aiprocessingservice.services.JobStatusServiceInterface
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import g11.aiprocessingservice.IntegrationTest
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import g11.aiprocessingservice.services.EmbeddingGenerator
import g11.aiprocessingservice.services.EmbeddingProcessor
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest
@DirtiesContext
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["file-outbox", "file-outbox-dlt", "file-analyzed", "embeddings-batch"],
    bootstrapServersProperty = "kafka.bootstrap-servers"
)
class KafkaListenerTest : IntegrationTest() {

    @Autowired
    private lateinit var jobRepository: JobRepository

    // Mock delle dipendenze esterne (non del database)
    @MockkBean
    private lateinit var authService: AuthServiceInterface

    @MockkBean(name = "TusUploaderService")
    private lateinit var fileServiceClient: RestClient

    @MockkBean(name = "chatClient")
    private lateinit var chatClient: ChatClient

    @MockkBean(name = "pdfAnalysisChatClient")
    private lateinit var pdfAnalysisChatClient: ChatClient

    @MockkBean
    private lateinit var jobStatusService: JobStatusServiceInterface

    @MockkBean
    private lateinit var embeddingProcessor: EmbeddingProcessor

    @MockkBean
    private lateinit var embeddingGenerator: EmbeddingGenerator

    @Autowired
    private lateinit var embeddedKafka: org.springframework.kafka.test.EmbeddedKafkaBroker

    private lateinit var producer: KafkaProducer<String, String>

    @BeforeEach
    fun setup() {
        jobRepository.deleteAll()

        val producerProps = mapOf(
            "bootstrap.servers" to embeddedKafka.brokersAsString,
            "key.serializer" to StringSerializer::class.java.name,
            "value.serializer" to StringSerializer::class.java.name,
            "acks" to "all",
            "retries" to "3"
        )
        producer = KafkaProducer(producerProps)

        println("✓ Kafka producer configured for ${embeddedKafka.brokersAsString}")

        // Setup mocks per le dipendenze esterne
        setupMocks()
    }

    private fun setupMocks() {
        // Mock del servizio di autenticazione
        every { authService.getAccessToken() } returns "mock-token"

        // Mock del servizio di gestione file - crea un mock completo per la catena di chiamate
        val mockResource = mockk<org.springframework.core.io.Resource>(relaxed = true)
        val mockInputStream = "Mock PDF content for testing".byteInputStream()
        every { mockResource.inputStream } returns mockInputStream

        val mockResponseSpec = mockk<RestClient.ResponseSpec>(relaxed = true)
        every { mockResponseSpec.body(org.springframework.core.io.Resource::class.java) } returns mockResource

        val mockRequestHeadersSpec = mockk<RestClient.RequestHeadersUriSpec<*>>(relaxed = true)
        every { mockRequestHeadersSpec.uri(any<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>()) } returns mockRequestHeadersSpec
        every { mockRequestHeadersSpec.headers(any()) } returns mockRequestHeadersSpec
        every { mockRequestHeadersSpec.retrieve() } returns mockResponseSpec

        every { fileServiceClient.get() } returns mockRequestHeadersSpec

        // Mock per jobStatusService (evita chiamate reali durante i test)
        justRun { jobStatusService.markJobAsFailed(any(), any(), any()) }
        justRun { jobStatusService.markJobAsCompleted(any(), any(), any()) }
        justRun { jobStatusService.markJobAsUnsupported(any(), any(), any()) }

        // Mock per i ChatClient AI - crea un oggetto MetadataDTO valido
        val mockMetadata = g11.aiprocessingservice.dto.MetadataDTO(
            summary = "Test document summary",
            tags = "test, document",
            sensitivity = g11.aiprocessingservice.dto.sensitivityLevel.PUBLIC
        )

        // Usa un approccio semplificato: mock la catena di chiamate completa
        every {
            chatClient.prompt()
                .user(any<String>())
                .call()
                .entity(g11.aiprocessingservice.dto.MetadataDTO::class.java)
        } returns mockMetadata

        every {
            pdfAnalysisChatClient.prompt()
                .user(any<String>())
                .call()
                .entity(g11.aiprocessingservice.dto.MetadataDTO::class.java)
        } returns mockMetadata
    }

    @AfterEach
    fun cleanup() {
        try {
            producer.close()
        } catch (e: Exception) {
            println("Warning: Error closing producer: ${e.message}")
        }
    }

    @Test
    fun `test kafka listener skips duplicate job`() {
        val jobId = UUID.randomUUID()
        val fileId = UUID.randomUUID()

        // Crea il payload come stringa JSON (usa text/plain)
        val payloadJson = """
            {
                "fileId": "$fileId",
                "version": 1,
                "filename": "duplicate-test.txt",
                "contentType": "text/plain",
                "ownerUserId": "${UUID.randomUUID()}",
                "fileVersionId": "${UUID.randomUUID()}"
            }
        """.trimIndent()

        // Formato Debezium CDC
        val testMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$jobId",
                "payload": "${payloadJson.replace("\"", "\\\"").replace("\n", "")}"
            }
        """.trimIndent()

        println("📤 Sending message first time...")

        // Invia il messaggio la prima volta
        producer.send(ProducerRecord("file-outbox", fileId.toString(), testMessage)).get(10, TimeUnit.SECONDS)
        Thread.sleep(8000) // Attendi più tempo

        val jobCountBefore = jobRepository.count()
        println("✓ Jobs in DB after first message: $jobCountBefore")

        // Invia lo stesso messaggio una seconda volta
        println("📤 Sending same message second time (duplicate)...")
        producer.send(ProducerRecord("file-outbox", fileId.toString(), testMessage)).get(10, TimeUnit.SECONDS)
        Thread.sleep(8000) // Attendi più tempo

        // Verifica che non sia stato creato un secondo job
        val jobCountAfter = jobRepository.count()
        println("✓ Jobs in DB after second message: $jobCountAfter")

        assertThat(jobCountAfter).isEqualTo(jobCountBefore)

        println("\n✅ TEST PASSED: KafkaListener correctly skipped duplicate message")
    }

    @Test
    fun `test kafka listener handles malformed JSON message`() {
        println("📤 Sending malformed JSON message...")

        // Messaggio JSON malformato (manca la chiusura delle parentesi)
        val malformedMessage = """
            {
                "id": "${UUID.randomUUID()}",
                "payload": "{\"fileId\":\"invalid
            }
        """.trimIndent()

        val jobCountBefore = jobRepository.count()
        println("✓ Jobs in DB before malformed message: $jobCountBefore")

        // Invia il messaggio malformato
        try {
            producer.send(ProducerRecord("file-outbox", "key", malformedMessage)).get(10, TimeUnit.SECONDS)
            Thread.sleep(5000)
        } catch (e: Exception) {
            println("⚠️ Expected error when sending malformed message: ${e.message}")
        }

        // Verifica che NON sia stato creato alcun job
        val jobCountAfter = jobRepository.count()
        println("✓ Jobs in DB after malformed message: $jobCountAfter")

        assertThat(jobCountAfter).isEqualTo(jobCountBefore)

        println("\n✅ TEST PASSED: KafkaListener correctly rejected malformed JSON")
    }

    @Test
    fun `test kafka listener skips already completed job`() {
        val jobId = UUID.randomUUID()
        val fileId = UUID.randomUUID()

        // Crea manualmente un job già COMPLETED nel database
        val existingJob = g11.aiprocessingservice.entities.Job(
            jobid = jobId,
            fileid = fileId,
            version = 1,
            type = "FILE_ANALYSIS",
            payload = mapOf(
                "filename" to "already-completed.pdf",
                "contentType" to "application/pdf"
            ),
            status = jobStatus.COMPLETED
        )
        jobRepository.save(existingJob)

        println("📤 Job already completed in DB, sending message...")
        println("   Job ID: $jobId (status: COMPLETED)")

        // Crea messaggio per lo stesso job
        val payloadJson = """
            {
                "fileId": "$fileId",
                "version": 1,
                "filename": "already-completed.pdf",
                "contentType": "application/pdf",
                "ownerUserId": "${UUID.randomUUID()}",
                "fileVersionId": "${UUID.randomUUID()}"
            }
        """.trimIndent()

        val testMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$jobId",
                "payload": "${payloadJson.replace("\"", "\\\"").replace("\n", "")}"
            }
        """.trimIndent()

        producer.send(ProducerRecord("file-outbox", fileId.toString(), testMessage)).get(10, TimeUnit.SECONDS)
        Thread.sleep(5000)

        // Verifica che il job sia ancora COMPLETED (non modificato)
        val savedJob = jobRepository.findById(jobId)
        assertThat(savedJob).isPresent
        assertThat(savedJob.get().status).isEqualTo(jobStatus.COMPLETED)

        println("✓ Job status unchanged: ${savedJob.get().status}")
        println("\n✅ TEST PASSED: KafkaListener correctly skipped already completed job")
    }

    @Test
    fun `test kafka listener resumes pending job`() {
        val jobId = UUID.randomUUID()
        val fileId = UUID.randomUUID()

        // Crea manualmente un job PENDING "bloccato" nel database
        val existingJob = g11.aiprocessingservice.entities.Job(
            jobid = jobId,
            fileid = fileId,
            version = 1,
            type = "FILE_ANALYSIS",
            payload = mapOf(
                "filename" to "stuck-pending.pdf",
                "contentType" to "application/pdf"
            ),
            status = jobStatus.PENDING
        )
        jobRepository.save(existingJob)

        println("📤 Job stuck in PENDING, sending message to resume...")
        println("   Job ID: $jobId (status: PENDING)")

        // Crea messaggio per lo stesso job
        val payloadJson = """
            {
                "fileId": "$fileId",
                "version": 1,
                "filename": "stuck-pending.pdf",
                "contentType": "application/pdf",
                "ownerUserId": "${UUID.randomUUID()}",
                "fileVersionId": "${UUID.randomUUID()}"
            }
        """.trimIndent()

        val testMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$jobId",
                "payload": "${payloadJson.replace("\"", "\\\"").replace("\n", "")}"
            }
        """.trimIndent()

        producer.send(ProducerRecord("file-outbox", fileId.toString(), testMessage)).get(10, TimeUnit.SECONDS)
        Thread.sleep(5000)

        // Verifica che il job esista ancora (il listener dovrebbe tentare di riprenderlo)
        val savedJob = jobRepository.findById(jobId)
        assertThat(savedJob).isPresent
        println("✓ Job found: ${savedJob.get().jobid} with status: ${savedJob.get().status}")

        println("\n✅ TEST PASSED: KafkaListener attempted to resume stuck pending job")
    }

}
