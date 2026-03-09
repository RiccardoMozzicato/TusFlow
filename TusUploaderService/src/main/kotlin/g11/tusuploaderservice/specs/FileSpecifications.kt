package g11.tusuploaderservice.specs

import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileShare
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
         * Build specification per file ORGANIZATION o DEPARTMENT filtrati per organization/department dell'utente
         * Filtra solo per sharingScope ORGANIZATION o DEPARTMENT
         */
        fun buildOrganizationDepartmentFiles(
            contentType: String?,
            dateFrom: String?,
            dateTo: String?,
            sharingScope: SharingScope,
            organization: String,
            department: String?
        ): Specification<File> {
            // Verifica che lo sharingScope sia ORGANIZATION o DEPARTMENT
            require(sharingScope == SharingScope.ORGANIZATION || sharingScope == SharingScope.DEPARTMENT) {
                "buildOrganizationDepartmentFiles accepts only ORGANIZATION or DEPARTMENT scope"
            }

            var spec: Specification<File> = hasSharingScope(sharingScope)
                .and(hasOrganization(organization))

            // Applica filtri opzionali
            if (contentType != null) {
                spec = spec.and(hasContentType(contentType))
            }
            if (dateFrom != null) {
                spec = spec.and(createdAfter(dateFrom))
            }
            if (dateTo != null) {
                spec = spec.and(createdBefore(dateTo))
            }

            // Se sharingScope è DEPARTMENT, filtra anche per department
            if (sharingScope == SharingScope.DEPARTMENT && department != null) {
                spec = spec.and(hasDepartment(department))
            }

            return spec
        }

        /**
         * Build specification per file PERSONAL
         * Restituisce file personal dove l'utente è owner O è presente nei fileShare
         */
        fun buildPersonalFiles(
            contentType: String?,
            dateFrom: String?,
            dateTo: String?,
            ownerId: UUID,
            userId: UUID
        ): Specification<File> {
            return Specification { root, query, cb ->
                query?.distinct(true)

                // Condizioni base
                val predicates = mutableListOf(
                    cb.equal(root.get<SharingScope>("sharingScope"), SharingScope.PERSONAL)
                )

                // Filtri opzionali
                if (contentType != null) {
                    val versions = root.join<File, Any>("versions", jakarta.persistence.criteria.JoinType.LEFT)
                    predicates.add(cb.equal(versions.get<String>("contentType"), contentType))
                }
                if (dateFrom != null) {
                    val from = Instant.parse(dateFrom)
                    val fromDate = Date.from(from)
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate))
                }
                if (dateTo != null) {
                    val to = Instant.parse(dateTo)
                    val toDate = Date.from(to)
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate))
                }

                // Condizione OR: owner O shared with user (usando LEFT JOIN)
                val shares = root.join<File, FileShare>("shares", jakarta.persistence.criteria.JoinType.LEFT)
                val ownerCondition = cb.equal(root.get<UUID>("ownerId"), ownerId)
                val sharedCondition = cb.equal(shares.get<UUID>("userID"), userId)
                predicates.add(cb.or(ownerCondition, sharedCondition))

                cb.and(*predicates.toTypedArray())
            }
        }
}