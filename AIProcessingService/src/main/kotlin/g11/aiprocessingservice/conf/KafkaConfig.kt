package g11.aiprocessingservice.conf

import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.EnableKafkaRetryTopic
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.util.backoff.BackOff

@EnableKafka
@EnableKafkaRetryTopic
@Configuration
class KafkaConfig (
    @Value ("\${kafka.bootstrap-servers}")  private val bootstrapAddress: String,
    @Value ("\${kafka.consumer.group-id}") private val groupId: String,
    private val meterRegistry: MeterRegistry,
){

    /**
     * KafkaAdmin configuration for managing topics (create, delete, etc.)
     */
    @Bean
    fun kafkaAdmin(): KafkaAdmin {
        val configs = mutableMapOf<String, Any>()
        configs[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapAddress
        return KafkaAdmin(configs)
    }

    /**
     * Producer configuration for sending messages to DLT
     */
    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        // Log the bootstrap address being used
        org.slf4j.LoggerFactory.getLogger(KafkaConfig::class.java)
            .info("Configuring Kafka Producer with bootstrap-servers: $bootstrapAddress")

        val props = hashMapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapAddress,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java
        )
        return DefaultKafkaProducerFactory(props)
    }

    /**
     * KafkaTemplate for sending messages (used by error handler to send to DLT)
     */
    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory())
    }

    /**
     * Creates the file-analyzed topic for downstream embedding processing
     */
    @Bean
    fun fileAnalyzedTopic(): NewTopic {
        return TopicBuilder.name("file-analyzed")
            .partitions(3)
            .replicas(1)
            .build()
    }

    /**
     * Declares the file-outbox topic (produced by Debezium) with multiple partitions so the
     * MetadataProcessor listener can consume it with concurrency > 1 and analyze several files
     * in parallel. KafkaAdmin creates it if missing and, on an already-existing topic with fewer
     * partitions, increases the partition count to this value on startup. Debezium keys each
     * outbox event by its unique row id, so events spread evenly across partitions.
     */
    @Bean
    fun fileOutboxTopic(): NewTopic {
        return TopicBuilder.name("file-outbox")
            .partitions(FILE_OUTBOX_PARTITIONS)
            .replicas(1)
            .build()
    }

    /**
     * Declares the embeddings-batch topic with multiple partitions so EmbeddingGenerator can
     * consume it with concurrency > 1 and embed several batches in parallel. Without this the
     * topic auto-creates with the broker default (1 partition), which caps the listener at a
     * single active thread regardless of its configured concurrency. EmbeddingProcessor sends
     * these records with a null key so a single file's batches spread across partitions — safe
     * because embedding batches have no ordering requirement (chunks dedupe by chunkIndex).
     * KafkaAdmin creates it if missing and raises an existing topic to this partition count on
     * startup (it can only increase, never decrease).
     */
    @Bean
    fun embeddingsBatchTopic(): NewTopic {
        return TopicBuilder.name("embeddings-batch")
            .partitions(EMBEDDINGS_BATCH_PARTITIONS)
            .replicas(1)
            .build()
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val props = hashMapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapAddress,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false
        )
        // Register a MicrometerConsumerListener so the native Kafka consumer client metrics — most
        // notably kafka.consumer.fetch.manager.records-lag (per topic/partition backlog) — are bound
        // to the registry and exported via OTLP. This single factory backs all three container
        // factories below, so it covers file-outbox, file-analyzed and embeddings-batch.
        return DefaultKafkaConsumerFactory<String, String>(props).apply {
            addListener(org.springframework.kafka.core.MicrometerConsumerListener(meterRegistry))
        }
    }

    /**
     * Error handler with retry logic and Dead Letter Topic (DLT)
     * - Retries 1 time after 5 minutes
     * - After max retry, sends message to DLT: file-outbox-dlt
     */
    @Bean
    fun errorHandler(kafkaTemplate: KafkaTemplate<String, String>): DefaultErrorHandler {
        // Fixed backoff: 1 retry after 5 minutes (300000ms)
        val backOff: BackOff = ExponentialBackOffWithMaxRetries(1).apply {
            initialInterval = 300000L     // 5 minutes = 300000 milliseconds
            multiplier = 1.0              // No multiplier (fixed delay)
            maxInterval = 300000L         // Max interval same as initial
        }

        val errorHandler = DefaultErrorHandler(
            { consumerRecord, exception ->
                // This is called when all retries are exhausted
                // Send to Dead Letter Topic
                val dltTopic = "file-outbox-dlt"
                val key = consumerRecord.key()?.toString() ?: ""
                val value = consumerRecord.value()?.toString() ?: ""
                kafkaTemplate.send(dltTopic, key, value)
                org.slf4j.LoggerFactory.getLogger(KafkaConfig::class.java)
                    .error("Message sent to DLT topic '$dltTopic' after max retries: ${exception.message}")
            },
            backOff
        )

        // Don't retry on specific exceptions (optional)
        // errorHandler.addNotRetryableExceptions(IllegalArgumentException::class.java)

        return errorHandler
    }

    // Default factory — used by EmbeddingProcessor (file-analyzed topic).
    // file-analyzed messages carry a traceparent Kafka header injected by MetadataProcessor,
    // so isObservationEnabled correctly creates a child span linked to the upstream trace.
    @Bean
    fun kafkaListenerContainerFactory(
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.containerProperties.isObservationEnabled = true
        factory.setCommonErrorHandler(errorHandler)
        return factory
    }

    // Factory for Debezium-sourced topics (file-outbox).
    // Debezium never sets Kafka headers, so isObservationEnabled would create orphan root spans.
    // Trace propagation is handled manually inside the listener via Propagator.extract().
    //
    // No common error handler is set here on purpose: the listener uses @RetryableTopic, whose
    // non-blocking retry infrastructure installs its own error handler (forwards failures to a
    // retry topic instead of blocking the consumer thread). AckMode.RECORD commits each record
    // after the listener returns — the listener no longer acknowledges manually.
    @Bean
    fun debeziumKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        // Must be explicitly false — Spring Boot auto-config sets it to true on all factories
        factory.containerProperties.isObservationEnabled = false
        return factory
    }

    // Factory for the embedding pipeline listeners (file-analyzed, embeddings-batch), which also use
    // @RetryableTopic for non-blocking retries — so, like the Debezium factory, it sets no common
    // error handler and commits per record (AckMode.RECORD) instead of relying on manual acks.
    // Observation stays enabled: these messages carry a traceparent header, so spans link correctly.
    @Bean
    fun embeddingKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        factory.containerProperties.isObservationEnabled = true
        return factory
    }

    /**
     * Scheduler required by @RetryableTopic's non-blocking retry infrastructure: it parks records
     * on retry topics until their delay elapses, then resumes consumption. Without a scheduler bean
     * the retry-topic bootstrapping fails to start.
     *
     * Wrapped in a [RetryTopicSchedulerWrapper] (rather than exposed as a bare [TaskScheduler] bean)
     * so it is unambiguous: enabling the STOMP WebSocket broker registers its own
     * `messageBrokerTaskScheduler`, and the retry-topic backoff manager requires a *single*
     * TaskScheduler. The wrapper lets Spring Kafka pick this one specifically and manages its
     * lifecycle (initialize/shutdown).
     */
    @Bean
    fun retryTopicSchedulerWrapper(): RetryTopicSchedulerWrapper {
        val scheduler = ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("retry-topic-scheduler-")
        }
        return RetryTopicSchedulerWrapper(scheduler)
    }

    companion object {
        /** Partition count for the file-outbox topic; also the upper bound on listener concurrency. */
        const val FILE_OUTBOX_PARTITIONS = 3

        /** Partition count for the embeddings-batch topic; the upper bound on EmbeddingGenerator concurrency. */
        const val EMBEDDINGS_BATCH_PARTITIONS = 3
    }
}