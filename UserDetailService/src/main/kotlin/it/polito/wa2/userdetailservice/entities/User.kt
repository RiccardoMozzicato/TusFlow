package it.polito.wa2.userdetailservice.entities

import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.enums.UserRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Temporal
import jakarta.persistence.TemporalType
import java.time.Instant
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

    @Column(updatable = true, nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    open var createdAt: Date? = null,

    @Column(updatable = true, nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    open var updatedAt: Date? = null,

    )

fun UserDTO.toEntity(): User {
    val date: Date = Date.from(Instant.now())
    return User(
        this.id,
        this.fullName,
        this.email,
        this.organization,
        this.role,
        this.department,
        this.storageQuota,
        date,
        date
    )
}