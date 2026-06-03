package g11.tusuploaderservice.dto

import g11.tusuploaderservice.entities.FileStatus
import g11.tusuploaderservice.entities.UploadStatus
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Sort
import java.time.Instant


data class FiltersDTO(
    val contentType: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val sortField: String,
    val sortDirection: Sort.Direction,
    val pageNumber: UInt = 0u,
    val pageSize: UInt = 10u,
    val sharingScopes: Set<SharingScope> = setOf(SharingScope.PERSONAL),
)