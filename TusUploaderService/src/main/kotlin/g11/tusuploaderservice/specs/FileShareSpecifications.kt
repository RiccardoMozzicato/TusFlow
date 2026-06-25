package g11.tusuploaderservice.specs

import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileShare
import g11.tusuploaderservice.entities.FileVersion
import g11.tusuploaderservice.entities.PERMISSION_LEVEL
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Specifications for the "Shared with me" listing: rows of [FileShare] for the
 * current recipient, optionally narrowed by the shared file's owner, the granted
 * permission level, the file's content type, and its creation date range.
 *
 * Mirrors the filtering style of [FileSpecifications.buildFilesByScopes] (e.g. the
 * case-insensitive "contains" match on the current version's contentType), but the
 * query root is [FileShare] so the recipient/permission live on the root and the
 * file-side filters reach across the `file` join.
 */
object FileShareSpecifications {

    /**
     * @param recipientId the current user the files were shared with (mandatory).
     * @param ownerId     keep only files owned by this user; null means any owner.
     * @param permission  keep only shares at this level (READ_ONLY/READ_WRITE); null means any.
     * @param contentType case-insensitive substring match on the current version's contentType.
     * @param dateFrom    ISO-8601 lower bound (inclusive) on the file's createdAt.
     * @param dateTo      ISO-8601 upper bound (inclusive) on the file's createdAt.
     * @param excludeContentTypes keep only files whose current version's contentType matches
     *        none of these tokens (AND of NOT LIKE); used for the "Other" file-type bucket.
     * @param search case-insensitive "contains" match on the shared file's current name
     *        (lastFilename); LIKE operators in the term are escaped. Null/blank means no filter.
     */
    fun build(
        recipientId: UUID,
        ownerId: UUID? = null,
        permission: PERMISSION_LEVEL? = null,
        contentType: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        excludeContentTypes: Collection<String>? = null,
        search: String? = null,
    ): Specification<FileShare> =
        Specification { root, query, cb ->
            val predicates = mutableListOf<Predicate>()
            val fileJoin = root.join<FileShare, File>("file")

            // Recipient is always the current user.
            predicates.add(cb.equal(root.get<UUID>("userID"), recipientId))

            permission?.let {
                predicates.add(cb.equal(root.get<PERMISSION_LEVEL>("permission"), it))
            }

            ownerId?.let {
                predicates.add(cb.equal(fileJoin.get<UUID>("ownerId"), it))
            }

            if (contentType != null || !excludeContentTypes.isNullOrEmpty()) {
                // Join the file's current version and match its contentType "contains",
                // matching the Document Library's behaviour (e.g. "word" covers .doc/.docx).
                // No DISTINCT needed: `file` is @ManyToOne (one row per share) and the
                // version join is pinned to the single current version, so no row fan-out.
                // (DISTINCT here would also break Postgres, which forbids SELECT DISTINCT
                // when ORDER BY references the joined file.createdAt column.)
                val versions = fileJoin.join<File, FileVersion>("versions")
                predicates.add(
                    cb.equal(versions.get<Long>("version"), fileJoin.get<Long>("currentVersion")),
                )
                val contentTypeExpr = cb.lower(versions.get<String>("contentType"))
                if (contentType != null) {
                    predicates.add(cb.like(contentTypeExpr, "%${contentType.lowercase()}%"))
                }
                // "Other" file-type bucket: exclude every known type token (AND of NOT LIKE).
                excludeContentTypes?.forEach { token ->
                    predicates.add(cb.notLike(contentTypeExpr, "%${token.lowercase()}%"))
                }
            }

            if (dateFrom != null) {
                val fromDate = Date.from(Instant.parse(dateFrom))
                predicates.add(cb.greaterThanOrEqualTo(fileJoin.get<Date>("createdAt"), fromDate))
            }
            if (dateTo != null) {
                val toDate = Date.from(Instant.parse(dateTo))
                predicates.add(cb.lessThanOrEqualTo(fileJoin.get<Date>("createdAt"), toDate))
            }

            // Free-text search on the shared file's current name (lastFilename):
            // case-insensitive "contains", matching the Document Library. LIKE
            // operators (%, _, \) in the term are escaped so user input can't
            // alter the pattern.
            if (!search.isNullOrBlank()) {
                val escaped = search.trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_")
                predicates.add(
                    cb.like(
                        cb.lower(fileJoin.get<String>("lastFilename")),
                        "%${escaped.lowercase()}%",
                        '\\',
                    ),
                )
            }

            cb.and(*predicates.toTypedArray())
        }
}
