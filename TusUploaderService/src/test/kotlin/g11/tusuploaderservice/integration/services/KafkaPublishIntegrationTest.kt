package g11.tusuploaderservice.integration.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.dto.UploadDTO
import g11.tusuploaderservice.dto.UserDTO
import g11.tusuploaderservice.dto.UserRole
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileVersion
import g11.tusuploaderservice.integration.IntegrationTest
import g11.tusuploaderservice.services.AuthService
import g11.tusuploaderservice.services.FileService
import g11.tusuploaderservice.services.StorageService
import g11.tusuploaderservice.services.TusService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.*

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
    brokerProperties = ["listeners=PLAINTEXT://localhost:9093", "port=9093"]
)
class KafkaPublishIntegrationTest : IntegrationTest() {

    @Autowired
    private lateinit var tusService: TusService

    @MockitoBean
    private lateinit var fileService: FileService

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var storageService: StorageService

    @Value("\${spring.embedded.kafka.brokers}")
    private lateinit var embeddedKafkaBrokers: String

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var consumer: KafkaConsumer<String, String>

    // Helper to avoid Kotlin null issues with Mockito any()
    private fun <T> anyObject(): T {
        ArgumentMatchers.any<T>()
        return null as T
    }

    @BeforeEach
    fun setupKafka() {
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
    }

    @AfterEach
    fun cleanupKafka() {
        if (::consumer.isInitialized) consumer.close()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `should publish FILE_INSERTED event directly to Kafka when upload completes`() {
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

        val fileUuid = UUID.randomUUID()
        val content = "Test content for Kafka direct publish".toByteArray()
        val uploadLength = content.size.toLong()
        val filename = "kafka_direct_test.txt"
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

        // Trigger the upload — TusService publishes directly to Kafka via kafkaTemplate
        val created = tusService.createUploadWithChunk(
            UploadDTO(
                uploadLength = uploadLength,
                filename = filename,
                fileUuid = fileUuid,
                sharingScope = SharingScope.PERSONAL,
                checksum = MessageDigest.getInstance("MD5").digest("kafkatest".toByteArray())
                    .joinToString("") { "%02x".format(it) }
            ),
            content
        )

        assertNotNull(created.id, "Upload should be created")

        // Consume the message from Kafka and verify FILE_INSERTED event
        var messageReceived = false
        var attempts = 0
        val maxAttempts = 10

        while (!messageReceived && attempts < maxAttempts) {
            val records = consumer.poll(Duration.ofSeconds(1))

            for (kafkaRecord in records) {
                try {
                    val receivedMessage = objectMapper.readTree(kafkaRecord.value())
                    val receivedEventType = receivedMessage.get("event_type")?.asText()

                    // payload is a JSON-encoded string — parse it
                    val payloadText = receivedMessage.get("payload")?.asText() ?: continue
                    val payloadTree = objectMapper.readTree(payloadText)
                    val receivedFileId = payloadTree.get("fileId")?.asText()

                    if (receivedEventType == "FILE_INSERTED" && receivedFileId == fileUuid.toString()) {
                        messageReceived = true

                        assertEquals("FILE_INSERTED", receivedEventType)
                        assertEquals(fileUuid.toString(), receivedFileId)
                        assertNotNull(receivedMessage.get("id"), "Envelope id should be present")

                        break
                    }
                } catch (e: Exception) {
                    // skip malformed messages
                }
            }
            attempts++
        }

        assertTrue(
            messageReceived,
            "Should receive FILE_INSERTED event from Kafka within $maxAttempts seconds"
        )
    }
}
