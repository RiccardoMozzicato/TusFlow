package it.polito.wa2.userdetailservice.services

import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.dto.FiltersDTO
import it.polito.wa2.userdetailservice.dto.UserCreationDTO
import java.util.UUID
import org.springframework.data.domain.Page

interface UserServiceInterface {

    fun getUsers(filtersDTO: FiltersDTO): Page<UserDTO>
    fun getUser(id: UUID): UserDTO
    fun getUsersBatch(ids: List<UUID>): Map<UUID, UserDTO>
    fun createUser(user: UserCreationDTO)
    fun updateUser(user: UserDTO)
    fun deleteUser(id: UUID)

}