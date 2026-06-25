package it.polito.wa2.userdetailservice.repositories

import it.polito.wa2.userdetailservice.dto.DepartmentDTO
import it.polito.wa2.userdetailservice.entities.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserRepository: JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    fun getUserByEmail(email: String): Optional<User>
    fun findByOrganization(organization: String, pageable: Pageable): Page<User>

    /**
     * Distinct departments in use, with their member counts. Scoped to [organization]
     * when non-null (null = no org restriction, mirroring the service-account listing).
     * Null/blank departments are excluded; [search] applies a case-insensitive substring
     * filter, with an empty string meaning "no filter" (a blank/absent term). Ordered by
     * member count desc, then name asc.
     *
     * [search] is intentionally non-null: binding a NULL to `LOWER(?)` makes Postgres
     * infer a `bytea` argument type and fail, so callers pass "" rather than null.
     */
    @Query(
        """
        SELECT new it.polito.wa2.userdetailservice.dto.DepartmentDTO(u.department, COUNT(u))
        FROM User u
        WHERE u.department IS NOT NULL AND TRIM(u.department) <> ''
          AND (:organization IS NULL OR u.organization = :organization)
          AND (:search = '' OR LOWER(u.department) LIKE CONCAT('%', LOWER(:search), '%'))
        GROUP BY u.department
        ORDER BY COUNT(u) DESC, u.department ASC
        """
    )
    fun findDepartments(
        @Param("organization") organization: String?,
        @Param("search") search: String,
    ): List<DepartmentDTO>
}
