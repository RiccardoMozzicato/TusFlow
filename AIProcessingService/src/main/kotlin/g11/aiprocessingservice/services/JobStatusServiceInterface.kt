package g11.aiprocessingservice.services

import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.MetadataDTO
import java.util.*

interface JobStatusServiceInterface {

    fun createJobIfNotExists(job: JobDTO): UUID?

    fun markJobAsCompleted(jobId: UUID, job: JobDTO, metadata: MetadataDTO)

    fun markJobAsUnsupported(jobId: UUID, job: JobDTO, reason: String?)

    fun markJobAsFailed(jobId: UUID, job: JobDTO, errorMessage: String)
}

