package g11.tusuploaderservice.integration.specs

import g11.tusuploaderservice.dto.Ownership
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
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.UUID

/**
 * Verifies the `ownership` partition of [FileSpecifications.buildFilesByScopes]: among the files
 * already visible to the caller across all sharing scopes, OWNED keeps only the caller's own
 * files, SHARED keeps only those owned by someone else, and a null filter keeps the full visible
 * set. Each test uses a fresh organization so assertions are isolated from other seeded data.
 */
@Transactional
class FileScopesOwnershipSpecTest @Autowired constructor(
    private val fileRepository: FileRepository,
    private val fileShareRepository: FileShareRepository,
) : IntegrationTest() {

    private val org = "org-${UUID.randomUUID()}"
    private val dept = "Sales"

    private val me = UUID.randomUUID()
    private val other = UUID.randomUUID()

    private fun persistFile(
        owner: UUID,
        scope: SharingScope,
        organization: String = org,
        department: String? = null,
        share: Pair<UUID, PERMISSION_LEVEL>? = null,
    ): UUID {
        val fileId = UUID.randomUUID()
        val now = Date()
        val file = File(
            uuid = fileId,
            ownerId = owner,
            createdAt = now,
            lastUpdateAt = now,
            lastFilename = "f.txt",
            currentVersion = 1L,
            organization = organization,
            department = department,
            sharingScope = scope,
        )
        file.addNewVersion(
            FileVersion(
                version = 1L,
                status = FileStatus.AVAILABLE,
                filename = "f.txt",
                size = 10L,
                metadata = null,
                checksum = null,
                contentType = "text/plain",
                createdAt = now,
                lastUpdateAt = now,
                objectKey = "key/$fileId",
            ),
        )
        val saved = fileRepository.save(file)
        // File.shares has no cascade, so the share row is persisted explicitly.
        share?.let { (userId, perm) ->
            fileShareRepository.save(FileShare(userID = userId, permission = perm, timestamp = now, file = saved))
        }
        return fileId
    }

    private fun visibleFor(ownership: Ownership?): Set<UUID> {
        val spec = FileSpecifications.buildFilesByScopes(
            contentType = null,
            dateFrom = null,
            dateTo = null,
            sharingScopes = setOf(SharingScope.PERSONAL, SharingScope.DEPARTMENT, SharingScope.ORGANIZATION),
            userId = me,
            organization = org,
            department = dept,
            isAdmin = false,
            ownership = ownership,
        )
        return fileRepository.findAll(spec).map { it.uuid }.toSet()
    }

    @Test
    fun `OWNED keeps only the callers own visible files`() {
        val ownedPersonal = persistFile(owner = me, scope = SharingScope.PERSONAL)
        val ownedOrg = persistFile(owner = me, scope = SharingScope.ORGANIZATION)
        // Owned by someone else but visible: excluded by OWNED.
        persistFile(owner = other, scope = SharingScope.PERSONAL, share = me to PERMISSION_LEVEL.READ_ONLY)
        persistFile(owner = other, scope = SharingScope.ORGANIZATION)

        assertEquals(setOf(ownedPersonal, ownedOrg), visibleFor(Ownership.OWNED))
    }

    @Test
    fun `SHARED keeps only visible files owned by someone else`() {
        val sharedPersonal = persistFile(owner = other, scope = SharingScope.PERSONAL, share = me to PERMISSION_LEVEL.READ_ONLY)
        val otherOrg = persistFile(owner = other, scope = SharingScope.ORGANIZATION)
        // Owned by me: excluded by SHARED.
        persistFile(owner = me, scope = SharingScope.PERSONAL)
        // Owned by someone else and NOT visible (personal, not shared): never in the result.
        persistFile(owner = other, scope = SharingScope.PERSONAL)

        assertEquals(setOf(sharedPersonal, otherOrg), visibleFor(Ownership.SHARED))
    }

    @Test
    fun `null ownership keeps the full visible set`() {
        val ownedPersonal = persistFile(owner = me, scope = SharingScope.PERSONAL)
        val sharedPersonal = persistFile(owner = other, scope = SharingScope.PERSONAL, share = me to PERMISSION_LEVEL.READ_ONLY)
        val otherOrg = persistFile(owner = other, scope = SharingScope.ORGANIZATION)

        assertEquals(setOf(ownedPersonal, sharedPersonal, otherOrg), visibleFor(null))
    }
}
