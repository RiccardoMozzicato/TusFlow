package g11.tusuploaderservice.repositories

import g11.tusuploaderservice.entities.FilesOutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FilesOutboxEventRepository : JpaRepository<FilesOutboxEvent, UUID>

