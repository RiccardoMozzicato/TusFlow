package g11.aiprocessingservice.services

import g11.aiprocessingservice.dto.FailedJobDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface FailedJobServiceInterface {

    /**
     * Returns a page of currently FAILED jobs the user has WRITE access to, newest first,
     * mapped to the dashboard's retry-page shape. Each entry carries the (fileId, version)
     * needed to drive a retry plus whether a manual retry is still permitted under the
     * user-retry cap.
     *
     * @param userToken the caller's JWT, used to resolve their writable files
     */
    fun listFailedJobs(userToken: String, pageable: Pageable): Page<FailedJobDTO>
}