package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.entities.DeadLetterEvent
import g11.aiprocessingservice.repositories.DeadLetterEventRepository
import g11.aiprocessingservice.services.DeadLetterService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeadLetterServiceTest {

    private val repository = mockk<DeadLetterEventRepository>(relaxed = true)
    private val service = DeadLetterService(repository)

    @Test
    fun `persists the dead letter, decoding byte-array exception headers to text`() {
        val captured = slot<DeadLetterEvent>()
        every { repository.save(capture(captured)) } answers { firstArg() }

        service.record(
            originalTopic = "file-analyzed",
            payload = """{"fileId":"abc"}""",
            exceptionType = "java.lang.IllegalStateException".toByteArray(StandardCharsets.UTF_8),
            exceptionMessage = "boom".toByteArray(StandardCharsets.UTF_8),
        )

        verify { repository.save(any()) }
        val saved = captured.captured
        assertEquals("file-analyzed", saved.originalTopic)
        assertEquals("""{"fileId":"abc"}""", saved.payload)
        assertEquals("java.lang.IllegalStateException", saved.exceptionType)
        assertEquals("boom", saved.exceptionMessage)
    }

    @Test
    fun `tolerates missing exception headers`() {
        val captured = slot<DeadLetterEvent>()
        every { repository.save(capture(captured)) } answers { firstArg() }

        // Disambiguate from the String? overload: this exercises the Kafka byte[] header path.
        service.record("file-outbox", "payload", null as ByteArray?, null as ByteArray?)

        assertNull(captured.captured.exceptionType)
        assertNull(captured.captured.exceptionMessage)
    }

    @Test
    fun `persists a dead letter from already-decoded exception details`() {
        val captured = slot<DeadLetterEvent>()
        every { repository.save(capture(captured)) } answers { firstArg() }

        // The terminal user-retry path supplies decoded strings rather than Kafka byte[] headers.
        service.record(
            originalTopic = "file-outbox",
            payload = """{"jobId":"abc"}""",
            exceptionType = "java.lang.IllegalStateException",
            exceptionMessage = "boom",
        )

        val saved = captured.captured
        assertEquals("file-outbox", saved.originalTopic)
        assertEquals("""{"jobId":"abc"}""", saved.payload)
        assertEquals("java.lang.IllegalStateException", saved.exceptionType)
        assertEquals("boom", saved.exceptionMessage)
    }
}
