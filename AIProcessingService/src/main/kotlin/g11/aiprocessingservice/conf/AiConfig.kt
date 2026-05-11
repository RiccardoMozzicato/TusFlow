package g11.aiprocessingservice.conf

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestClientCustomizer
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

    // Vision model client for image analysis (Groq Llama 4 Scout or equivalent)
    @Bean
    @Qualifier("imageChatClient")
    fun imageChatClient(
        @Qualifier("openAiChatModel") openAiChatModel: OpenAiChatModel,
        @Value("\${ai.image.model}") imageModel: String,
        @Value("classpath:/prompts/image-analysis-prompt.st") imagePrompt: org.springframework.core.io.Resource
    ): ChatClient =
        ChatClient.builder(openAiChatModel)
            .defaultSystem(imagePrompt)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model(imageModel)
                    .build()
            )
            .build()

    // Sets a 20-second read timeout on the RestClient.Builder used by Spring AI's OpenAiApi
    // (injected via ObjectProvider<RestClient.Builder> in OpenAiChatAutoConfiguration).
    // The TusUploaderService client overrides this factory explicitly with its own timeouts (3s/5s).
    @Bean
    fun groqReadTimeoutCustomizer(): RestClientCustomizer = RestClientCustomizer { builder ->
        val factory = SimpleClientHttpRequestFactory()
        factory.setReadTimeout(20_000) // 20 seconds
        builder.requestFactory(factory)
    }

    // REST CLIENT CONFIG
    @Bean
    @Qualifier("TusUploaderService")
    fun fileClient(builder: RestClient.Builder) : RestClient{

        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(3000)  // 3 seconds
        requestFactory.setReadTimeout(60000)    // 60 seconds - allow large file downloads

        return builder
            .baseUrl("http://localhost:8081")
            .requestFactory(requestFactory)
            .build()

    }
}