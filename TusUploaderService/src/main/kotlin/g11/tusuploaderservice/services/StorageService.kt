package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.ChunkDTO
import g11.tusuploaderservice.entities.Upload
import g11.tusuploaderservice.exceptions.StorageException
import io.minio.*
import io.minio.messages.DeleteObject
import io.minio.messages.Item
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.text.get


@Service
class StorageService (
    private val minioClient: MinioClient,
    @Value("\${minio.uploads-bucket}") private val uploadsBucketName: String,
    @Value("\${minio.files-bucket}") private val filesBucketName: String
) : StorageServiceInterface
{
    private val log = LoggerFactory.getLogger(StorageService::class.java)

    init {
        log.info("Initializing StorageService with uploads bucket: {} and files bucket: {}", uploadsBucketName, filesBucketName)
        // Crea il bucket se non esiste
        var found = minioClient.bucketExists(
            BucketExistsArgs.builder().bucket(filesBucketName).build()
        )

        if (!found) {
            log.info("Files bucket {} does not exist, creating it", filesBucketName)
            minioClient.makeBucket(
                MakeBucketArgs.builder().bucket(filesBucketName).build()
            )
            log.info("Successfully created files bucket: {}", filesBucketName)
        } else {
            log.debug("Files bucket {} already exists", filesBucketName)
        }

        found = minioClient.bucketExists(
            BucketExistsArgs.builder().bucket(uploadsBucketName).build()
        )

        if (!found) {
            log.info("Uploads bucket {} does not exist, creating it", uploadsBucketName)
            minioClient.makeBucket(
                MakeBucketArgs.builder().bucket(uploadsBucketName).build()
            )
            log.info("Successfully created uploads bucket: {}", uploadsBucketName)
        } else {
            log.debug("Uploads bucket {} already exists", uploadsBucketName)
        }
    }

    override fun putObject(chunkDTO: ChunkDTO): String {
        // TusService MUST ensure that UUID and chunkNumber are not null
        log.debug("Uploading a new chunk for {}", chunkDTO.uuid)
        val objectName= chunkDTO.uuid+"/part-"+chunkDTO.number.toString()

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(chunkDTO.bucket)
                .`object`(objectName)
                .stream(chunkDTO.inputStream, chunkDTO.size, -1)
                .contentType(chunkDTO.contentType)
                .build()
        )
        return objectName
    }


    override fun composeObject(upload: Upload, targetObjectKey: String) {
        try {
            log.info("Composing objects for upload {} to target key: {}", upload.id, targetObjectKey)

            // Recupera tutti i chunk usando il prefix
            val results = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(uploadsBucketName)
                    .prefix("${upload.id}/part-")
                    .recursive(true)
                    .build()
            )

            // Crea la lista di ComposeSource ordinata per numero di parte
            val sourceObjectList = results
                .map { it.get() }
                .sortedBy { item ->
                    item.objectName()
                        .substringAfterLast("part-")
                        .toIntOrNull() ?: Int.MAX_VALUE
                }
                .map { item ->
                    ComposeSource.builder()
                        .bucket(uploadsBucketName)
                        .`object`(item.objectName())
                        .build()
                }
                .toList()

            if (sourceObjectList.isEmpty()) {
                log.error("No chunks found for upload {}", upload.id)
                throw RuntimeException("No chunks found for upload ${upload.id}")
            }

            log.debug("Found {} chunks for upload {}, composing...", sourceObjectList.size, upload.id)
            // Create my-bucketname/my-objectname by combining source object list.
            minioClient.composeObject(
                ComposeObjectArgs.builder()
                    .bucket(filesBucketName)
                    .`object`(targetObjectKey)
                    .sources(sourceObjectList)
                    .build()
            )
            log.info("Successfully composed {} chunks for upload {} into {}", sourceObjectList.size, upload.id, targetObjectKey)
        }
        catch (e: Exception) {
            log.error("Error composing object for upload {}: {}", upload.id, e.message, e)
            //we throw a StorageException to signal the failure to the caller for triggering rollback of transaction
            throw StorageException("Error composing object for upload ${upload.id}: ${e.message}", e)
        }

    }

    override fun getObject(bucket: String, objectName: String): ByteArray {
        log.debug("Retrieving object {} from bucket {}", objectName, bucket)

        val obj = minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(bucket)
                .`object`(objectName)
                .build()
        )

        return obj.readAllBytes()
    }

    override fun getObjectSize(bucket: String, objectName: String): Long {
        log.debug("Getting size of object {} from bucket {}", objectName, bucket)

        val stat = minioClient.statObject(
            StatObjectArgs.builder()
                .bucket(bucket)
                .`object`(objectName)
                .build()
        )

        return stat.size()
    }

    override fun deleteObject(bucket: String, objectName: String) {
        log.debug("Deleting object {} from bucket {}", objectName, bucket)

        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucket).`object`(objectName).build()
            )
            log.debug("Successfully deleted object {} from bucket {}", objectName, bucket)
        } catch (e: Exception) {
            log.error("Error deleting object {} from bucket {}: {}", objectName, bucket, e.message, e)
            throw e
        }
    }

    override fun deleteObjects(bucket: String, objectKey: List<String>) {
        log.debug("Deleting {} objects from bucket {}", objectKey.size, bucket)

        val toDelete= objectKey.map {
            DeleteObject(it)
        }

        val errors = minioClient.removeObjects(
            RemoveObjectsArgs.builder()
                .bucket(bucket)
                .objects(toDelete)
                .build()
        )

        if(errors.iterator().hasNext()){
            log.warn("Unable to delete because an error occurred")
            throw Exception("Error while deleting.")
        }
    }

    override fun deletePartialObjects(objectKey: String) {
        //objectKey rappresenta l'UUID della upload
        log.debug("Deleting partial objects with prefix {}/part- from bucket {}", objectKey, uploadsBucketName)

        val objects = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(uploadsBucketName)
                .prefix("$objectKey/part-")
                .recursive(true)
                .build()
        ).map {
            val item = it.get()
            DeleteObject(item.objectName())
        }

            log.debug("Found {} partial objects to delete for upload {}", objects.size, objectKey)

        val errors = minioClient.removeObjects(
            RemoveObjectsArgs.builder()
                .bucket(uploadsBucketName)
                .objects(objects)
                .build()
        )

        if(errors.iterator().hasNext()){
            log.warn("Unable to delete because an error occurred")
            throw Exception("Error while deleting.")
        }
    }

    override fun getMinimumChunksForSizeWithIndex(objectKey: String, partNumber: Int, targetSize: Long): List<ByteArray> {
        //default targetSize is 5MiB
        log.debug("Retrieving partial objects for {} starting from part {} with target size {} bytes", objectKey, partNumber, targetSize)

        val results: Iterable<Result<Item>> = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(uploadsBucketName)
                .prefix("$objectKey/part-")
                .recursive(true)
                .build()
        )

        val filteredResults = results
            .map { it.get() }
            .sortedBy { item ->
                item.objectName()
                    .substringAfterLast("part-")
                    .toIntOrNull() ?: Int.MAX_VALUE
            }
            .filter { item ->
                val partNum = item.objectName()
                    .substringAfterLast("part-")
                    .toIntOrNull() ?: 0
                partNum >= partNumber
            }

        val byteArrays = mutableListOf<ByteArray>()
        var totalSize = 0L
        for (item in filteredResults) {
            totalSize += item.size()
            val objStream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(uploadsBucketName)
                    .`object`(item.objectName())
                    .build()
            )

            objStream.use { stream ->
                byteArrays.add(stream.readAllBytes())
            }
            if (totalSize >= targetSize) {
                break
            }
        }

        return byteArrays
    }

    // Upload final object to files bucket
    override fun putFinalObject(chunkDTO: ChunkDTO): String {
        log.debug("Uploading final object for {}", chunkDTO.uuid)
        val objectName= chunkDTO.uuid+"/v"+chunkDTO.version+"/"+chunkDTO.filename

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(chunkDTO.bucket)
                .`object`(objectName)
                .stream(chunkDTO.inputStream, chunkDTO.size, -1)
                .contentType(chunkDTO.contentType)
                .build()
        )
        return objectName
    }

    override fun deleteAllVersions(objectKey: String) {
        val prefix = "$objectKey/"
        log.info("Deleting all versions with prefix {}", prefix)
        val objectsToDelete = mutableListOf<String>()

        // Lista tutti gli oggetti con il prefisso
        val results = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(filesBucketName)
                .prefix(prefix)
                .recursive(true)
                .build()
        )

        // Raccogli tutti i nomi degli oggetti
        for (result in results) {
            val item = result.get()
            objectsToDelete.add(item.objectName())
        }

            log.debug("Found {} versions to delete for key {}", objectsToDelete.size, objectKey)

        if (objectsToDelete.isNotEmpty()) {
            deleteObjects(filesBucketName, objectsToDelete)
        }
    }
}