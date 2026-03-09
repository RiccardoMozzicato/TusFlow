package g11.aiprocessingservice.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

/**
 * Service to retrieve files accessible by a user (owned + shared).
 * This is used by the RAG system to filter embeddings based on user access.
 */
@Service
class FileAccessService(
    @Qualifier("TusUploaderService") private val fileServiceClient: RestClient
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Get all file IDs that a user has access to (owned files + files shared with them).
     *
     * @param userId The UUID of the user
     * @param userToken The user's JWT token (NOT the service account token)
     * @return Set of file UUIDs that the user can access
     */
    fun getAccessibleFileIds(userId: UUID, userToken: String): Set<String> {
        try {
            val ownedFileIds = getOwnedFileIds(userId, userToken)
            val sharedFileIds = getSharedFileIds(userId, userToken)

            val allAccessibleIds = ownedFileIds + sharedFileIds

            log.info("User $userId has access to ${allAccessibleIds.size} files (${ownedFileIds.size} owned + ${sharedFileIds.size} shared)")

            return allAccessibleIds
        } catch (ex: RestClientResponseException) {
            log.error("Error retrieving accessible files for user $userId: ${ex.message}")
            // In case of error, return empty set (fail-safe)
            return emptySet()
        } catch (ex: Exception) {
            log.error("Unexpected error retrieving accessible files for user $userId", ex)
            return emptySet()
        }
    }

    /**
     * Get file IDs owned by the user
     */
    private fun getOwnedFileIds(userId: UUID, token: String): Set<String> {
        try {
            // Fetch personal files (owned by user) - get all pages
            val ownedFiles = mutableSetOf<String>()
                var page = 0
                var hasMore = true

                while (hasMore) {
                    val response = fileServiceClient.get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .path("/api/v1/files")
                                .queryParam("sharingScope","PERSONAL")
                                .queryParam("page", page)
                                .queryParam("size", 100) // Large page size to minimize requests
                                .build()
                        }
                        .headers { it.setBearerAuth(token) }
                        .retrieve()
                        .body(PageResponse::class.java)

                    if (response != null && response.content.isNotEmpty()) {
                        ownedFiles.addAll(response.content.map { it.id })
                        hasMore = !response.last
                        page++
                    } else {
                        hasMore = false
                    }
                }


            log.debug("Found ${ownedFiles.size} owned files for user $userId")
            return ownedFiles
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode.value() == 404) {
                log.debug("No owned files found for user $userId (404)")
                return emptySet()
            }
            throw ex
        }
    }

    /**
     * Get file IDs shared with the user
     */
    private fun getSharedFileIds(userId: UUID, token: String): Set<String> {
        try {
            // Fetch files shared with user - get all pages
            val sharedFiles = mutableSetOf<String>()
            var page = 0
            var hasMore = true

            while (hasMore) {
                val response = fileServiceClient.get()
                    .uri { uriBuilder ->
                        uriBuilder
                            .path("/api/v1/files/my-shared")
                            .queryParam("page", page)
                            .queryParam("size", 100) // Large page size to minimize requests
                            .build()
                    }
                    .headers { it.setBearerAuth(token) }
                    .retrieve()
                    .body(PageResponse::class.java)

                if (response != null && response.content.isNotEmpty()) {
                    sharedFiles.addAll(response.content.map { it.id })
                    hasMore = !response.last
                    page++
                } else {
                    hasMore = false
                }
            }

            log.debug("Found ${sharedFiles.size} shared files for user $userId")
            return sharedFiles
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode.value() == 404) {
                log.debug("No shared files found for user $userId (404)")
                return emptySet()
            }
            throw ex
        }
    }
}
/**
 * Simplified DTO for file metadata response
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FileMetadataDTO(
    val id: String,
    val fileName: String? = null
)

/**
 * Paginated response wrapper
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PageResponse(
    val content: List<FileMetadataDTO> = emptyList(),
    val totalPages: Int = 0,
    val totalElements: Long = 0,
    val last: Boolean = true,
    val size: Int = 0,
    val number: Int = 0
)

