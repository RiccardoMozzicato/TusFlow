package g11.aiprocessingservice.conf

import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
class EmbeddingConfig {

    private val log = LoggerFactory.getLogger(EmbeddingConfig::class.java)

    /**
     * Marca ollamaEmbeddingModel come primario per il VectorStore in tutti i profili
     * tranne `local`. Risolve il conflitto tra ollamaEmbeddingModel e openAiEmbeddingModel.
     */
    @Bean
    @Primary
    @Profile("!local")
    fun primaryEmbeddingModel(
        @Qualifier("ollamaEmbeddingModel") ollamaEmbeddingModel: EmbeddingModel,
        @Value("\${spring.ai.ollama.embedding.options.model:unknown}") ollamaModel: String,
        @Value("\${spring.ai.vectorstore.pgvector.dimensions:2560}") dimensions: String,
    ): EmbeddingModel {
        log.info("Embedding model in use: provider=Ollama, model={}, dimensions={}", ollamaModel, dimensions)
        return ollamaEmbeddingModel
    }

    /**
     * Nel profilo `local` la sorgente degli embedding è selezionabile: di default usa
     * Ollama (qwen3-embedding:4b in locale); con APP_EMBEDDING_PROVIDER=openai (o
     * `openwebui`) usa il modello OpenAI-compatibile servito da OpenWebUI. Entrambi sono
     * Qwen3-Embedding-4B → 2560 dim nello stesso spazio, quindi sono intercambiabili
     * contro lo stesso vector_store.
     */
    @Bean
    @Primary
    @Profile("local")
    fun localPrimaryEmbeddingModel(
        @Value("\${app.embedding.provider:ollama}") provider: String,
        @Qualifier("ollamaEmbeddingModel") ollamaEmbeddingModel: EmbeddingModel,
        @Qualifier("openAiEmbeddingModel") openAiEmbeddingModel: EmbeddingModel,
        @Value("\${spring.ai.ollama.embedding.options.model:unknown}") ollamaModel: String,
        @Value("\${spring.ai.openai.embedding.options.model:unknown}") openAiModel: String,
        @Value("\${spring.ai.vectorstore.pgvector.dimensions:2560}") dimensions: String,
    ): EmbeddingModel {
        val useOpenAi = provider.equals("openai", ignoreCase = true) ||
            provider.equals("openwebui", ignoreCase = true)
        val backend = if (useOpenAi) "OpenWebUI" else "Ollama"
        val model = if (useOpenAi) openAiModel else ollamaModel
        log.info(
            "Embedding model in use: profile=local, provider={} ({}), model={}, dimensions={}",
            provider, backend, model, dimensions,
        )
        return if (useOpenAi) openAiEmbeddingModel else ollamaEmbeddingModel
    }
}

