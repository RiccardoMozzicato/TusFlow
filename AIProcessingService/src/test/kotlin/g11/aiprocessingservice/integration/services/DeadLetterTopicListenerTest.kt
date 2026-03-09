package g11.aiprocessingservice.integration.services

import g11.aiprocessingservice.IntegrationTest
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Integration tests for DeadLetterTopicListener.
 *
 * Tests verify that:
 * 1. DLT consumer receives messages sent to file-outbox-dlt topic
 * 2. Messages are correctly parsed and logged
 * 3. Malformed messages are handled gracefully
 * 4. Multiple messages are processed sequentially
 */
@SpringBootTest
@DirtiesContext
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["file-outbox", "file-outbox-dlt"],
    brokerProperties = ["listeners=PLAINTEXT://localhost:9094", "port=9094"]
)
@TestPropertySource(properties = [
    "kafka.bootstrap-servers=localhost:9094",
    "kafka.consumer.bootstrap-servers=localhost:9094",
    "kafka.consumer.group-id=test-group",
    "kafka.consumer.auto-offset-reset=earliest",
    "kafka.producer.bootstrap-servers=localhost:9094"
])
@ExtendWith(OutputCaptureExtension::class)
class DeadLetterTopicListenerTest : IntegrationTest() {

    private lateinit var producer: KafkaProducer<String, String>

    @BeforeEach
    fun setup() {
        // Setup Kafka producer per inviare messaggi al DLT
        val producerProps = mapOf(
            "bootstrap.servers" to "localhost:9094",
            "key.serializer" to StringSerializer::class.java.name,
            "value.serializer" to StringSerializer::class.java.name,
            "acks" to "all",
            "retries" to "3"
        )
        producer = KafkaProducer(producerProps)

        println("✓ DLT Test: Kafka producer configured for localhost:9094")
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
    fun `test DLT listener receives and logs valid message`(output: CapturedOutput) {
        val fileId = UUID.randomUUID()
        val filename = "failed-document.pdf"
        val version = "1"

        // Crea un messaggio valido che simula un job fallito
        val dltMessage = """
            {
                "fileId": "$fileId",
                "version": $version,
                "filename": "$filename",
                "contentType": "application/pdf",
                "ownerUserId": "${UUID.randomUUID()}",
                "fileVersionId": "${UUID.randomUUID()}"
            }
        """.trimIndent()

        println("📤 Sending message to DLT topic 'file-outbox-dlt'")
        println("   File ID: $fileId")
        println("   Filename: $filename")

        // Invia il messaggio al DLT topic
        val record = ProducerRecord<String, String>("file-outbox-dlt", fileId.toString(), dltMessage)
        val sendResult = producer.send(record)
        val metadata = sendResult.get(10, TimeUnit.SECONDS)

        println("✓ Message sent successfully to DLT at partition ${metadata.partition()}, offset ${metadata.offset()}")

        // Attendi che il listener elabori il messaggio
        println("⏳ Waiting for DLT listener to consume message...")
        Thread.sleep(5000) // 5 secondi per dare tempo al listener

        // Verifica che il messaggio sia stato loggato correttamente
        val capturedOutput = output.toString()

        assert(capturedOutput.contains("MESSAGE IN DEAD LETTER TOPIC")) {
            "Expected DLT header message in logs"
        }

        assert(capturedOutput.contains("Message received in DLT after max retries")) {
            "Expected DLT message notification in logs"
        }

        assert(capturedOutput.contains(fileId.toString())) {
            "Expected file ID in logs"
        }

        assert(capturedOutput.contains(filename)) {
            "Expected filename in logs"
        }

        assert(capturedOutput.contains("Job FAILED after maximum retries")) {
            "Expected failure message in logs"
        }

        assert(capturedOutput.contains("MANUAL INTERVENTION")) {
            "Expected manual intervention warning in logs"
        }

        println("✓ DLT message logged correctly")
        println("   - File ID found in logs: $fileId")
        println("   - Filename found in logs: $filename")
        println("\n✅ TEST PASSED: DLT listener correctly processed valid message")
    }

    @Test
    fun `test DLT listener handles malformed JSON message`(output: CapturedOutput) {
        println("📤 Sending malformed JSON to DLT topic")

        // Messaggio JSON malformato
        val malformedMessage = """
            {
                "fileId": "not-a-valid-json
                "missing": "closing brace"
        """.trimIndent()

        // Invia il messaggio malformato al DLT
        val record = ProducerRecord<String, String>("file-outbox-dlt", "malformed-key", malformedMessage)
        producer.send(record).get(10, TimeUnit.SECONDS)

        println("✓ Malformed message sent to DLT")
        println("⏳ Waiting for DLT listener to process...")
        Thread.sleep(5000)

        // Verifica che il listener abbia gestito l'errore senza crashare
        val capturedOutput = output.toString()

        assert(capturedOutput.contains("MESSAGE IN DEAD LETTER TOPIC")) {
            "Expected DLT header even for malformed messages"
        }

        assert(capturedOutput.contains("Failed to parse DLT message") ||
               capturedOutput.contains("Raw message")) {
            "Expected error handling for malformed JSON"
        }

        println("✓ DLT listener handled malformed message gracefully")
        println("\n✅ TEST PASSED: DLT listener doesn't crash on malformed JSON")
    }

    @Test
    fun `test DLT listener handles message with missing fields`(output: CapturedOutput) {
        println("📤 Sending message with missing fields to DLT topic")

        // Messaggio JSON valido ma con campi mancanti
        val incompleteMessage = """
            {
                "someField": "someValue",
                "anotherField": 123
            }
        """.trimIndent()

        val record = ProducerRecord<String, String>("file-outbox-dlt", "incomplete-key", incompleteMessage)
        producer.send(record).get(10, TimeUnit.SECONDS)

        println("✓ Incomplete message sent to DLT")
        println("⏳ Waiting for DLT listener to process...")
        Thread.sleep(5000)

        // Verifica che il listener abbia processato il messaggio
        val capturedOutput = output.toString()

        assert(capturedOutput.contains("MESSAGE IN DEAD LETTER TOPIC")) {
            "Expected DLT header for incomplete messages"
        }

        assert(capturedOutput.contains("Message received in DLT")) {
            "Expected message processing notification"
        }

        // Il listener dovrebbe gestire i campi null senza crashare
        assert(capturedOutput.contains("File ID: null") ||
               capturedOutput.contains("MESSAGE IN DEAD LETTER TOPIC")) {
            "Expected graceful handling of missing fields"
        }

        println("✓ DLT listener handled incomplete message without crashing")
        println("\n✅ TEST PASSED: DLT listener handles missing fields gracefully")
    }

    @Test
    fun `test DLT listener processes multiple messages in sequence`(output: CapturedOutput) {
        println("📤 Sending multiple messages to DLT topic")

        val messages = (1..3).map { index ->
            val fileId = UUID.randomUUID()
            val message = """
                {
                    "fileId": "$fileId",
                    "version": $index,
                    "filename": "failed-document-$index.pdf",
                    "contentType": "application/pdf",
                    "ownerUserId": "${UUID.randomUUID()}",
                    "fileVersionId": "${UUID.randomUUID()}"
                }
            """.trimIndent()

            Pair(fileId, message)
        }

        // Invia tutti i messaggi
        messages.forEach { (fileId, message) ->
            println("   Sending message for File ID: $fileId")
            val record = ProducerRecord<String, String>("file-outbox-dlt", fileId.toString(), message)
            producer.send(record).get(10, TimeUnit.SECONDS)
        }

        println("✓ All messages sent to DLT")
        println("⏳ Waiting for DLT listener to process all messages...")
        Thread.sleep(8000) // Più tempo per processare multipli messaggi

        // Verifica che tutti i messaggi siano stati loggati
        val capturedOutput = output.toString()

        messages.forEach { (fileId, _) ->
            assert(capturedOutput.contains(fileId.toString())) {
                "Expected file ID $fileId in logs"
            }
            println("   ✓ Found File ID in logs: $fileId")
        }

        // Verifica che ci siano 3 occorrenze del header DLT
        val dltHeaderCount = capturedOutput.split("MESSAGE IN DEAD LETTER TOPIC").size - 1
        assert(dltHeaderCount >= 3) {
            "Expected at least 3 DLT messages, found $dltHeaderCount"
        }

        println("✓ All $dltHeaderCount messages processed by DLT listener")
        println("\n✅ TEST PASSED: DLT listener successfully processed multiple messages")
    }

    @Test
    fun `test DLT listener logs all required information`(output: CapturedOutput) {
        val fileId = UUID.randomUUID()
        val filename = "detailed-test.txt"
        val version = "5"
        val ownerId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        val detailedMessage = """
            {
                "fileId": "$fileId",
                "version": $version,
                "filename": "$filename",
                "contentType": "text/plain",
                "ownerUserId": "$ownerId",
                "fileVersionId": "$versionId"
            }
        """.trimIndent()

        println("📤 Sending detailed message to DLT for logging verification")
        println("   File ID: $fileId")
        println("   Filename: $filename")
        println("   Version: $version")

        val record = ProducerRecord<String, String>("file-outbox-dlt", fileId.toString(), detailedMessage)
        producer.send(record).get(10, TimeUnit.SECONDS)

        Thread.sleep(5000)

        val capturedOutput = output.toString()

        // Verifica che tutte le informazioni importanti siano loggate
        val requiredElements = listOf(
            "MESSAGE IN DEAD LETTER TOPIC" to "DLT header",
            fileId.toString() to "File ID",
            filename to "Filename",
            version to "Version",
            "MANUAL INTERVENTION" to "Manual intervention warning",
            "Possible actions" to "Action suggestions",
            "jobs' table" to "Database reference"
        )

        println("\n🔍 Verifying all required log elements:")
        requiredElements.forEach { (element, description) ->
            assert(capturedOutput.contains(element)) {
                "Expected '$description' ($element) in logs"
            }
            println("   ✓ Found: $description")
        }

        println("\n✅ TEST PASSED: DLT listener logs all required information")
    }

    @Test
    fun `test DLT listener handles empty message`(output: CapturedOutput) {
        println("📤 Sending empty message to DLT topic")

        val emptyMessage = ""

        val record = ProducerRecord<String, String>("file-outbox-dlt", "empty-key", emptyMessage)
        producer.send(record).get(10, TimeUnit.SECONDS)

        println("✓ Empty message sent to DLT")
        println("⏳ Waiting for DLT listener to process...")
        Thread.sleep(5000)

        // Verifica che il listener abbia gestito il messaggio vuoto
        val capturedOutput = output.toString()

        assert(capturedOutput.contains("MESSAGE IN DEAD LETTER TOPIC") ||
               capturedOutput.contains("Failed to parse DLT message")) {
            "Expected DLT processing or error handling for empty message"
        }

        println("✓ DLT listener handled empty message without crashing")
        println("\n✅ TEST PASSED: DLT listener handles empty messages gracefully")
    }

    @Test
    fun `test DLT listener handles non-JSON message`(output: CapturedOutput) {
        println("📤 Sending non-JSON message to DLT topic")

        val nonJsonMessage = "This is not a JSON message at all, just plain text"

        val record = ProducerRecord<String, String>("file-outbox-dlt", "non-json-key", nonJsonMessage)
        producer.send(record).get(10, TimeUnit.SECONDS)

        println("✓ Non-JSON message sent to DLT")
        println("⏳ Waiting for DLT listener to process...")
        Thread.sleep(5000)

        val capturedOutput = output.toString()

        // Il listener dovrebbe ricevere il messaggio e tentare di parsarlo
        assert(capturedOutput.contains("MESSAGE IN DEAD LETTER TOPIC")) {
            "Expected DLT header even for non-JSON messages"
        }

        assert(capturedOutput.contains("Failed to parse DLT message") ||
               capturedOutput.contains("Raw message") ||
               capturedOutput.contains(nonJsonMessage)) {
            "Expected error handling or raw message logging for non-JSON"
        }

        println("✓ DLT listener handled non-JSON message gracefully")
        println("\n✅ TEST PASSED: DLT listener processes non-JSON messages without crashing")
    }
}

