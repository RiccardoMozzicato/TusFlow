package it.polito.wa2.userdetailservice.dto

import jakarta.validation.constraints.Min
import org.springframework.data.domain.Sort

data class FiltersDTO(
   @field:Min(0)
   val pageNumber: Int = 0,
   @field:Min(1)
   val pageSize: Int = 10,
   /** Free-text query matched case-insensitively against fullName and email; null/blank means no filter. */
   val search: String? = null,
   /** Column to sort by; null falls back to the service default (fullName). */
   val sortField: String? = null,
   val sortDirection: Sort.Direction = Sort.Direction.ASC,
)
