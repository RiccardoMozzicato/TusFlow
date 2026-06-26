package it.polito.wa2.userdetailservice.specs

import it.polito.wa2.userdetailservice.entities.User
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification

object UserSpecifications {

    /**
     * Builds the user-listing specification.
     *
     * [organization] restricts the result to a single organization when non-null
     * (the org-scoped admin/manager/staff listing); null means no org restriction
     * (the service-account listing that sees every user).
     *
     * [search] filters (case-insensitive, substring) across the user's display
     * name and email in OR. The display name (`fullName`) is set from the username
     * at creation, so this single term covers name/surname AND username. Match is
     * a lowercased LIKE "%term%", mirroring the file-listing search style.
     */
    fun buildUsers(organization: String?, search: String?): Specification<User> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (organization != null) {
                predicates.add(cb.equal(root.get<String>("organization"), organization))
            }

            if (!search.isNullOrBlank()) {
                val term = "%${search.trim().lowercase()}%"
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get<String>("fullName")), term),
                        cb.like(cb.lower(root.get<String>("email")), term),
                    ),
                )
            }

            cb.and(*predicates.toTypedArray())
        }
}
