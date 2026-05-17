package g11.tusuploaderservice.integration.services

import g11.tusuploaderservice.dto.UploadDTO
import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.dto.UserDTO
import g11.tusuploaderservice.dto.UserRole
import g11.tusuploaderservice.repositories.FilesOutboxEventsRepository
import g11.tusuploaderservice.services.TusService
import g11.tusuploaderservice.services.FileService
import g11.tusuploaderservice.services.AuthService
import g11.tusuploaderservice.services.StorageService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.security.MessageDigest
import java.util.*
import g11.tusuploaderservice.integration.IntegrationTest
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileVersion
import org.mockito.Mockito.mock
import org.springframework.kafka.test.context.EmbeddedKafka
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.Duration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Test di integrazione end-to-end per verificare il pattern Transactional Outbox con Kafka.
 *
 * Questo test:
 * - Verifica che gli eventi vengano salvati nella tabella outbox
 * - Simula Debezium leggendo dalla tabella outbox e inviando a Kafka Embedded
 * - Verifica che i messaggi possano essere consumati da Kafka
 *
 * Nota: Questo test simula il comportamento di Debezium senza avviare Debezium reale,
 * poiché Debezium richiede PostgreSQL con CDC abilitato (non disponibile con H2 in-memory).
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = [
    "spring.task.scheduling.enabled=false",
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "kafka.topic.file-upload=file-outbox"
])
@EmbeddedKafka(
    partitions = 1,
    topics = ["file-outbox"],
    brokerProperties = ["listeners=PLAINTEXT://localhost:9092", "port=9092"]
)
class DebeziumKafkaIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var tusService: TusService

    @Autowired
    private lateinit var outboxRepository: FilesOutboxEventsRepository

    @MockitoBean
    private lateinit var fileService: FileService

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var storageService: StorageService

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var consumer: KafkaConsumer<String, String>
    private lateinit var producer: KafkaProducer<String, String>

    @org.springframework.beans.factory.annotation.Value("\${spring.embedded.kafka.brokers}")
    private lateinit var embeddedKafkaBrokers: String

    // Helper function to avoid Kotlin null issues with Mockito any()
    private fun <T> anyObject(): T {
        ArgumentMatchers.any<T>()
        return null as T
    }

    @BeforeEach
    fun setupKafka() {
        // Setup Kafka producer per simulare Debezium
        val producerProps = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBrokers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
        }
        producer = KafkaProducer(producerProps)

        // Setup Kafka consumer
        val consumerProps = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBrokers)
            put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-${UUID.randomUUID()}")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
        }
        consumer = KafkaConsumer(consumerProps)
        consumer.subscribe(listOf("file-outbox"))

        println("✓ Kafka producer and consumer configured")
        println("  Broker: $embeddedKafkaBrokers")
    }

    @AfterEach
    fun cleanupKafka() {
        if (::consumer.isInitialized) consumer.close()
        if (::producer.isInitialized) producer.close()
    }

    @Test
    fun `should save event to outbox and send to Kafka simulating Debezium behavior`() {
        println("=".repeat(80))
        println("TEST: Transactional Outbox + Kafka Integration (Simulating Debezium)")
        println("=".repeat(80))

        // Setup security context
        val userId = UUID.randomUUID()
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("preferred_username", "testuser")
            .claim("realm_access", mapOf("roles" to listOf("admin")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val authorities = listOf(SimpleGrantedAuthority("ROLE_admin"))
        val authentication = JwtAuthenticationToken(jwt, authorities)
        SecurityContextHolder.getContext().authentication = authentication

        println("✓ Security context configured")

        val fileUuid = UUID.randomUUID()
        val content = "Test content for Debezium-Kafka integration".toByteArray()
        val uploadLength = content.size.toLong()
        val filename = "debezium_kafka_test.txt"
        val contentType = "text/plain"

        // Mock external services
        val mockUserDTO = UserDTO(
            id = userId,
            fullName = "Test User",
            email = "test@example.com",
            organization = "Test Org",
            department = null,
            role = UserRole.ROLE_admin
        )
        `when`(authService.getAccessToken()).thenReturn("mock-token")
        `when`(fileService.getUserDetails("mock-token")).thenReturn(mockUserDTO)

        val mockFile = mock(File::class.java)
        `when`(mockFile.uuid).thenReturn(fileUuid)
        `when`(fileService.createFile(anyObject(), anyObject())).thenReturn(mockFile)

        val mockFileVersion = mock(FileVersion::class.java)
        `when`(mockFileVersion.id).thenReturn(1L)
        `when`(mockFileVersion.version).thenReturn(1L)
        `when`(mockFileVersion.filename).thenReturn(filename)
        `when`(mockFileVersion.contentType).thenReturn(contentType)
        `when`(mockFileVersion.metadata).thenReturn(emptyMap())
        `when`(fileService.createFileVersion(anyObject(), anyObject())).thenReturn(mockFileVersion)

        `when`(storageService.putObject(anyObject())).thenReturn("mock-object-key")

        // STEP 1: Create upload - this saves event to outbox table
        println("\n[STEP 1] Creating upload (saves event to outbox)...")
        val created = tusService.createUploadWithChunk(
            UploadDTO(
                uploadLength = uploadLength,
                filename = filename,
                fileUuid = fileUuid,
                sharingScope = SharingScope.PERSONAL,
                checksum = MessageDigest.getInstance("MD5").digest("debezium".toByteArray())
                    .joinToString("") { "%02x".format(it) },
            ),
            content
        )

        assertNotNull(created.id, "Upload should be created")
        println("✓ Upload created with ID: ${created.id}")

        // STEP 2: Verify event is saved in outbox table
        println("\n[STEP 2] Verifying event in outbox table...")
        val outboxEvents = outboxRepository.findAll()
        val fileInsertedEvent = outboxEvents.findLast { it.eventType == "FILE_INSERTED" }

        assertNotNull(fileInsertedEvent, "Should find FILE_INSERTED event in outbox")
        println("✓ Found FILE_INSERTED event with ID: ${fileInsertedEvent!!.id}")

        val payload = fileInsertedEvent.payload
        assertEquals(fileUuid.toString(), payload["fileId"].toString())
        assertEquals(userId.toString(), payload["ownerUserId"].toString())
        assertEquals(filename, payload["filename"].toString())
        println("✓ Event payload verified in outbox table")

        // STEP 3: Simulate Debezium - read from outbox and send to Kafka
        println("\n[STEP 3] Simulating Debezium: Reading from outbox and sending to Kafka...")

        // Crea il messaggio Debezium CDC format (dopo ExtractNewRecordState)
        val debeziumMessage = mapOf(
            "id" to fileInsertedEvent.id.toString(),
            "event_type" to fileInsertedEvent.eventType,
            "payload" to mapOf(
                "fileId" to payload["fileId"].toString(),
                "fileVersionId" to payload["fileVersionId"],
                "version" to payload["version"],
                "ownerUserId" to payload["ownerUserId"].toString(),
                "filename" to payload["filename"].toString(),
                "contentType" to payload["contentType"].toString(),
                "metadata" to payload["metadata"]
            ),
            "created_at" to fileInsertedEvent.createdAt.time
        )

        val messageJson = objectMapper.writeValueAsString(debeziumMessage)
        val record = ProducerRecord<String, String>("file-outbox", fileUuid.toString(), messageJson)

        val sendResult = producer.send(record).get() // Blocking send
        println("✓ Message sent to Kafka topic '${sendResult.topic()}' at offset ${sendResult.offset()}")
        println("  Message: ${messageJson.take(150)}...")

        // STEP 4: Consume message from Kafka and verify
        println("\n[STEP 4] Consuming message from Kafka...")

        var messageReceived = false
        var attempts = 0
        val maxAttempts = 10

        while (!messageReceived && attempts < maxAttempts) {
            val records = consumer.poll(Duration.ofSeconds(1))

            if (records.count() > 0) {
                println("✓ Received ${records.count()} message(s) from Kafka")

                for (kafkaRecord in records) {
                    println("  - Topic: ${kafkaRecord.topic()}")
                    println("  - Key: ${kafkaRecord.key()}")
                    println("  - Value: ${kafkaRecord.value().take(200)}...")

                    try {
                        val receivedMessage = objectMapper.readTree(kafkaRecord.value())
                        val receivedEventType = receivedMessage.get("event_type")?.asText()
                        val receivedPayload = receivedMessage.get("payload")
                        val receivedFileId = receivedPayload?.get("fileId")?.asText()

                        if (receivedEventType == "FILE_INSERTED" && receivedFileId == fileUuid.toString()) {
                            messageReceived = true

                            // Verify all fields
                            assertEquals("FILE_INSERTED", receivedEventType)
                            assertEquals(fileUuid.toString(), receivedFileId)
                            assertEquals("1", receivedPayload.get("fileVersionId")?.asText())
                            assertEquals(1, receivedPayload.get("version")?.asInt())
                            assertEquals(userId.toString(), receivedPayload.get("ownerUserId")?.asText())
                            assertEquals(filename, receivedPayload.get("filename")?.asText())
                            assertEquals(contentType, receivedPayload.get("contentType")?.asText())

                            println("\n✓ Message verified successfully!")
                            println("  ✓ Event type: $receivedEventType")
                            println("  ✓ File ID: $receivedFileId")
                            println("  ✓ Filename: ${receivedPayload.get("filename")?.asText()}")
                            break
                        }
                    } catch (e: Exception) {
                        println("  ⚠ Error parsing message: ${e.message}")
                    }
                }
            }
            attempts++
        }

        assertTrue(messageReceived,
            "Should receive Debezium CDC message from Kafka within $maxAttempts seconds")

        println("\n" + "=".repeat(80))
        println("✓ TEST PASSED: Complete Debezium-Kafka Integration Working!")
        println("  ✓ Event saved in outbox table (Transactional Outbox Pattern)")
        println("  ✓ Event read from outbox and sent to Kafka (simulating Debezium)")
        println("  ✓ Message consumed from Kafka and verified")
        println("  ✓ This simulates the complete Debezium CDC pipeline")
        println("=".repeat(80))

        // Cleanup
        SecurityContextHolder.clearContext()
    }
}
