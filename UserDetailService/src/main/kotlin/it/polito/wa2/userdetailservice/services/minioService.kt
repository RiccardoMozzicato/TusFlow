package it.polito.wa2.userdetailservice.services

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import it.polito.wa2.userdetailservice.dto.AvatarContentDTO
import it.polito.wa2.userdetailservice.dto.AvatarDTO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class minioService(
    @Value("\${minio.users-bucket}") private val usersBucket: String,
    private val minioClient: MinioClient
): minioServiceInterface {
    private val log = LoggerFactory.getLogger(minioService::class.java)

    init {
        log.info("Initializing minioService with users bucket: {}", usersBucket)

        var found = minioClient.bucketExists(
            BucketExistsArgs.builder().bucket(usersBucket).build()
        )

        if (!found) {
            log.info("users bucket {} does not exist, creating it", usersBucket)
            minioClient.makeBucket(
                MakeBucketArgs.builder().bucket(usersBucket).build()
            )
            log.info("Successfully created users bucket: {}", usersBucket)
        } else {
            log.debug("users bucket {} already exists", usersBucket)
        }
    }

    override fun putAvatar(objetKey: String, avatar: AvatarDTO) {
        avatar.avatar.use { buffer ->
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(usersBucket)
                    .`object`(objetKey)
                    .stream(buffer, avatar.size, -1)
                    .contentType(avatar.contentType)
                    .build()
            )
        }
    }

    override fun getAvatar(objectKey: String): AvatarContentDTO {
        val response = minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(usersBucket)
                .`object`(objectKey)
                .build()
        )
        val contentType = response.headers()["Content-Type"] ?: DEFAULT_AVATAR_CONTENT_TYPE
        val etag = response.headers()["ETag"]
        return AvatarContentDTO(response, contentType, etag)
    }

    override fun removeAvatar(objectKey: String) {
        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(usersBucket)
                .`object`(objectKey)
                .build()
        )
    }

    companion object {
        private const val DEFAULT_AVATAR_CONTENT_TYPE = "image/webp"
    }
}