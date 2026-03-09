package it.polito.wa2.userdetailservice.dto

import jakarta.validation.constraints.Min

data class FiltersDTO(
   @field:Min(0)
   val pageNumber: Int = 0,
   @field:Min(1)
   val pageSize: Int = 10
)