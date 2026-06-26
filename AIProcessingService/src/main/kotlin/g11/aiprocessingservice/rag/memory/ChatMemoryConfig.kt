package g11.aiprocessingservice.rag.memory

import g11.aiprocessingservice.rag.retrieval.RagProperties
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.ChatMemoryRepository
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Conversational chat memory backed by the auto-configured [ChatMemoryRepository]
 * (JdbcChatMemoryRepository → SPRING_AI_CHAT_MEMORY). Defining our own [ChatMemory] bean lets the
 * window size be driven by `app.rag.memory.max-messages` instead of the framework default; the
 * auto-configured MessageWindowChatMemory backs off (@ConditionalOnMissingBean).
 *
 * The table schema is owned by Flyway (V11), so keep
 * `spring.ai.chat.memory.repository.jdbc.initialize-schema=never`.
 */
@Configuration
class ChatMemoryConfig {

    @Bean
    fun chatMemory(repository: ChatMemoryRepository, props: RagProperties): ChatMemory =
        MessageWindowChatMemory.builder()
            .chatMemoryRepository(repository)
            .maxMessages(props.memory.maxMessages)
            .build()
}
