package g11.tusuploaderservice.unit.services

import g11.tusuploaderservice.dto.ChunkDTO
import g11.tusuploaderservice.entities.MergeMode
import g11.tusuploaderservice.entities.Upload
import g11.tusuploaderservice.entities.UploadStatus
import g11.tusuploaderservice.services.StorageService
import io.minio.ComposeObjectArgs
import io.minio.GetObjectArgs
import io.minio.GetObjectResponse
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.ObjectWriteResponse
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.Result
import io.minio.messages.DeleteError
import io.minio.messages.Item
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Date
import java.util.UUID
import kotlin.test.assertContentEquals

class StorageServiceTest {
    @MockK(relaxUnitFun = true)
    lateinit var minioClient: MinioClient

    private lateinit var storageService: StorageService
    private val uploadsBucket = "uploads"
    private val filesBucket = "files"

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        every { minioClient.bucketExists(any()) } returns true
        storageService = StorageService(minioClient, uploadsBucket, filesBucket)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `putObject uploads a chunk to the requested bucket`() {
        val dto = chunkDTO()
        every { minioClient.putObject(any()) } returns mockk<ObjectWriteResponse>()

        val objectName = storageService.putObject(dto)

        Assertions.assertEquals("${dto.uuid}/part-${dto.number}", objectName)
        verify {
            minioClient.putObject(withArg<PutObjectArgs> { args ->
                Assertions.assertEquals(dto.bucket, args.bucket())
                Assertions.assertEquals(objectName, args.`object`())
            })
        }
    }

    @Test
    fun `putObject handles empty input stream`() {
        val dto = chunkDTO(data = ByteArray(0))
        every { minioClient.putObject(any()) } returns mockk<ObjectWriteResponse>()

        val objectName = storageService.putObject(dto)

        Assertions.assertEquals("${dto.uuid}/part-${dto.number}", objectName)
        verify {
            minioClient.putObject(withArg<PutObjectArgs> { args ->
                Assertions.assertEquals(dto.bucket, args.bucket())
                Assertions.assertEquals(objectName, args.`object`())
            })
        }
    }

    @Test
    fun `putObject throws StorageException when MinIO fails`() {
        val dto = chunkDTO()
        every { minioClient.putObject(any()) } throws Exception("MinIO error")

        try {
            storageService.putObject(dto)
            assert(false) { "Expected StorageException to be thrown" }
        } catch (e: Exception) {
            // Expected - putObject doesn't wrap exceptions in StorageException
            Assertions.assertEquals("MinIO error", e.message)
        }
    }

    @Test
    fun `putFinalObject uploads the composed file`() {
        val dto = chunkDTO(version = 2L, filename = "final.bin", bucket = filesBucket)
        every { minioClient.putObject(any()) } returns mockk<ObjectWriteResponse>()

        val objectName = storageService.putFinalObject(dto)

        Assertions.assertEquals("${dto.uuid}/v${dto.version}/${dto.filename}", objectName)
        verify {
            minioClient.putObject(withArg<PutObjectArgs> { args ->
                Assertions.assertEquals(filesBucket, args.bucket())
                Assertions.assertEquals(objectName, args.`object`())
            })
        }
    }

    @Test
    fun `putFinalObject handles empty input stream`() {
        val dto = chunkDTO(version = 2L, filename = "empty.bin", bucket = filesBucket, data = ByteArray(0))
        every { minioClient.putObject(any()) } returns mockk<ObjectWriteResponse>()

        val objectName = storageService.putFinalObject(dto)

        Assertions.assertEquals("${dto.uuid}/v${dto.version}/${dto.filename}", objectName)
        verify {
            minioClient.putObject(withArg<PutObjectArgs> { args ->
                Assertions.assertEquals(filesBucket, args.bucket())
                Assertions.assertEquals(objectName, args.`object`())
            })
        }
    }

    @Test
    fun `putFinalObject throws exception when MinIO fails`() {
        val dto = chunkDTO(version = 2L, filename = "fail.bin", bucket = filesBucket)
        every { minioClient.putObject(any()) } throws Exception("Storage failure")

        try {
            storageService.putFinalObject(dto)
            assert(false) { "Expected exception to be thrown" }
        } catch (e: Exception) {
            Assertions.assertEquals("Storage failure", e.message)
        }
    }

    @Test
    fun `composeObject sorts chunk parts before composing`() {
        val uploadId = UUID.randomUUID()
        val upload = sampleUpload(uploadId)
        val targetKey = "files/$uploadId"
        every { minioClient.listObjects(any()) } returns listOf(
            itemResult("$uploadId/part-2"),
            itemResult("$uploadId/part-1")
        )
        every { minioClient.composeObject(any()) } returns mockk<ObjectWriteResponse>()

        storageService.composeObject(upload, targetKey)

        verify {
            minioClient.listObjects(withArg<ListObjectsArgs> { args ->
                Assertions.assertEquals(uploadsBucket, args.bucket())
                Assertions.assertEquals("${upload.id}/part-", args.prefix())
            })
        }
        verify {
            minioClient.composeObject(withArg<ComposeObjectArgs> { args ->
                Assertions.assertEquals(filesBucket, args.bucket())
                Assertions.assertEquals(targetKey, args.`object`())
                val composedOrder = args.sources().map { it.`object`() }
                Assertions.assertEquals(listOf("$uploadId/part-1", "$uploadId/part-2"), composedOrder)
            })
        }
    }

    @Test
    fun `getObject returns the raw bytes from MinIO`() {
        val bytes = "payload".toByteArray()
        val response = mockk<GetObjectResponse> {
            every { readAllBytes() } returns bytes
            every { close() } just runs
        }
        every { minioClient.getObject(any<GetObjectArgs>()) } returns response

        val result = storageService.getObject(filesBucket, "object-key")

        assertContentEquals(bytes, result)
        verify {
            minioClient.getObject(withArg<GetObjectArgs> { args ->
                Assertions.assertEquals(filesBucket, args.bucket())
                Assertions.assertEquals("object-key", args.`object`())
            })
        }
    }

    @Test
    fun `getMinimumChunksForSizeWithIndex gathers chunks until target size`() {
        val objectKey = "upload-123"
        every { minioClient.listObjects(any()) } returns listOf(
            itemResult("$objectKey/part-1", size = 3),
            itemResult("$objectKey/part-2", size = 4),
            itemResult("$objectKey/part-3", size = 5)
        )
        val stream2 = mockStream("part2".toByteArray())
        val stream3 = mockStream("part3".toByteArray())
        every { minioClient.getObject(any<GetObjectArgs>()) } returnsMany listOf(stream2, stream3)

        val chunks = storageService.getMinimumChunksForSizeWithIndex(objectKey, partNumber = 2, targetSize = 7)

        Assertions.assertEquals(2, chunks.size)
        assertContentEquals("part2".toByteArray(), chunks[0])
        assertContentEquals("part3".toByteArray(), chunks[1])
        verify(exactly = 2) {
            minioClient.getObject(match<GetObjectArgs> {
                it.bucket() == uploadsBucket && it.`object`().startsWith("$objectKey/part-")
            })
        }
    }

    @Test
    fun `deleteObject delegates to MinIO`() {
        storageService.deleteObject(filesBucket, "object-to-delete")

        verify {
            minioClient.removeObject(withArg<RemoveObjectArgs> { args ->
                Assertions.assertEquals(filesBucket, args.bucket())
                Assertions.assertEquals("object-to-delete", args.`object`())
            })
        }
    }

    @Test
    fun `deleteObjects removes every requested key`() {
        every { minioClient.removeObjects(any()) } answers { emptyDeleteErrors() }
        val keys = listOf("first", "second")

        storageService.deleteObjects(uploadsBucket, keys)

        verify(exactly = 1) {
            minioClient.removeObjects(any())
        }
    }

    @Test
    fun `deletePartialObjects removes every chunk part`() {
        val uploadId = "uuid-1"
        every { minioClient.listObjects(any()) } returns listOf(
            itemResult("$uploadId/part-1"),
            itemResult("$uploadId/part-2")
        )
        every { minioClient.removeObjects(any()) } answers { emptyDeleteErrors() }

        storageService.deletePartialObjects(uploadId)

        verify(exactly = 1) {
            minioClient.listObjects(any())
        }
        verify(exactly = 1) {
            minioClient.removeObjects(any())
        }
    }

    @Test
    fun `deleteAllVersions removes every stored file version`() {
        val objectKey = "file-uuid"
        every { minioClient.listObjects(any()) } returns listOf(
            itemResult("$objectKey/v1/file.bin"),
            itemResult("$objectKey/v2/file.bin")
        )
        every { minioClient.removeObjects(any()) } answers { emptyDeleteErrors() }

        storageService.deleteAllVersions(objectKey)

        verify(exactly = 1) {
            minioClient.listObjects(any())
        }
        verify(exactly = 1) {
            minioClient.removeObjects(any())
        }
    }

    private fun chunkDTO(
        uuid: String = UUID.randomUUID().toString(),
        number: Int = 1,
        bucket: String = uploadsBucket,
        filename: String? = "chunk.bin",
        version: Long? = 1L,
        data: ByteArray = "chunk".toByteArray(),
        contentType: String? = "application/octet-stream"
    ): ChunkDTO = ChunkDTO(
        filename = filename,
        uuid = uuid,
        number = number,
        inputStream = ByteArrayInputStream(data),
        size = data.size.toLong(),
        contentType = contentType,
        bucket = bucket,
        version = version
    )

    private fun sampleUpload(id: UUID = UUID.randomUUID()): Upload {
        val now = Date()
        return Upload(
            id = id,
            fileId = UUID.randomUUID(),
            filename = "file.bin",
            contentType = "application/octet-stream",
            size = 10L,
            totalOffset = 10L,
            metadata = mapOf("k" to "v"),
            checksum = "checksum",
            status = UploadStatus.UPLOADING,
            createdAt = now,
            expiresAt = Date(now.time + 86_400_000L),
            objectKey = null,
            mergeMode = MergeMode.SERVER_SIDE,
            chunkCount = 2,
            fileVersionId = null,
            ownerId = UUID.randomUUID(),
            organization = "polito",
            department = "IT"
        )
    }

    private fun itemResult(objectName: String, size: Long = 1L): Result<Item> {
        val item = mockk<Item>()
        every { item.objectName() } returns objectName
        every { item.size() } returns size
        val result = mockk<Result<Item>>()
        every { result.get() } returns item
        return result
    }

    private fun mockStream(bytes: ByteArray): GetObjectResponse = mockk {
        every { readAllBytes() } returns bytes
        every { close() } just runs
    }

    private fun emptyDeleteErrors(): Iterable<Result<DeleteError>> {
        val iterator = mockk<Iterator<Result<DeleteError>>>()
        every { iterator.hasNext() } returns false

        val iterable = mockk<Iterable<Result<DeleteError>>>()
        every { iterable.iterator() } returns iterator

        return iterable
    }
}