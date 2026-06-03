package g11.tusuploaderservice.specs

import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileShare
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.util.Date
import java.util.UUID

object FileSpecifications {


    fun hasContentType(contentType: String): Specification<File> =
        Specification { root, query, cb ->
            query?.distinct(true)
            val versions = root.join<File, Any>("versions")
            cb.equal(versions.get<String>("contentType"), contentType)
        }

    fun hasSharingScope(sharingScope: SharingScope): Specification<File> =
        Specification { root, _, cb ->
            cb.equal(root.get<SharingScope>("sharingScope"), sharingScope)
        }

    fun hasOrganization(organization: String): Specification<File> =
        Specification { root, _, cb ->
            cb.equal(root.get<String>("organization"), organization)
        }

    fun hasDepartment(department: String): Specification<File> =
        Specification { root, _, cb ->
            cb.equal(root.get<String>("department"), department)
        }

    fun isSharedWithUser(userId: UUID): Specification<File> =
        Specification { root, query, cb ->
            query?.distinct(true)
            val shares = root.join<File, FileShare>("shares", jakarta.persistence.criteria.JoinType.LEFT)
            cb.equal(shares.get<UUID>("userID"), userId)
        }

    fun hasOwner(ownerId: UUID): Specification<File> =
        Specification { root, _, cb ->
            cb.equal(root.get<UUID>("ownerId"), ownerId)
        }

        fun createdAfter(dateFromIso: String): Specification<File> {
            val from = Instant.parse(dateFromIso)
            val fromDate = Date.from(from)
            return Specification { root, _, cb -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate) }
        }

        fun createdBefore(dateToIso: String): Specification<File> {
            val to = Instant.parse(dateToIso)
            val toDate = Date.from(to)
            return Specification { root, _, cb -> cb.lessThanOrEqualTo(root.get("createdAt"), toDate) }
        }

        /**
         * Build specification per i file visibili all'utente nei sharingScope richiesti (in OR):
         * - PERSONAL: file dove l'utente è owner O è presente nei fileShare
         * - DEPARTMENT: file del department dell'utente; l'admin vede tutti i department
         *   dell'organization, un non-admin senza department non vede alcun file DEPARTMENT
         * - ORGANIZATION: file dell'organization dell'utente
         */
        fun buildFilesByScopes(
            contentType: String?,
            dateFrom: String?,
            dateTo: String?,
            sharingScopes: Set<SharingScope>,
            userId: UUID,
            organization: String,
            department: String?,
            isAdmin: Boolean
        ): Specification<File> {
            require(sharingScopes.isNotEmpty()) { "At least one sharing scope is required" }

            return Specification { root, query, cb ->
                query?.distinct(true)

                val predicates = mutableListOf<Predicate>()

                // Filtri opzionali comuni a tutti gli scope
                if (contentType != null) {
                    val versions = root.join<File, Any>("versions", JoinType.LEFT)
                    predicates.add(cb.equal(versions.get<String>("contentType"), contentType))
                }
                if (dateFrom != null) {
                    val fromDate = Date.from(Instant.parse(dateFrom))
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate))
                }
                if (dateTo != null) {
                    val toDate = Date.from(Instant.parse(dateTo))
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate))
                }

                // Un predicato per ogni scope richiesto, combinati in OR
                val scopeField = root.get<SharingScope>("sharingScope")
                val scopePredicates = sharingScopes.map { scope ->
                    when (scope) {
                        SharingScope.PERSONAL -> {
                            // Owner O shared with user (usando LEFT JOIN)
                            val shares = root.join<File, FileShare>("shares", JoinType.LEFT)
                            cb.and(
                                cb.equal(scopeField, SharingScope.PERSONAL),
                                cb.or(
                                    cb.equal(root.get<UUID>("ownerId"), userId),
                                    cb.equal(shares.get<UUID>("userID"), userId)
                                )
                            )
                        }

                        SharingScope.DEPARTMENT -> {
                            val deptPredicates = mutableListOf(
                                cb.equal(scopeField, SharingScope.DEPARTMENT),
                                cb.equal(root.get<String>("organization"), organization)
                            )
                            when {
                                // Admin: vede i file DEPARTMENT di tutti i department dell'organization
                                isAdmin -> {}
                                department != null ->
                                    deptPredicates.add(cb.equal(root.get<String>("department"), department))
                                // Non-admin senza department: nessun file DEPARTMENT
                                else -> deptPredicates.add(cb.disjunction())
                            }
                            cb.and(*deptPredicates.toTypedArray())
                        }

                        SharingScope.ORGANIZATION -> cb.and(
                            cb.equal(scopeField, SharingScope.ORGANIZATION),
                            cb.equal(root.get<String>("organization"), organization)
                        )
                    }
                }
                predicates.add(cb.or(*scopePredicates.toTypedArray()))

                cb.and(*predicates.toTypedArray())
            }
        }
}