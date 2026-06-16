package g11.tusuploaderservice.specs

import g11.tusuploaderservice.dto.Ownership
import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.dto.UserRole
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileShare
import g11.tusuploaderservice.entities.FileStatus
import g11.tusuploaderservice.entities.FileVersion
import g11.tusuploaderservice.entities.PERMISSION_LEVEL
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
         * Restituisce solo file la cui versione corrente è AVAILABLE.
         *
         * [ownership] partiziona l'insieme già visibile: OWNED tiene solo i file di cui
         * l'utente è owner, SHARED solo quelli con owner diverso, null nessun vincolo.
         */
        fun buildFilesByScopes(
            contentType: String?,
            dateFrom: String?,
            dateTo: String?,
            sharingScopes: Set<SharingScope>,
            userId: UUID,
            organization: String,
            department: String?,
            isAdmin: Boolean,
            ownership: Ownership? = null
        ): Specification<File> {
            require(sharingScopes.isNotEmpty()) { "At least one sharing scope is required" }

            return Specification { root, query, cb ->
                query?.distinct(true)

                val predicates = mutableListOf<Predicate>()

                // Join sulla versione corrente: mostriamo i file la cui versione
                // corrente è AVAILABLE oppure MERGE_PENDING (ancora in lavorazione),
                // escludendo solo i DELETED.
                val currentVersion = root.join<File, FileVersion>("versions")
                predicates.add(cb.equal(currentVersion.get<Long>("version"), root.get<Long>("currentVersion")))
                predicates.add(
                    currentVersion.get<FileStatus>("status")
                        .`in`(FileStatus.AVAILABLE, FileStatus.MERGE_PENDING),
                )

                // Filtri opzionali comuni a tutti gli scope (sulla versione corrente).
                // Match "leggero" case-insensitive (contains) sul contentType: così un
                // filtro come "text/plain" intercetta anche "text/plain; charset=utf-8"
                // e "word" copre sia application/msword sia il MIME OpenXML del .docx.
                if (contentType != null) {
                    predicates.add(
                        cb.like(
                            cb.lower(currentVersion.get<String>("contentType")),
                            "%${contentType.lowercase()}%",
                        ),
                    )
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

                // Ownership: partiziona i file visibili in base all'owner corrente.
                when (ownership) {
                    Ownership.OWNED -> predicates.add(cb.equal(root.get<UUID>("ownerId"), userId))
                    Ownership.SHARED -> predicates.add(cb.notEqual(root.get<UUID>("ownerId"), userId))
                    null -> {}
                }

                cb.and(*predicates.toTypedArray())
            }
        }

        /**
         * Build a specification selecting the files the user has WRITE access to, mirroring
         * [g11.tusuploaderservice.services.FileService.getCurrentUserPermission] as a single query:
         * - owner (any scope);
         * - explicit READ_WRITE share;
         * - DEPARTMENT scope in the same organization when admin (any department) or manager of
         *   the file's department;
         * - ORGANIZATION scope in the same organization when admin.
         * Staff get no role-based write access (department/organization files are read-only for them).
         * Excludes files whose current version is DELETED, matching [buildFilesByScopes].
         *
         * When [candidateIds] is non-empty the result is further restricted to those files, so
         * callers that already have a small set of files of interest avoid materializing the
         * user's entire writable universe.
         */
        fun buildWritableFiles(
            userId: UUID,
            organization: String,
            department: String?,
            role: UserRole,
            candidateIds: Collection<UUID> = emptyList(),
        ): Specification<File> =
            Specification { root, query, cb ->
                query?.distinct(true)

                // Restrict to the current, non-deleted version (AVAILABLE or still MERGE_PENDING).
                val currentVersion = root.join<File, FileVersion>("versions")
                val currentVersionPredicate = cb.and(
                    cb.equal(currentVersion.get<Long>("version"), root.get<Long>("currentVersion")),
                    currentVersion.get<FileStatus>("status")
                        .`in`(FileStatus.AVAILABLE, FileStatus.MERGE_PENDING),
                )

                val scopeField = root.get<SharingScope>("sharingScope")
                val orgField = root.get<String>("organization")

                val writePredicates = mutableListOf<Predicate>()

                // Owner always has READ_WRITE.
                writePredicates.add(cb.equal(root.get<UUID>("ownerId"), userId))

                // Explicit READ_WRITE share for this user.
                val shares = root.join<File, FileShare>("shares", JoinType.LEFT)
                writePredicates.add(
                    cb.and(
                        cb.equal(shares.get<UUID>("userID"), userId),
                        cb.equal(shares.get<PERMISSION_LEVEL>("permission"), PERMISSION_LEVEL.READ_WRITE),
                    ),
                )

                // Role-based scope write access within the user's organization.
                when (role) {
                    UserRole.ROLE_admin -> {
                        // Admin: READ_WRITE on any DEPARTMENT or ORGANIZATION file in their org.
                        writePredicates.add(
                            cb.and(
                                cb.equal(scopeField, SharingScope.DEPARTMENT),
                                cb.equal(orgField, organization),
                            ),
                        )
                        writePredicates.add(
                            cb.and(
                                cb.equal(scopeField, SharingScope.ORGANIZATION),
                                cb.equal(orgField, organization),
                            ),
                        )
                    }

                    UserRole.ROLE_manager -> {
                        // Manager: READ_WRITE on DEPARTMENT files of their own department.
                        if (department != null) {
                            writePredicates.add(
                                cb.and(
                                    cb.equal(scopeField, SharingScope.DEPARTMENT),
                                    cb.equal(orgField, organization),
                                    cb.equal(root.get<String>("department"), department),
                                ),
                            )
                        }
                    }

                    // Staff: only owner + explicit READ_WRITE share (added above).
                    UserRole.ROLE_staff -> {}
                }

                val writable = cb.and(currentVersionPredicate, cb.or(*writePredicates.toTypedArray()))
                if (candidateIds.isEmpty()) {
                    writable
                } else {
                    cb.and(writable, root.get<UUID>("uuid").`in`(candidateIds))
                }
            }
}