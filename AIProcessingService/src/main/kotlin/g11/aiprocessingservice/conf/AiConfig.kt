package g11.aiprocessingservice.conf

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class AiConfig {

    // SPRING AI CLIENT CONFIG
    @Bean
    @Qualifier("chatClient")
    fun chatClient(
        @Qualifier("openAiChatModel") openAiChatModel: OpenAiChatModel,
        @Value("\${ai.chat.model}") chatModel: String,
        @Value("classpath:/prompts/file-analysis-prompt.st") filePrompt: org.springframework.core.io.Resource
    ): ChatClient =
        ChatClient.builder(openAiChatModel)
            .defaultSystem(filePrompt)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model(chatModel)
                    .build()
            )
            .build()

    @Bean
    @Qualifier("ragChatClient")
    fun ragChatClient(
        @Qualifier("openAiChatModel") openAiChatModel: OpenAiChatModel,
        @Value("\${ai.chat.model}") chatModel: String,
        @Value("classpath:/prompts/rag-prompt.st") ragPrompt: org.springframework.core.io.Resource
    ): ChatClient =
        ChatClient.builder(openAiChatModel)
            .defaultSystem(ragPrompt)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model(chatModel)
                    .build()
            )
            .build()


    @Bean
    @Qualifier("pdfAnalysisChatClient")
    fun pdfAnalysisChatClient(
        @Qualifier("openAiChatModel") openAiChatModel: OpenAiChatModel,
        @Value("\${ai.pdf.model}") pdfModel: String,
        @Value("classpath:/prompts/pdf-analysis-prompt.st") pdfPrompt: org.springframework.core.io.Resource
    ): ChatClient =
        ChatClient.builder(openAiChatModel)
            .defaultSystem(pdfPrompt)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model(pdfModel)
                    .build()
            )
            .build()

    // REST CLIENT CONFIG
    @Bean
    @Qualifier("TusUploaderService")
    fun fileClient(builder: RestClient.Builder) : RestClient{

        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(3000) // 3 seconds
        requestFactory.setReadTimeout(5000)    // 5 seconds

        return builder
            .baseUrl("http://localhost:8081")
            .requestFactory(requestFactory)
            .build()

    }
}