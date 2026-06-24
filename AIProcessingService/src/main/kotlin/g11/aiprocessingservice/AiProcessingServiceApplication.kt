package g11.aiprocessingservice

import g11.aiprocessingservice.conf.SupportedContentTypeProperties
import g11.aiprocessingservice.rag.retrieval.RagProperties
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.annotation.EnableAsync

@EnableAsync
@EnableConfigurationProperties(SupportedContentTypeProperties::class, RagProperties::class)
@SpringBootApplication
class AiProcessingServiceApplication

fun main(args: Array<String>) {
    runApplication<AiProcessingServiceApplication>(*args)
}
