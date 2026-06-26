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
import org.springframework.data.domain.Sort
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
         * Build specification per i file visibili all'utente nei sharingScope richiesti (in OR).
         * In ogni scope la membership è messa in OR con uno share esplicito sul file: se un file
         * di un certo scope è condiviso con l'utente, rientra quando quello scope è richiesto.
         * - PERSONAL: file dove l'utente è owner O è condiviso con lui (fileShare)
         * - DEPARTMENT: file del department dell'utente (l'admin vede tutti i department
         *   dell'organization) O file DEPARTMENT condivisi con l'utente
         * - ORGANIZATION: file dell'organization dell'utente O file ORGANIZATION condivisi con lui
         * Restituisce solo file la cui versione corrente è AVAILABLE.
         *
         * [ownership] partiziona l'insieme già visibile: OWNED tiene solo i file di cui
         * l'utente è owner, SHARED solo quelli con owner diverso, null nessun vincolo.
         *
         * [search] filtra (case-insensitive, substring) sul nome del file corrente
         * (lastFilename), così la ricerca lato server combacia col titolo mostrato nella UI.
         *
         * [sortField]/[sortDirection] ordinano il risultato dentro lo specification
         * (quando [sortField] non è null): "size" ordina per la dimensione della
         * versione corrente (file_versions.size, NON sul root File) usando il join
         * alla versione; "lastFilename" ordina case-insensitive (lower()) coerente
         * con la ricerca; ogni altro campo ordina sulla colonna root omonima. L'uuid
         * è chiave secondaria per una paginazione stabile a parità di valore.
         *
         * Nota: questa query NON usa `distinct`. Per evitare la moltiplicazione di righe
         * che imporrebbe il distinct (incompatibile con l'ORDER BY su una colonna non
         * selezionata in Postgres) la condizione "condiviso con me" di TUTTI gli scope
         * usa un subquery EXISTS invece di un LEFT JOIN su file_shares: senza il join un
         * file condiviso con più utenti non gonfia né le righe né la count.
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
            ownership: Ownership? = null,
            excludeContentTypes: Collection<String>? = null,
            search: String? = null,
            sortField: String? = null,
            sortDirection: Sort.Direction = Sort.Direction.DESC,
        ): Specification<File> {
            require(sharingScopes.isNotEmpty()) { "At least one sharing scope is required" }

            return Specification { root, query, cb ->
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
                // "Other" file-type bucket: keep files whose current version's contentType
                // doesn't match any of the known type tokens (AND of NOT LIKE), i.e. the
                // complement of the PDF/DOCX/TXT filters.
                if (!excludeContentTypes.isNullOrEmpty()) {
                    val contentTypeExpr = cb.lower(currentVersion.get<String>("contentType"))
                    excludeContentTypes.forEach { token ->
                        predicates.add(cb.notLike(contentTypeExpr, "%${token.lowercase()}%"))
                    }
                }
                if (dateFrom != null) {
                    val fromDate = Date.from(Instant.parse(dateFrom))
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate))
                }
                if (dateTo != null) {
                    val toDate = Date.from(Instant.parse(dateTo))
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate))
                }

                // Share esplicito sul file per l'utente corrente, espresso come EXISTS correlato
                // (non un LEFT JOIN su file_shares) così la query non moltiplica le righe quando
                // un file è condiviso con più utenti: niente inflazione della count e nessun
                // bisogno di `distinct` (incompatibile con l'ORDER BY su colonne non selezionate
                // in Postgres). Riusato da ogni scope: uno share dà visibilità sul file dentro il
                // suo scope, a prescindere dalla membership (es. un file DEPARTMENT condiviso con
                // chi non appartiene a quel department, idem ORGANIZATION).
                val sharedWithUser = query!!.subquery(Int::class.java).let { sub ->
                    val share = sub.from(FileShare::class.java)
                    sub.select(cb.literal(1)).where(
                        cb.equal(share.get<File>("file").get<UUID>("uuid"), root.get<UUID>("uuid")),
                        cb.equal(share.get<UUID>("userID"), userId),
                    )
                    cb.exists(sub)
                }

                // Un predicato per ogni scope richiesto, combinati in OR. In ciascuno la regola
                // di accesso "per ruolo/membership" è messa in OR con lo share esplicito.
                // Ricerca free-text sul nome del file corrente (lastFilename): match
                // case-insensitive di tipo "contains". Gli operatori LIKE (%, _, \) nel
                // termine vengono escapati così l'input utente non altera il pattern.
                if (!search.isNullOrBlank()) {
                    val escaped = search.trim()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_")
                    predicates.add(
                        cb.like(
                            cb.lower(root.get<String>("lastFilename")),
                            "%${escaped.lowercase()}%",
                            '\\',
                        ),
                    )
                }

                // Un predicato per ogni scope richiesto, combinati in OR
                val scopeField = root.get<SharingScope>("sharingScope")
                val scopePredicates = sharingScopes.map { scope ->
                    when (scope) {
                        SharingScope.PERSONAL -> cb.and(
                            cb.equal(scopeField, SharingScope.PERSONAL),
                            cb.or(
                                cb.equal(root.get<UUID>("ownerId"), userId),
                                sharedWithUser,
                            ),
                        )

                        SharingScope.DEPARTMENT -> {
                            // Membership: stessa organization e (admin: qualunque department;
                            // non-admin con department: solo il proprio; non-admin senza
                            // department: nessun file via membership).
                            val deptMembership = cb.and(
                                cb.equal(root.get<String>("organization"), organization),
                                when {
                                    isAdmin -> cb.conjunction()
                                    department != null -> cb.equal(root.get<String>("department"), department)
                                    else -> cb.disjunction()
                                }
                            )
                            cb.and(
                                cb.equal(scopeField, SharingScope.DEPARTMENT),
                                cb.or(deptMembership, sharedWithUser)
                            )
                        }

                        SharingScope.ORGANIZATION -> cb.and(
                            cb.equal(scopeField, SharingScope.ORGANIZATION),
                            cb.or(
                                cb.equal(root.get<String>("organization"), organization),
                                sharedWithUser
                            )
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

                // Ordinamento applicato qui (non via Pageable) per poter gestire i
                // campi che non sono colonne dirette del root File: "size" sta su
                // file_versions (join `currentVersion`), "lastFilename" va ordinato
                // case-insensitive. Saltato sulla count query (resultType Long), dove
                // l'ORDER BY non serve e non è ammesso. L'uuid è chiave secondaria per
                // rendere stabile la paginazione a parità di valore.
                if (sortField != null && query != null &&
                    query.resultType != Long::class.javaObjectType
                ) {
                    val sortExpr = when (sortField) {
                        "size" -> currentVersion.get<Long>("size")
                        "lastFilename" -> cb.lower(root.get<String>("lastFilename"))
                        else -> root.get<Any>(sortField)
                    }
                    val primary = if (sortDirection == Sort.Direction.ASC) cb.asc(sortExpr) else cb.desc(sortExpr)
                    query.orderBy(primary, cb.asc(root.get<UUID>("uuid")))
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