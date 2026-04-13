package g11.tusuploaderservice.conf

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.stereotype.Component

/**
 * Promotes the W3C `traceparent` field from the Debezium message JSON body
 * into the Kafka record headers, so that Spring Kafka's observation mechanism
 * (isObservationEnabled = true) can create a child span linked to the
 * originating AIProcessingService trace.
 *
 * Debezium serialises the JSONB `payload` column as an escaped JSON string:
 *   { "event_type": "...", "payload": "{\"traceparent\":\"00-...\"}", ... }
 */
@Component
class TraceContextInterceptor(
    private val objectMapper: ObjectMapper
) : RecordInterceptor<String, String> {

    override fun intercept(
        record: ConsumerRecord<String, String>,
        consumer: Consumer<String, String>
    ): ConsumerRecord<String, String> {
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