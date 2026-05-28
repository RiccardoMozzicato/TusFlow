package it.polito.wa2.userdetailservice.repositories

import it.polito.wa2.userdetailservice.entities.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserRepository: JpaRepository<User, UUID> {
    fun getUserByEmail(email: String): Optional<User>
    fun findByOrganization(organization: String, pageable: Pageable): Page<User>
}
