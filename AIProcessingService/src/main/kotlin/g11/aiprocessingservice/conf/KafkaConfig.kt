package g11.aiprocessingservice.conf

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
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.util.backoff.BackOff

@EnableKafka
@Configuration
class KafkaConfig (
    @Value ("\${kafka.bootstrap-servers}")  private val bootstrapAddress: String,
    @Value ("\${kafka.consumer.group-id}") private val groupId: String
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
     * Creates the Dead Letter Topic (DLT) automatically on application startup.
     * Topic: file-outbox-dlt
     * Partitions: 1
     * Replication factor: 1
     *
     * Note: If Kafka is not available at startup, the topic creation will fail silently.
     * The topic will be created on first use if auto.create.topics.enable=true in Kafka.
     */
    @Bean
    fun deadLetterTopic(): NewTopic {
        return TopicBuilder.name("file-outbox-dlt")
            .partitions(1)
            .replicas(1)
            .build()
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
        return DefaultKafkaConsumerFactory(props)
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

    // Expected by default from Spring Kafka
    @Bean
    fun kafkaListenerContainerFactory(
        errorHandler: DefaultErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory()

        // Set acknowledgment mode to MANUAL: requires explicit acknowledgment.acknowledge() call
        // This allows us to commit the offset only after successfully creating the job in the database
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL

        // Set error handler with retry and DLT
        factory.setCommonErrorHandler(errorHandler)

        return factory
    }
}