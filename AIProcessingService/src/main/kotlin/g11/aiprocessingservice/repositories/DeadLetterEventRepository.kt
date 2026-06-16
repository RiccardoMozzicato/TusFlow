package g11.aiprocessingservice.repositories

import g11.aiprocessingservice.entities.DeadLetterEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeadLetterEventRepository : JpaRepository<DeadLetterEvent, UUID>
