package g11.aiprocessingservice.conf

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
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

    // Vision model client for image analysis (Groq Llama 4 Scout or equivalent).
    //
    // Unlike the chat/pdf clients, this one does NOT reuse the shared `openAiChatModel`'s endpoint:
    // it builds a dedicated OpenAiApi pointed at `ai.image.base-url` (defaults to Groq). That keeps
    // image analysis on a vision-capable model even when a profile (e.g. `local`) repoints
    // spring.ai.openai.* at a non-vision self-hosted model. `mutate()` keeps the auto-configured
    // model's retry/observation/tool collaborators — only the endpoint differs.
    @Bean
    @Qualifier("imageChatClient")
    fun imageChatClient(
        @Qualifier("openAiChatModel") openAiChatModel: OpenAiChatModel,
        @Value("\${ai.image.model}") imageModel: String,
        @Value("\${ai.image.base-url}") imageBaseUrl: String,
        @Value("\${ai.image.api-key}") imageApiKey: String,
        @Value("\${ai.image.connect-timeout-ms:10000}") connectTimeoutMs: Int,
        @Value("\${ai.image.read-timeout-ms:60000}") readTimeoutMs: Int,
        @Value("classpath:/prompts/image-analysis-prompt.st") imagePrompt: org.springframework.core.io.Resource
    ): ChatClient {
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(connectTimeoutMs)
        requestFactory.setReadTimeout(readTimeoutMs)

        val imageApi = OpenAiApi.builder()
            .baseUrl(imageBaseUrl)
            .apiKey(imageApiKey)
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
            .build()

        val imageChatModel = openAiChatModel.mutate().openAiApi(imageApi).build()

        return ChatClient.builder(imageChatModel)
            .defaultSystem(imagePrompt)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model(imageModel)
                    .build()
            )
            .build()
    }

    // Sets the read/connect timeout on the RestClient.Builder used by Spring AI's OpenAiApi
    // (injected via ObjectProvider<RestClient.Builder> in OpenAiChatAutoConfiguration).
    // The TusUploaderService client overrides this factory explicitly with its own timeouts (3s/5s).
    //
    // Default (20s) suits fast Groq responses. Self-hosted reasoning models (e.g. Qwen via
    // OpenWebUI) think for tens of seconds to minutes before answering, so the `local` profile
    // raises ai.openai.read-timeout-ms accordingly.
    @Bean
    fun groqReadTimeoutCustomizer(
        @Value("\${ai.openai.read-timeout-ms:20000}") readTimeoutMs: Int,
        @Value("\${ai.openai.connect-timeout-ms:10000}") connectTimeoutMs: Int,
    ): RestClientCustomizer = RestClientCustomizer { builder ->
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(connectTimeoutMs)
        factory.setReadTimeout(readTimeoutMs)
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