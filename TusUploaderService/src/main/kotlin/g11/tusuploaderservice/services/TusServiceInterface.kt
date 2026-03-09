package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.UploadDTO
import jakarta.validation.Valid
import java.time.Instant

interface TusServiceInterface {
    fun createUpload(@Valid upload: UploadDTO): UploadDTO
    fun createUploadWithChunk(@Valid upload: UploadDTO, content: ByteArray): UploadDTO
    fun appendChunk(@Valid upload: UploadDTO, content: ByteArray): UploadDTO
    fun getSupportedHashAlgorithms(): List<String>
    fun executeChecksum(chunk: ByteArray, checksumHeader: String)
//    fun readFileDTO(id: String): UploadDTO
    fun getUploadInfo(id:String): UploadDTO
    fun terminateUpload(id:String)
}