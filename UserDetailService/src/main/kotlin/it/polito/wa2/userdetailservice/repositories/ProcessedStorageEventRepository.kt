package it.polito.wa2.userdetailservice.repositories

import it.polito.wa2.userdetailservice.entities.ProcessedStorageEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProcessedStorageEventRepository : JpaRepository<ProcessedStorageEvent, UUID>
