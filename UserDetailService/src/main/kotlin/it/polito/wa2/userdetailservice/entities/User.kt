package it.polito.wa2.userdetailservice.entities

import it.polito.wa2.userdetailservice.enums.UserRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Temporal
import jakarta.persistence.TemporalType
import jakarta.persistence.Version
import java.util.Date
import java.util.UUID


@Entity
@Table(name = "users")
class User(

    @Id
    @Column(updatable = false, nullable = false)
    open var id: UUID,

    @Column(updatable = true, nullable = false)
    open var fullName: String,

    @Column(updatable = true, nullable = false, unique = true)
    open var email: String,

    @Column(updatable = true, nullable = false)
    open var organization: String,

    @Column(updatable = true, nullable = false)
    open var role: UserRole,

    @Column(updatable = true, nullable = true)
    open var department: String? = null,

    @Column(updatable = true, nullable = true)
    open var storageQuota: Long? = null,

    @Column(updatable = true, nullable = false)
    open var storageUsed: Long = 0,

    @Column(updatable = true, nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    open var createdAt: Date? = null,

    @Column(updatable = true, nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    open var updatedAt: Date? = null,

    // Optimistic-lock version: guards concurrent updates to the same user row (e.g. interleaved
    // storage events) by failing the stale write instead of silently clobbering it. Managed by
    // Hibernate; never set it manually.
    @Version
    @Column(nullable = false)
    open var version: Long = 0,

    )

