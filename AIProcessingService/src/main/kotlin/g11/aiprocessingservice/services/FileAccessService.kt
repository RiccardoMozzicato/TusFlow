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
) : FileAccessServiceInterface {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Get all file IDs that a user has access to (owned files + files shared with them).
     *
     * @param userId The UUID of the user
     * @param userToken The user's JWT token (NOT the service account token)
     * @return Set of file UUIDs that the user can access
     */
    override fun getAccessibleFileIds(userId: UUID, userToken: String): Set<String> {
        try {
            // A single all-scopes `/api/v1/files` query returns every file readable by the user.
            // The PERSONAL scope already ORs owned files with files shared with the user, so this
            // also covers PERSONAL shares; DEPARTMENT/ORGANIZATION scopes cover role-based access.
            val accessibleFileIds = getUserFileIds(userId, userToken)

            log.info("User $userId has access to ${accessibleFileIds.size} readable files")

            return accessibleFileIds
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
     * Of [candidateIds], the subset the user has WRITE access to (owned, READ_WRITE shares, and
     * role-based department/organization access). The permission policy is computed authoritatively
     * by TusUploaderService; this method forwards the candidate set and the user's token so the
     * request stays bounded to the files the caller actually cares about.
     *
     * @param candidateIds The file ids to check; an empty set returns empty without a round-trip
     * @param userToken The user's JWT token (NOT the service account token)
     * @return Subset of [candidateIds] the user can modify; empty on any downstream error (fail-safe)
     */
    override fun getWritableFileIds(candidateIds: Collection<UUID>, userToken: String): Set<UUID> {
        if (candidateIds.isEmpty()) return emptySet()
        return try {
            // The endpoint caps each request at MAX_WRITABLE_CANDIDATES, so split larger candidate
            // sets (e.g. the dashboard stats, which span every file with a job) into batches and
            // union the results. Without this, an over-cap request would 400 and fail-safe to empty,
            // silently dropping the user's stats/list to zero.
            val writable = candidateIds.distinct()
                .chunked(WRITABLE_BATCH_SIZE)
                .flatMap { batch -> requestWritableBatch(batch, userToken) }
                .toSet()
            log.info("User has WRITE access to ${writable.size} of ${candidateIds.size} candidate files")
            writable
        } catch (ex: RestClientResponseException) {
            log.error("Error retrieving writable files: ${ex.message}")
            emptySet()
        } catch (ex: Exception) {
            log.error("Unexpected error retrieving writable files", ex)
            emptySet()
        }
    }

    /** Single round-trip for one bounded batch of candidate ids; callers union the batches. */
    private fun requestWritableBatch(batch: List<UUID>, userToken: String): List<UUID> {
        val ids = fileServiceClient.post()
            .uri { it.path("/api/v1/files/writable-ids").build() }
            .headers { it.setBearerAuth(userToken) }
            .body(batch)
            .retrieve()
            .body(Array<UUID>::class.java)
        return ids?.toList() ?: emptyList()
    }

    /**
     * Get all file IDs readable by the user across every sharing scope
     * (PERSONAL, DEPARTMENT, ORGANIZATION).
     *
     * The PERSONAL scope on `/api/v1/files` already returns both owned files and files
     * explicitly shared with the user (the server-side spec ORs `ownerId == userId` with a
     * join on `shares.userID == userId`), so this set also covers PERSONAL shares.
     */
    private fun getUserFileIds(userId: UUID, token: String): Set<String> {
        try {
            // Fetch all files readable by the user - get all pages
            val readableFiles = mutableSetOf<String>()
                var page = 0
                var hasMore = true

                while (hasMore) {
                    val response = fileServiceClient.get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .path("/api/v1/files")
                                .queryParam("sharingScope", "PERSONAL", "DEPARTMENT", "ORGANIZATION")
                                .queryParam("page", page)
                                .queryParam("size", 100) // Large page size to minimize requests
                                .build()
                        }
                        .headers { it.setBearerAuth(token) }
                        .retrieve()
                        .body(PageResponse::class.java)

                    if (response != null && response.content.isNotEmpty()) {
                        readableFiles.addAll(response.content.map { it.id })
                        hasMore = !response.last
                        page++
                    } else {
                        hasMore = false
                    }
                }


            log.debug("Found ${readableFiles.size} readable files for user $userId")
            return readableFiles
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode.value() == 404) {
                log.debug("No readable files found for user $userId (404)")
                return emptySet()
            }
            throw ex
        }
    }

    private companion object {
        /**
         * Max candidate ids per `writable-ids` request. Must stay at or below the endpoint's own
         * cap (`FileController.MAX_WRITABLE_CANDIDATES`); larger candidate sets are chunked.
         */
        const val WRITABLE_BATCH_SIZE = 1000
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

