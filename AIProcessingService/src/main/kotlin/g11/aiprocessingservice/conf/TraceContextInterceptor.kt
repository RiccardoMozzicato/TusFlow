package g11.aiprocessingservice.conf

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.stereotype.Component

/**
 * Ensures every consumed Kafka record has a `traceparent` header so that
 * Spring Kafka's observation mechanism (isObservationEnabled = true) can
 * create a child span linked to the originating trace.
 *
 * Two message formats are handled:
 *
 * 1. Direct KafkaTemplate messages (file-analyzed, embeddings-batch):
 *    traceparent is already in the Kafka record headers — no action needed.
 *
 * 2. Debezium outbox messages (file-outbox, job-outbox):
 *    Debezium serialises the JSONB `payload` column as an escaped JSON string,
 *    so the message body looks like:
 *      { "id": "...", "event_type": "...", "payload": "{\"traceparent\":\"00-...\"}", ... }
 *    The interceptor parses the nested payload string and promotes traceparent
 *    to a Kafka header.
 */
@Component
class TraceContextInterceptor(
    private val objectMapper: ObjectMapper
) : RecordInterceptor<String, String> {

    override fun intercept(
        record: ConsumerRecord<String, String>,
        consumer: Consumer<String, String>
    ): ConsumerRecord<String, String> {
        // If traceparent is already in the headers (injected by KafkaTemplate observation),
        // Spring Kafka's observation reads it directly — nothing to do.
        if (record.headers().lastHeader("traceparent") != null) return record

        val traceparent = extractTraceparent(record.value()) ?: return record

        val headers = RecordHeaders(record.headers())
        headers.add("traceparent", traceparent.toByteArray(Charsets.UTF_8))

        return ConsumerRecord(
            record.topic(), record.partition(), record.offset(),
            record.timestamp(), record.timestampType(),
            record.serializedKeySize(), record.serializedValueSize(),
            record.key(), record.value(),
            headers, record.leaderEpoch()
        )
    }

    private fun extractTraceparent(json: String): String? = try {
        val root = objectMapper.readTree(json)

        // Try root level first (plain JSON messages)
        root["traceparent"]?.asText()?.takeIf { it.isNotBlank() }
            // Try inside Debezium "payload" field (serialised as escaped JSON string)
            ?: root["payload"]?.asText()?.takeIf { it.isNotBlank() }?.let { payloadStr ->
                objectMapper.readTree(payloadStr)["traceparent"]?.asText()?.takeIf { it.isNotBlank() }
            }
    } catch (_: Exception) {
        null
    }
}