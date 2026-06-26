package g11.aiprocessingservice.services

import java.util.UUID

/**
 * Resolves the files a user may read or write, by delegating to TusUploaderService's
 * authoritative permission policy. Used by the RAG retrieval filter and by the job
 * dashboard/retry flows to scope results to the caller's accessible files.
 */
interface FileAccessServiceInterface {

    /**
     * All file IDs the user can read (owned + shared, across every sharing scope).
     * Fail-safe: returns an empty set on any downstream error.
     */
    fun getAccessibleFileIds(userId: UUID, userToken: String): Set<String>

    /**
     * Of [candidateIds], the subset the user has WRITE access to. An empty input returns
     * empty without a round-trip; fail-safe to empty on any downstream error.
     */
    fun getWritableFileIds(candidateIds: Collection<UUID>, userToken: String): Set<UUID>
}