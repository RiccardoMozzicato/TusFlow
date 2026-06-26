package it.polito.wa2.userdetailservice.conf

import io.minio.MinioClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MinIOConfig(
    @Value("\${minio.url}") private val minioUrl: String,
    @Value("\${minio.access-key}") private val minioAccessKey: String,
    @Value("\${minio.secret-key}") private val minioSecretKey: String
) {

    @Bean
    fun minioClient(): MinioClient =
        MinioClient.builder()
            .endpoint(minioUrl)
            .credentials(minioAccessKey, minioSecretKey)
            .build()
}