package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.ChunkDTO
import g11.tusuploaderservice.entities.Upload
import java.util.UUID

interface StorageServiceInterface {
    fun putObject(chunkDTO: ChunkDTO): String
    fun getObject(bucket:String = "uploads", objectName: String): ByteArray
    fun getObjectSize(bucket: String, objectName: String): Long
    fun deleteObject(bucket: String, objectName: String)
    fun deleteObjects(bucket: String, objectKey: List<String>)
    fun deletePartialObjects(objectKey: String)
    fun composeObject(upload: Upload, targetObjectKey: String)
    fun getMinimumChunksForSizeWithIndex(objectKey: String, partNumber: Int, targetSize: Long = 5242880L): List<ByteArray>
    fun putFinalObject(chunkDTO: ChunkDTO): String
    fun deleteAllVersions(objectKey: String)
}