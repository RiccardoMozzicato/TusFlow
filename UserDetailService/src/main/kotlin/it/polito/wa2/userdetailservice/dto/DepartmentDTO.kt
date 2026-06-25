package it.polito.wa2.userdetailservice.dto

/**
 * A distinct department in use within an organization, together with how many
 * users belong to it. Departments are not a first-class entity; this is derived
 * from the (free-form) [it.polito.wa2.userdetailservice.entities.User.department]
 * column. [userCount] is a JPA `COUNT(...)`, hence `Long`.
 */
data class DepartmentDTO(
    val name: String,
    val userCount: Long,
)