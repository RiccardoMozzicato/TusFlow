package g11.aiprocessingservice.integration.services

import com.ninjasquad.springmockk.MockkBean
import g11.aiprocessingservice.IntegrationTest
import g11.aiprocessingservice.entities.DeadLetterEvent
import g11.aiprocessingservice.repositories.DeadLetterEventRepository
import g11.aiprocessingservice.services.AuthServiceInterface
import g11.aiprocessingservice.services.JobStatusServiceInterface
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals

/**
 * Verifies the dead-letter wiring end to end: a record on a `-dlt` topic is drained into the
 * dead_letter_events audit table by the per-pipeline @DltHandler. We exercise EmbeddingProcessor's
 * handler (registered on file-analyzed-dlt), which still audits on Kafka exhaustion — the embedding
 * pipeline has no user-retry phase. (The file-outbox handler is now log-only: the file-analysis
 * pipeline audits only when the user's retries are also exhausted; see MetadataProcessor… tests.)
 * External collaborators are mocked so the context loads.
 */
@SpringBootTest
@DirtiesContext
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["file-outbox", "file-analyzed", "file-analyzed-dlt", "embeddings-batch"],
    bootstrapServersProperty = "kafka.bootstrap-servers",
)
class DeadLetterHandlerTest : IntegrationTest() {

    @MockkBean
    private lateinit var authService: AuthServiceInterface

    @MockkBean(name = "TusUploaderService")
    private lateinit var fileServiceClient: RestClient

    @MockkBean(name = "chatClient")
    private lateinit var chatClient: ChatClient

    @MockkBean(name = "pdfAnalysisChatClient")
    private lateinit var pdfAnalysisChatClient: ChatClient

    @MockkBean(name = "imageChatClient")
    private lateinit var imageChatClient: ChatClient

    @MockkBean
    private lateinit var jobStatusService: JobStatusServiceInterface

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var deadLetterRepository: DeadLetterEventRepository

    private lateinit var producer: KafkaProducer<String, String>

    @BeforeEach
    fun setup() {
        deadLetterRepository.deleteAll()
        producer = KafkaProducer(
            mapOf(
                "bootstrap.servers" to embeddedKafka.brokersAsString,
                "key.serializer" to StringSerializer::class.java.name,
                "value.serializer" to StringSerializer::class.java.name,
                "acks" to "all",
            ),
        )
    }

    @AfterEach
    fun cleanup() {
        producer.close()
    }

    @Test
    fun `a record on file-analyzed-dlt is drained into the audit table`() {
        val payload = """{"fileId":"11111111-1111-1111-1111-111111111111","filename":"broken.pdf"}"""

        producer.send(ProducerRecord("file-analyzed-dlt", "key", payload)).get()

        val saved = awaitSingleDeadLetter()
        assertEquals("file-analyzed", saved.originalTopic)
        assertEquals(payload, saved.payload)
    }

    @Test
    fun `a non-JSON record is still persisted verbatim`() {
        val payload = "this is not json"

        producer.send(ProducerRecord("file-analyzed-dlt", "key", payload)).get()

        assertEquals(payload, awaitSingleDeadLetter().payload)
    }

    // The @DltHandler consumes asynchronously, so poll the audit table until the row appears.
    private fun awaitSingleDeadLetter(): DeadLetterEvent {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            deadLetterRepository.findAll().firstOrNull()?.let { return it }
            Thread.sleep(200)
        }
        throw AssertionError("No dead_letter_events row was persisted within the timeout")
    }
}
