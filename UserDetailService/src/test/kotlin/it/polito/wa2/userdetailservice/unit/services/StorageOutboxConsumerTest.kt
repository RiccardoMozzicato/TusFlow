package it.polito.wa2.userdetailservice.unit.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import it.polito.wa2.userdetailservice.entities.ProcessedStorageEvent
import it.polito.wa2.userdetailservice.entities.User
import it.polito.wa2.userdetailservice.enums.UserRole
import it.polito.wa2.userdetailservice.metrics.UserDetailMetrics
import it.polito.wa2.userdetailservice.repositories.ProcessedStorageEventRepository
import it.polito.wa2.userdetailservice.repositories.UserRepository
import it.polito.wa2.userdetailservice.services.StorageOutboxConsumer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.support.Acknowledgment
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class StorageOutboxConsumerTest {

    private lateinit var userRepository: UserRepository
    private lateinit var processedStorageEventRepository: ProcessedStorageEventRepository
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var consumer: StorageOutboxConsumer

    private val objectMapper = jacksonObjectMapper()
    private val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")

    @BeforeEach
    fun setup() {
        userRepository = mockk(relaxed = true)
        processedStorageEventRepository = mockk(relaxed = true)
        acknowledgment = mockk(relaxed = true)
        consumer = StorageOutboxConsumer(userRepository, processedStorageEventRepository, mockk(relaxed = true), UserDetailMetrics(SimpleMeterRegistry()))
        // Default: events have never been processed before.
        every { processedStorageEventRepository.existsById(any()) } returns false
        // Generic save<S>(): S returns Object under a relaxed mock, which fails the call-site
        // cast to ProcessedStorageEvent. Return the saved entity instead.
        every { processedStorageEventRepository.save(any()) } answers { firstArg() }
    }

    private fun makeUser(storageUsed: Long = 0, storageQuota: Long? = null) = User(
        id = userId,
        fullName = "Mario Rossi",
        email = "mario.rossi@gmail.com",
        organization = "Polytechnic University of Turin",
        role = UserRole.ROLE_manager,
        department = "Engineering",
        storageQuota = storageQuota,
        storageUsed = storageUsed,
    )

    /**
     * Builds the JSON envelope produced by the Debezium outbox connector: event_type and id at the
     * root, plus a payload that may be either a nested object or a stringified JSON document.
     */
    private fun message(
        eventType: String?,
        ownerUserId: String? = userId.toString(),
        size: Long? = 100L,
        eventId: String? = UUID.randomUUID().toString(),
        payloadAsString: Boolean = false,
    ): String {
        val payload = buildMap<String, Any?> {
            if (ownerUserId != null) put("ownerUserId", ownerUserId)
            if (size != null) put("size", size)
        }
        val root = buildMap<String, Any?> {
            if (eventType != null) put("event_type", eventType)
            if (eventId != null) put("id", eventId)
            put("payload", if (payloadAsString) objectMapper.writeValueAsString(payload) else payload)
        }
        return objectMapper.writeValueAsString(root)
    }

    @Test
    fun `STORAGE_RESERVED increments storageUsed by the event size`() {
        // arrange
        val user = makeUser(storageUsed = 200)
        every { userRepository.findById(userId) } returns Optional.of(user)

        // act
        consumer.onMessage(message("STORAGE_RESERVED", size = 100L), acknowledgment, 0L)

        // assert
        assertEquals(300L, user.storageUsed)
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `STORAGE_RELEASED decrements storageUsed by the event size`() {
        // arrange
        val user = makeUser(storageUsed = 200)
        every { userRepository.findById(userId) } returns Optional.of(user)

        // act
        consumer.onMessage(message("STORAGE_RELEASED", size = 50L), acknowledgment, 0L)

        // assert
        assertEquals(150L, user.storageUsed)
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `STORAGE_RELEASED never drives storageUsed below zero`() {
        // arrange
        val user = makeUser(storageUsed = 30)
        every { userRepository.findById(userId) } returns Optional.of(user)

        // act
        consumer.onMessage(message("STORAGE_RELEASED", size = 100L), acknowledgment, 0L)

        // assert
        assertEquals(0L, user.storageUsed)
    }

    @Test
    fun `STORAGE_CONFIRMED does not change storageUsed`() {
        // arrange - it was already counted at reservation time
        val user = makeUser(storageUsed = 200)
        every { userRepository.findById(userId) } returns Optional.of(user)

        // act
        consumer.onMessage(message("STORAGE_CONFIRMED", size = 100L), acknowledgment, 0L)

        // assert
        assertEquals(200L, user.storageUsed)
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `STORAGE_RESERVED tracks usage even when the user has no quota`() {
        // arrange - usage is tracked unconditionally so a quota can be set later
        val user = makeUser(storageUsed = 0, storageQuota = null)
        every { userRepository.findById(userId) } returns Optional.of(user)

        // act
        consumer.onMessage(message("STORAGE_RESERVED", size = 4096L), acknowledgment, 0L)

        // assert
        assertEquals(4096L, user.storageUsed)
    }

    @Test
    fun `records the processed event id before applying the delta`() {
        // arrange
        val user = makeUser(storageUsed = 0)
        val eventId = UUID.randomUUID()
        val savedSlot = slot<ProcessedStorageEvent>()
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { processedStorageEventRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        // act
        consumer.onMessage(message("STORAGE_RESERVED", size = 100L, eventId = eventId.toString()), acknowledgment, 0L)

        // assert
        verify { processedStorageEventRepository.save(any()) }
        assertEquals(eventId, savedSlot.captured.eventId)
        assertEquals(100L, user.storageUsed)
    }

    @Test
    fun `applies the delta when the payload is a stringified JSON document`() {
        // arrange
        val user = makeUser(storageUsed = 10)
        every { userRepository.findById(userId) } returns Optional.of(user)

        // act
        consumer.onMessage(message("STORAGE_RESERVED", size = 5L, payloadAsString = true), acknowledgment, 0L)

        // assert
        assertEquals(15L, user.storageUsed)
    }

    @Test
    fun `ignores unrelated event types without touching the user`() {
        // act
        consumer.onMessage(message("FILE_INSERTED"), acknowledgment, 0L)

        // assert
        verify(exactly = 0) { userRepository.findById(any()) }
        verify(exactly = 0) { processedStorageEventRepository.save(any()) }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `skips messages without an event_type`() {
        // act
        consumer.onMessage(message(eventType = null), acknowledgment, 0L)

        // assert
        verify(exactly = 0) { userRepository.findById(any()) }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `skips payloads missing ownerUserId or size`() {
        // act
        consumer.onMessage(message("STORAGE_RESERVED", ownerUserId = null), acknowledgment, 0L)
        consumer.onMessage(message("STORAGE_RESERVED", size = null), acknowledgment, 0L)

        // assert
        verify(exactly = 0) { userRepository.findById(any()) }
        verify(exactly = 2) { acknowledgment.acknowledge() }
    }

    @Test
    fun `skips events for an unknown user`() {
        // arrange
        every { userRepository.findById(userId) } returns Optional.empty()

        // act
        consumer.onMessage(message("STORAGE_RESERVED"), acknowledgment, 0L)

        // assert
        verify(exactly = 0) { processedStorageEventRepository.save(any()) }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `skips events without a valid id`() {
        // arrange
        val user = makeUser(storageUsed = 100)
        every { userRepository.findById(userId) } returns Optional.of(user)

        // act
        consumer.onMessage(message("STORAGE_RESERVED", eventId = "not-a-uuid"), acknowledgment, 0L)

        // assert - delta not applied, marker not written
        assertEquals(100L, user.storageUsed)
        verify(exactly = 0) { processedStorageEventRepository.save(any()) }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `does not re-apply an already processed event`() {
        // arrange
        val user = makeUser(storageUsed = 200)
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { processedStorageEventRepository.existsById(any()) } returns true

        // act
        consumer.onMessage(message("STORAGE_RESERVED", size = 100L), acknowledgment, 0L)

        // assert - the counter is untouched and no marker is written
        assertEquals(200L, user.storageUsed)
        verify(exactly = 0) { processedStorageEventRepository.save(any()) }
        verify { acknowledgment.acknowledge() }
    }

    @Test
    fun `does not apply the delta when the marker insert loses a concurrent race`() {
        // arrange - a concurrent consumer already inserted the same event id
        val user = makeUser(storageUsed = 200)
        every { userRepository.findById(userId) } returns Optional.of(user)
        every { processedStorageEventRepository.save(any()) } throws DataIntegrityViolationException("duplicate key")

        // act
        consumer.onMessage(message("STORAGE_RESERVED", size = 100L), acknowledgment, 0L)

        // assert - the delta is skipped so the counter does not drift
        assertEquals(200L, user.storageUsed)
        verify { acknowledgment.acknowledge() }
    }
}
