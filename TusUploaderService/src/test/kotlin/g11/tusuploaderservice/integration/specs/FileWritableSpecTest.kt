package g11.tusuploaderservice.integration.specs

import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.dto.UserRole
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
 * Verifies that [FileSpecifications.buildWritableFiles] returns exactly the files a user may
 * modify, mirroring the role/scope rules in `FileService.getCurrentUserPermission`. Each test
 * uses a fresh organization so the assertions are isolated from any other seeded data.
 */
@Transactional
class FileWritableSpecTest @Autowired constructor(
    private val fileRepository: FileRepository,
    private val fileShareRepository: FileShareRepository,
) : IntegrationTest() {

    private val org = "org-${UUID.randomUUID()}"
    private val sales = "Sales"
    private val eng = "Eng"

    private val staff = UUID.randomUUID()
    private val manager = UUID.randomUUID()
    private val admin = UUID.randomUUID()
    private val otherOwner = UUID.randomUUID()

    private fun persistFile(
        owner: UUID,
        scope: SharingScope,
        organization: String = org,
        department: String? = null,
        status: FileStatus = FileStatus.AVAILABLE,
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
                status = status,
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

    private fun writableFor(
        userId: UUID,
        role: UserRole,
        department: String?,
        candidateIds: Collection<UUID> = emptyList(),
    ): Set<UUID> =
        fileRepository
            .findAll(FileSpecifications.buildWritableFiles(userId, org, department, role, candidateIds))
            .map { it.uuid }
            .toSet()

    @Test
    fun `staff can write only owned and READ_WRITE-shared files`() {
        val owned = persistFile(owner = staff, scope = SharingScope.PERSONAL)
        val rwShared = persistFile(owner = otherOwner, scope = SharingScope.PERSONAL, share = staff to PERMISSION_LEVEL.READ_WRITE)
        // Excluded: read-only share, department file (staff is read-only there), org file.
        persistFile(owner = otherOwner, scope = SharingScope.PERSONAL, share = staff to PERMISSION_LEVEL.READ_ONLY)
        persistFile(owner = otherOwner, scope = SharingScope.DEPARTMENT, department = sales)
        persistFile(owner = otherOwner, scope = SharingScope.ORGANIZATION)

        assertEquals(setOf(owned, rwShared), writableFor(staff, UserRole.ROLE_staff, sales))
    }

    @Test
    fun `manager can write their department files plus owned and RW-shared`() {
        val owned = persistFile(owner = manager, scope = SharingScope.PERSONAL)
        val deptSales = persistFile(owner = otherOwner, scope = SharingScope.DEPARTMENT, department = sales)
        // Excluded: other department, organization scope (manager is read-only there).
        persistFile(owner = otherOwner, scope = SharingScope.DEPARTMENT, department = eng)
        persistFile(owner = otherOwner, scope = SharingScope.ORGANIZATION)

        assertEquals(setOf(owned, deptSales), writableFor(manager, UserRole.ROLE_manager, sales))
    }

    @Test
    fun `admin can write any department and organization file in their org`() {
        val deptSales = persistFile(owner = otherOwner, scope = SharingScope.DEPARTMENT, department = sales)
        val deptEng = persistFile(owner = otherOwner, scope = SharingScope.DEPARTMENT, department = eng)
        val orgFile = persistFile(owner = otherOwner, scope = SharingScope.ORGANIZATION)
        // Excluded: file in a different organization.
        persistFile(owner = otherOwner, scope = SharingScope.DEPARTMENT, organization = "other-org", department = sales)

        assertEquals(setOf(deptSales, deptEng, orgFile), writableFor(admin, UserRole.ROLE_admin, sales))
    }

    @Test
    fun `a deleted current version is never writable even for the owner`() {
        persistFile(owner = staff, scope = SharingScope.PERSONAL, status = FileStatus.DELETED)

        assertEquals(emptySet<UUID>(), writableFor(staff, UserRole.ROLE_staff, sales))
    }

    @Test
    fun `candidate ids restrict the result to the intersection`() {
        val ownedA = persistFile(owner = staff, scope = SharingScope.PERSONAL)
        val ownedB = persistFile(owner = staff, scope = SharingScope.PERSONAL)

        // Only ownedA is a candidate, so ownedB (writable but not asked for) is excluded.
        assertEquals(
            setOf(ownedA),
            writableFor(staff, UserRole.ROLE_staff, sales, candidateIds = setOf(ownedA)),
        )
    }
}
