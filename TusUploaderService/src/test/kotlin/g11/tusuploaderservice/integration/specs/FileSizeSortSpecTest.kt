package g11.tusuploaderservice.integration.specs

import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileShare
import g11.tusuploaderservice.entities.FileStatus
import g11.tusuploaderservice.entities.FileVersion
import g11.tusuploaderservice.entities.PERMISSION_LEVEL
import g11.tusuploaderservice.integration.IntegrationTest
import g11.tusuploaderservice.repositories.FileRepository
import g11.tusuploaderservice.repositories.FileShareRepository
import g11.tusuploaderservice.specs.FileSpecifications
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.UUID

/**
 * Verifies size ordering in [FileSpecifications.buildFilesByScopes]. `size` lives on the current
 * file version (not the File root), so it is ordered via the version join with no `distinct`.
 * The key case is a PERSONAL file the caller owns that is shared with several other users: under
 * the old LEFT JOIN + distinct design that multiplied rows; the EXISTS rewrite must return it
 * exactly once and order the whole set by size with correct pagination. A fresh organization per
 * test isolates assertions from other seeded data.
 */
@Transactional
class FileSizeSortSpecTest @Autowired constructor(
    private val fileRepository: FileRepository,
    private val fileShareRepository: FileShareRepository,
) : IntegrationTest() {

    private val org = "org-${UUID.randomUUID()}"
    private val dept = "Sales"

    private val me = UUID.randomUUID()
    private val other1 = UUID.randomUUID()
    private val other2 = UUID.randomUUID()

    private fun persistFile(
        owner: UUID,
        scope: SharingScope,
        size: Long,
        sharedWith: List<UUID> = emptyList(),
        filename: String = "f.txt",
    ): UUID {
        val fileId = UUID.randomUUID()
        val now = Date()
        val file = File(
            uuid = fileId,
            ownerId = owner,
            createdAt = now,
            lastUpdateAt = now,
            lastFilename = filename,
            currentVersion = 1L,
            organization = org,
            department = null,
            sharingScope = scope,
        )
        file.addNewVersion(
            FileVersion(
                version = 1L,
                status = FileStatus.AVAILABLE,
                filename = filename,
                size = size,
                metadata = null,
                checksum = null,
                contentType = "text/plain",
                createdAt = now,
                lastUpdateAt = now,
                objectKey = "key/$fileId",
            ),
        )
        val saved = fileRepository.save(file)
        // File.shares has no cascade, so share rows are persisted explicitly.
        sharedWith.forEach { userId ->
            fileShareRepository.save(
                FileShare(userID = userId, permission = PERMISSION_LEVEL.READ_ONLY, timestamp = now, file = saved),
            )
        }
        return fileId
    }

    private fun sortedBySize(direction: Sort.Direction, pageSize: Int): List<UUID> {
        val spec = FileSpecifications.buildFilesByScopes(
            contentType = null,
            dateFrom = null,
            dateTo = null,
            sharingScopes = setOf(SharingScope.PERSONAL),
            userId = me,
            organization = org,
            department = dept,
            isAdmin = false,
            sortField = "size",
            sortDirection = direction,
        )
        return fileRepository.findAll(spec, PageRequest.of(0, pageSize)).content.map { it.uuid }
    }

    @Test
    fun `orders ascending by size without duplicating a multi-share owned file`() {
        // Owned by me and shared with two other users: previously multiplied to 2 rows.
        val big = persistFile(owner = me, scope = SharingScope.PERSONAL, size = 30L, sharedWith = listOf(other1, other2))
        val small = persistFile(owner = me, scope = SharingScope.PERSONAL, size = 10L)
        val mid = persistFile(owner = other1, scope = SharingScope.PERSONAL, size = 20L, sharedWith = listOf(me))

        assertEquals(listOf(small, mid, big), sortedBySize(Sort.Direction.ASC, pageSize = 10))
    }

    @Test
    fun `orders descending by size`() {
        val big = persistFile(owner = me, scope = SharingScope.PERSONAL, size = 30L, sharedWith = listOf(other1, other2))
        val small = persistFile(owner = me, scope = SharingScope.PERSONAL, size = 10L)
        val mid = persistFile(owner = other1, scope = SharingScope.PERSONAL, size = 20L, sharedWith = listOf(me))

        assertEquals(listOf(big, mid, small), sortedBySize(Sort.Direction.DESC, pageSize = 10))
    }

    @Test
    fun `paginates without inflating the count for a multi-share file`() {
        // Owned by me and shared with two other users: the case that previously inflated the count.
        persistFile(owner = me, scope = SharingScope.PERSONAL, size = 30L, sharedWith = listOf(other1, other2))
        val small = persistFile(owner = me, scope = SharingScope.PERSONAL, size = 10L)
        val mid = persistFile(owner = other1, scope = SharingScope.PERSONAL, size = 20L, sharedWith = listOf(me))

        val spec = FileSpecifications.buildFilesByScopes(
            contentType = null,
            dateFrom = null,
            dateTo = null,
            sharingScopes = setOf(SharingScope.PERSONAL),
            userId = me,
            organization = org,
            department = dept,
            isAdmin = false,
            sortField = "size",
            sortDirection = Sort.Direction.ASC,
        )
        val firstPage = fileRepository.findAll(spec, PageRequest.of(0, 2))

        // Three distinct files despite the two shares on `big`; first asc page is [small, mid].
        assertEquals(3, firstPage.totalElements)
        assertEquals(2, firstPage.totalPages)
        assertEquals(listOf(small, mid), firstPage.content.map { it.uuid })
    }

    @Test
    fun `orders ascending by name case-insensitively`() {
        // Mixed case must sort apple, Banana, cherry (not ASCII order B, a, c).
        val banana = persistFile(owner = me, scope = SharingScope.PERSONAL, size = 1L, filename = "Banana.txt")
        val apple = persistFile(owner = me, scope = SharingScope.PERSONAL, size = 1L, filename = "apple.txt")
        val cherry = persistFile(owner = me, scope = SharingScope.PERSONAL, size = 1L, filename = "cherry.txt")

        val spec = FileSpecifications.buildFilesByScopes(
            contentType = null,
            dateFrom = null,
            dateTo = null,
            sharingScopes = setOf(SharingScope.PERSONAL),
            userId = me,
            organization = org,
            department = dept,
            isAdmin = false,
            sortField = "lastFilename",
            sortDirection = Sort.Direction.ASC,
        )
        val ordered = fileRepository.findAll(spec, PageRequest.of(0, 10)).content.map { it.uuid }

        assertEquals(listOf(apple, banana, cherry), ordered)
    }
}
