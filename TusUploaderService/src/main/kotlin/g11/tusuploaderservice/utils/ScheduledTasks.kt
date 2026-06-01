package g11.tusuploaderservice.utils
import g11.tusuploaderservice.entities.FilesOutboxEvent
import g11.tusuploaderservice.entities.MergeMode
import g11.tusuploaderservice.entities.Upload
import g11.tusuploaderservice.entities.UploadStatus
import g11.tusuploaderservice.repositories.FilesOutboxEventsRepository
import g11.tusuploaderservice.repositories.TusRepository
import g11.tusuploaderservice.services.FileService
import g11.tusuploaderservice.services.StorageService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Collections.emptyList
import java.util.Date
import java.util.UUID

@Component
class ScheduledTasks (
    private val tusRepository: TusRepository,
    private val storageService: StorageService,
    private val fileService: FileService,
    private val outboxRepository: FilesOutboxEventsRepository
    ) {


    private val log = LoggerFactory.getLogger(ScheduledTasks::class.java)


    @Scheduled(fixedRate = 3600000)
    @Transactional
    fun WALL_E() {
        log.info("Executing WALL-E task right now")
        var objects: List<UUID> = emptyList()

        log.debug("Searching for expired uploads... with {} and status {}", Date(), UploadStatus.MERGE_PENDING)
        val result=tusRepository.findAllByExpiresAtBeforeAndStatusNot(now = Date(), UploadStatus.MERGE_PENDING)

        result.forEach { it ->
            objects=objects.plus(it.id!!)
        }

        // Emit STORAGE_RELEASED for expired uploads that had a known size (STORAGE_RESERVED was sent for them)
        result.filter { it.size != null }.forEach { upload ->
            val ownerId = upload.ownerId
            val event = FilesOutboxEvent(
                eventType = "STORAGE_RELEASED",
                payload = mapOf(
                    "ownerUserId" to ownerId.toString(),
                    "fileId" to upload.fileId.toString(),
                    "uploadId" to upload.id.toString(),
                    "size" to upload.size
                ),
                createdAt = Date.from(Instant.now())
            )
            outboxRepository.save(event)
        }

        tusRepository.deleteAllByIdInBatch(objects.asIterable())
        storageService.deleteObjects("uploads",objects.map { it.toString() })

        objects.forEach { log.info("Objects were deleted: $it") }

        log.info("WALL-E task completed")
    }

    @Scheduled(fixedRate = 3000000)
    fun eve() {
        log.info("Executing EVE task")
        try{
            val toCompose = tusRepository.findByStatus( UploadStatus.MERGE_PENDING)
            log.info("Found {} uploads to compose", toCompose.size)

            // Separate uploads with chunkCount == 1 for renaming and moving to files bucket
            val (toRename, remainingToCompose) = toCompose.partition { it.chunkCount == 1 }

            toRename.forEach { upload ->
                try {
                    fileService.moveSingleChunkUpload(upload)
                    log.debug("File with UUID: {} renamed and moved successfully", upload.fileId)
                    tusRepository.delete(upload)
                } catch (e: Exception) {
                    log.error("Error renaming and moving single-chunk upload with UUID: {}", upload.fileId, e)
                }
            }

            val toComposeClientSide = remainingToCompose.filter {  it.mergeMode == MergeMode.CLIENT_SIDE }

            toComposeClientSide.forEach { upload ->
                try {
                    fileService.composeObjectClientSide(upload)
                    log.debug("File with UUID: {} composed client-side successfully", upload.fileId)
                } catch (e: Exception) {
                    log.error("Error composing client-side upload with UUID: {}", upload.fileId, e)
                }
            }

            //compose both server-side and "previously client-side" remaining uploads.
            remainingToCompose.forEach { upload ->
                try {
                    fileService.composeObject(upload)
                    log.debug("File with UUID: {} composed successfully", upload.fileId)
                    storageService.deletePartialObjects(upload.id.toString())
                    log.debug("Parts of the file with UUID: {} removed successfully", upload.fileId)
                    tusRepository.delete(upload)
                } catch (e: Exception) {
                    log.error("Error composing server-side upload with UUID: {}", upload.id, e)
                }
            }
        }
     catch (e: Exception) {
        log.error("Error executing EVE task", e)
    }
        log.info("EVE task executed")
    }

}



