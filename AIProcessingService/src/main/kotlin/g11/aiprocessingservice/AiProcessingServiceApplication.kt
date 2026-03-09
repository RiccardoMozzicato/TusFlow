package g11.aiprocessingservice

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener

@SpringBootApplication
class AiProcessingServiceApplication

fun main(args: Array<String>) {
    runApplication<AiProcessingServiceApplication>(*args)
}
