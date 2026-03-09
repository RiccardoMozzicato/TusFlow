package g11.aiprocessingservice.conf

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class EmbeddingConfig {

    /**
     * Marca ollamaEmbeddingModel come primario per il VectorStore.
     * Questo risolve il conflitto tra ollamaEmbeddingModel e openAiEmbeddingModel.
     */
    @Bean
    @Primary
    fun primaryEmbeddingModel(
        @Qualifier("ollamaEmbeddingModel") ollamaEmbeddingModel: EmbeddingModel
    ): EmbeddingModel {
        return ollamaEmbeddingModel
    }
}

